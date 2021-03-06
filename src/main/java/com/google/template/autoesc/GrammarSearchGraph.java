package com.google.template.autoesc;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.template.autoesc.ParseWatcher.Branch;
import com.google.template.autoesc.combimpl.LookaheadCombinator;
import com.google.template.autoesc.combimpl.LoopCombinator;
import com.google.template.autoesc.combimpl.OrCombinator;
import com.google.template.autoesc.combimpl.ReferenceCombinator;
import com.google.template.autoesc.combimpl.SeqCombinator;
import com.google.template.autoesc.combimpl.SingletonCombinator;
import com.google.template.autoesc.out.BranchMarker;
import com.google.template.autoesc.out.EphemeralOutput;
import com.google.template.autoesc.out.LookaheadMarker;
import com.google.template.autoesc.out.LoopMarker;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.out.PartialOutput;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.out.UnaryOutput;
import com.google.template.autoesc.var.VariableOutput;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextTables;
import com.google.template.autoesc.viz.VizOutput;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static com.google.template.autoesc.viz.TextTables.column;

/**
 * Allows searching from parse states through transitions that don't consume
 * input to find a common parse state for the start of the next branch.
 *
 * <h3>Searching for Plans</h3>
 * We can construct a graph where each node (<a href="#inlining">caveat</a>)
 * corresponds to a way of entering/exiting a grammar element.
 * <p>
 * We can then build edges based on {@link Combinator#allEpsilonTransitions}
 * epsilon transitions -- transitions that do not consume inputs.
 * <p>
 * We can then walk from each parse state until we find a state that all
 * start states reach via &epsilon; transitions.
 * <p>
 * Backtracking lets us then compute a series of enter/exit operations for
 * each start state that gets us to the common end-state.
 *
 * <h3 id="inlining">Inlining non-terminals</h3>
 * If non-terminal <i>Foo</i> can enter non-terminal <i>Bar</i> and vice-versa
 * then to reconcile the stacks
 * <ol>
 *   <li>[Foo, Bar]</pre>
 *   <li>[Foo]</pre>
 *   <li>[Foo, Bar, Foo]</pre>
 * </ol>
 * we would not benefit from having the second exit <i>Foo</i>, or the third
 * enter <i>Bar</i> again.
 * <p>
 * Correctly analyzing flow through a function calls relating returns with
 * call-sites.
 * Similarly, we need to correctly link exits from a non-terminal's body with
 * exits from the top-most reference on the stack.
 * <p>
 * We don't keep one node for every transition in the grammar.
 * Instead, we look at each stack for the {@link ReferenceCombinator}s on it.
 * This gives us the reference stack.
 * <p>
 * For every prefix of the reference stack, we <i>inline</i> a sub-graph
 * consisting of every combinator reachable (via &epsilon; transitions) from the
 * referent combinator without going through a reference.
 * <p>
 * We do this lazily we maintain a map from
 * (reference name stack, combinator, transition type) tuples &rarr; nodes.
 * <p>
 * Each node then represents a transition to a unique parse state stack.
 * <p>
 * We don't inline reference chains that are not on any start branch stack.
 * Finding a path to a common end-point might require entering references
 * and thus extending the reference stack beyond any prefix, but we know that
 * our goal will never be inside such a reference, so we can <i>gloss over</i>
 * such references.
 *
 * <h3>Identifying success</h3>
 * With each node, we associate a set of the branches that have reached it.
 * <p>
 * The search succeeds when one node has a set of reaching branches that
 * includes all branches.
 *
 * <h3>Building a plan</h3>
 * We only inline a body when it is on a start stack.
 * This allows us to <i>gloss over</i> the details of references that we need to
 * descend into but which cannot contain the search goal.
 * So a goal might look like
 * <blockquote>
 *  [Enter node a, Enter node b, Pass-through non-terminal C, Exit d pass, ...]
 * </blockquote>
 * Once we've identified a goal, we then flesh-out Pass-through and Fail-out-of
 * steps by finding a plan from the start of that referent to the end.
 * <p>
 * We avoid endlessly fleshing out indirectly LR or RR non-terminals.
 *
 * <h3>Optimistic Assumption</h3>
 * <h4>Opaque predicates</h4>
 * It is possible that some transitions are really unavailable to us.
 * For example, we could construct a grammar that uses variable
 * {@linkPlain Grammar#set sets} and {@linkPlain Grammar#in tests} so that
 * an early transition precludes a later transition.
 * <p>
 * We assume this does not happen.

 * <h4>Reference Pass/Failure</h4>
 * We don't inline references not on any branch's starting stack.
 * Instead, we assume that a references body can pass via epsilon transitions
 * when its referent's {@link Combinator#consumesInput consumesInput} is not
 * {@link Frequency#ALWAYS} and can fail with no input when its referent's
 * {@link Combinator#consumesInput consumesInput} is not
 * {@link Frequency#NEVER}.
 * <p>
 * Either of these assumptions might be false.
 *
 */
final class GrammarSearchGraph {

  static final boolean DEBUG = false;
  static final boolean DEBUG_BEST_JOIN = DEBUG || false;

  private static FList<ProdName> prodNameChainOf(FList<Combinator> stack) {
    return stack.mapFiltered(
        new Function<Combinator, Optional<ProdName>>() {
          @Override
          public Optional<ProdName> apply(Combinator c) {
            if (c instanceof ReferenceCombinator) {
              return Optional.of(((ReferenceCombinator) c).name);
            }
            return Optional.absent();
          }
        });
  }


  private static final class NodeKey {
    final FList<ProdName> chain;
    final Combinator c;
    final TransitionType t;

    NodeKey(FList<ProdName> chain, Combinator c, TransitionType t) {
      Preconditions.checkArgument(
          c instanceof SingletonCombinator
          || c.getMetadata().nodeIndex > 0,
          "Nodes not assigned unique indices");

      this.chain = chain;
      this.c = c;
      this.t = t;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof NodeKey)) { return false; }
      NodeKey that = (NodeKey) o;
      return c.getMetadata().nodeIndex == that.c.getMetadata().nodeIndex
          && t == that.t && chain.equals(that.chain);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(chain, c.getMetadata().nodeIndex, t);
    }

    @Override
    public String toString() {
      return "{" + chain + ", " + t + ", " + c + "}";
    }
  }


  /**
   * A delta between two parse states.
   */
  private static final class Node {
    private final List<Optional<SearchStep>> reaching = new ArrayList<>();
    /** The number of present elements of reaching. */
    private int nReaching;
    /** The max of the nRollbacks of present elements of reaching. */
    private int maxNRollbacks;

    Node() {
    }

    boolean reachesViaBetterPath(int branchIndex, SearchStep step) {
      if (branchIndex >= reaching.size()) {
        return false;
      }
      Optional<SearchStep> alreadyReachingStepOpt = reaching.get(branchIndex);
      if (!alreadyReachingStepOpt.isPresent()) {
        return false;
      }
      SearchStep alreadyReachingStep = alreadyReachingStepOpt.get();
      return alreadyReachingStep.compareTo(step) < 0;
    }

    void setReaching(int branchIndex, SearchStep step) {
      while (reaching.size() <= branchIndex) {
        reaching.add(Optional.<SearchStep>absent());
      }
      Optional<SearchStep> oldStepOpt = reaching.get(branchIndex);
      int oldNRollbacks;
      if (oldStepOpt.isPresent()) {
        SearchStep oldStep = oldStepOpt.get();
        oldNRollbacks = oldStep.nRollbacks;
      } else {
        ++nReaching;
        oldNRollbacks = 0;
      }
      reaching.set(branchIndex, Optional.of(step));
      if (step.nRollbacks >= this.maxNRollbacks) {
        this.maxNRollbacks = step.nRollbacks;
      } else if (oldNRollbacks == this.maxNRollbacks) {
        recomputeMaxNRollbacks();
      }
    }

    private void recomputeMaxNRollbacks() {
      // Recompute max.
      int newMax = 0;
      for (Optional<SearchStep> stepOpt : reaching) {
        if (stepOpt.isPresent()) {
          newMax = Math.max(newMax, stepOpt.get().nRollbacks);
        }
      }
      this.maxNRollbacks = newMax;
    }

    ImmutableList<Optional<SearchStep>> getReaching() {
      return ImmutableList.copyOf(reaching);
    }

    int maxNRollbacks() {
      return maxNRollbacks;
    }

    int nReaching() {
      return nReaching;
    }
  }


  private static final class SearchStep implements Comparable<SearchStep> {
    /**
     * The index of the branch from which the path to this search step
     * originated.
     */
    final int branchIndex;
    /** Identifies the transition reached. */
    final NodeKey key;
    /**
     * The parse state at the beginning of the transition specified by
     * {@link #key}.
     */
    final Parse p;
    /**
     * The number of steps between this step and the end of the branch
     * identified by {@link #branchIndex}.
     */
    final int distanceFromBranchEnd;
    /**
     * The number of prior steps that involved rolling back an ephemeral marker
     * to, presumably, fail over to a later branch.
     */
    final int nRollbacks;
    /**
     * The search step that preceded this one.
     */
    final FList<SearchStep> prev;

    SearchStep(
        int branchIndex, NodeKey key, Parse p, int nRollbacks,
        SearchStep prev) {
      this(branchIndex, key, p, nRollbacks, FList.cons(prev, prev.prev));
    }

    SearchStep(
        int branchIndex, NodeKey key, Parse p, int nRollbacks,
        FList<SearchStep> prev) {
      this.branchIndex = branchIndex;
      this.key = key;
      this.p = p;
      this.prev = prev;
      this.distanceFromBranchEnd = this.prev.length();
      this.nRollbacks = nRollbacks;
    }

    @Override
    public String toString() {
      return "{Step " + key + ", " + p + "}";
    }

    /** {@code a < b} when a is a better early search step than b. */
    @Override
    public int compareTo(SearchStep step) {
      int delta;
      // Prefer fewer rollbacks.
      delta = Integer.compare(this.nRollbacks, step.nRollbacks);
      // Prefer shorter paths.
      if (delta == 0) {
        delta = Integer.compare(
            this.distanceFromBranchEnd, step.distanceFromBranchEnd);
      }
      // Prefer not failing.
      if (delta == 0) {
        TransitionType tt = key.t;
        delta = Integer.compare(
            (tt == TransitionType.EXIT_FAIL
            ? 1 : 0),
            (tt == TransitionType.EXIT_FAIL
            ? 1 : 0)
            );
      }
      return delta;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(nRollbacks, distanceFromBranchEnd, key.t);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SearchStep)) {
        return false;
      }
      SearchStep that = (SearchStep) o;
      return this.compareTo(that) == 0;
    }
  }

  /**
   * @param branchEndStates The state at the end of each branch.
   * @return
   */
  static JoinResult join(Iterable<? extends Joinable> joinables) {
    ImmutableList.Builder<Branch> branchNames = ImmutableList.builder();
    ImmutableList.Builder<Parse> branchEndStates = ImmutableList.builder();
    for (Joinable x : joinables) {
      branchNames.add(x.getBranch());
      branchEndStates.add(x.getParse());
    }
    return new GrammarSearchGraph(
            branchNames.build(), branchEndStates.build()
        )
        .join();
  }

  static final class JoinResult {
    final ImmutableList<Parse> branchEndStates;
    final ImmutableList<Parse> branchJoinStates;
    private final BitSet joined;

    JoinResult(
        ImmutableList<Parse> branchEndStates,
        ImmutableList<Parse> branchJoinStates,
        BitSet joined) {
      Preconditions.checkArgument(
          branchEndStates.size() == branchJoinStates.size());
      Preconditions.checkArgument(
          joined.nextSetBit(branchEndStates.size()) < 0);
      this.branchEndStates = branchEndStates;
      this.branchJoinStates = branchJoinStates;
      this.joined = new BitSet();
      this.joined.or(joined);
    }

    boolean joined(int branchIndex) {
      return joined.get(branchIndex);
    }

    int branchCount() {
      return branchEndStates.size();
    }

    int nJoined() {
      return joined.cardinality();
    }

    boolean allJoined() {
      return firstNotJoined() < 0;
    }

    int firstNotJoined() {
      int next = joined.nextClearBit(0);
      return next < branchCount() ? next : -1;
    }
  }

  private static final ImmutableList<TransitionType> TRANSITION_SEARCH_ORDER
    = ImmutableList.of(
        // By putting this first, we bias towards solutions that don't involve
        // stepping out of loops that might consume more input.
        TransitionType.ENTER,
        TransitionType.EXIT_PASS,
        TransitionType.EXIT_FAIL);
  static {
    assert TRANSITION_SEARCH_ORDER.containsAll(
        EnumSet.allOf(TransitionType.class));
  }


  private final ImmutableSet<FList<ProdName>> nonTerminalsToEnter;
  private final PriorityQueue<SearchStep> periphery = new PriorityQueue<>();
  private final Map<NodeKey, Node> nodes = new LinkedHashMap<>();
  private Node bestMatch = null;
  private NodeKey bestMatchKey = null;
  final ImmutableList<Branch> branchNames;
  final ImmutableList<Parse> branchEndStates;
  final Language lang;

  private GrammarSearchGraph(
      ImmutableList<Branch> branchNames,
      ImmutableList<Parse> branchEndStates) {
    this(
        branchNames, branchEndStates,
        HashBasedTable.
            <ProdName, FList<Output>, Optional<FList<Output>>>create());
  }

  private GrammarSearchGraph(
      ImmutableList<Branch> branchNames,
      ImmutableList<Parse> branchEndStates,
      Table<ProdName, FList<Output>, Optional<FList<Output>>>
          expandedPassingReferences) {
    this.branchEndStates = branchEndStates;
    this.branchNames = branchNames;
    this.expandedPassingReferences = expandedPassingReferences;
    lang = branchEndStates.isEmpty() ? null : branchEndStates.get(0).lang;
    this.nonTerminalsToEnter = ImmutableSet.copyOf(Lists.transform(
        branchEndStates,
        new Function<Parse, FList<ProdName>>() {
          @SuppressWarnings("synthetic-access")
          @Override
          public FList<ProdName> apply(Parse p) {
            if (p == null) { throw new IllegalArgumentException(); }
            Preconditions.checkArgument(p.lang == lang);
            return prodNameChainOf(p.stack);
          }
        }));
  }

  private Node getNode(NodeKey key) {
    Node node = nodes.get(key);
    if (node == null) {
      node = new Node();
      nodes.put(key, node);
    } else {
      if (DEBUG) {
        System.out.println("Resusing node for key " + key);
      }
    }
    return node;
  }

  @SuppressFBWarnings(
      value="UPM_UNCALLED_PRIVATE_METHOD",
      justification="Used when debug flag is turned on.")
  private static <T extends Comparable<T>> ImmutableList<T> sortedList(
      Iterable<? extends T> elements) {
    List<T> copy = new ArrayList<>();
    for (T el : elements) {
      copy.add(el);
    }
    Collections.sort(copy);
    return ImmutableList.copyOf(copy);
  }

  JoinResult join() {
    // Initialize the peripheries with the start nodes.
    final int nBranches = branchEndStates.size();
    for (int branchIndex = 0; branchIndex < nBranches; ++branchIndex) {
      Parse unfilteredBranchEndState = branchEndStates.get(branchIndex);
      checkParseStateValid(unfilteredBranchEndState);
      Parse branchEndState = filterOrsThatPushbackContentOnFailure(
          unfilteredBranchEndState);
      if (!branchEndState.stack.isEmpty()) {
        FList<ProdName> startNameChain = prodNameChainOf(branchEndState.stack);
        // We get the prod name before unpausing because the name of any
        // reference being entered should not be on the startNameChain.
        // Not until we enter the referent's body.
        branchEndState = branchEndState.resume();
        Combinator resumedHd = branchEndState.stack.hd();
        if (DEBUG) {
          System.out.println("\nbranchIndex=" + branchIndex);
          TextTables.appendTable(
              System.out,
              column("unfiltered stack", unfilteredBranchEndState.stack),
              column("filtered", branchEndState.stack));
        }
        NodeKey startNodeKey = new NodeKey(
            startNameChain, resumedHd,
            // Entering a paused node enters the head.
            TransitionType.ENTER);
        checkParseStateValid(branchEndState);
        periphery.add(new SearchStep(
            branchIndex, startNodeKey, branchEndState, 0,
            FList.<SearchStep>empty()));
      }
    }

    int roundNum = 0;
    while (!MatchComparator.isBestPossible(bestMatch, branchEndStates.size())) {
      if (DEBUG) {
        int i = 0;
        for (SearchStep step : sortedList(periphery)) {
          System.out.println("Periphery #" + i + " : " + step);
          System.out.println(
              "\tbranch=" + branchNames.get(step.branchIndex)
              + "#" + step.branchIndex);
          System.out.println("\tdistanceFromEnd=" + step.distanceFromBranchEnd);
          System.out.println("\tnRollbacks=" + step.nRollbacks);
          System.out.println("\tcallChain=" + step.key.chain);
          ++i;
        }
      }
      SearchStep step = periphery.poll();
      if (step == null) {
        break;
      }
      final int branchIndex = step.branchIndex;
      if (DEBUG) {
        System.out.println(
            "\n\nPERIPHERY ROUND " + roundNum
            + " branch=" + branchNames.get(branchIndex)
            + ":" + branchIndex);
        ++roundNum;
        TextTables.appendTable(
            System.out,
            column("stack", step.p.stack),
            column("out", step.p.out));
      }
      Node node = getNode(step.key);
      if (node.reachesViaBetterPath(branchIndex, step)) {
        continue;
      }
      node.setReaching(branchIndex, step);
      if (MatchComparator.INSTANCE.compare(bestMatch, node) < 0) {
        if (DEBUG_BEST_JOIN) {
          System.out.println(
              "Better match\n\t" + step.key + "\n\treached via " + step
              + (bestMatch != null
                 ? "\n\tis better than " + bestMatchKey
                 : ""));
        }
        bestMatch = node;
        bestMatchKey = step.key;
      }
      OutputContext ctx = new OutputContext.ListOutputContext(step.p.out);

      Parse p = step.p;
      TransitionType stepTT = TransitionType.of(p.stack.hd());
      Combinator c = p.stack.hd();
      switch (stepTT) {
      case ENTER:
        break;
      case EXIT_PASS: case EXIT_FAIL:
        FList<Combinator> tl = p.stack.tl();
        if (tl.isEmpty()) {
          // We've reached a fixed point since the stacks consisting of only
          // 1. the Empty combinator : ( "" )
          // 2. the Error combinator : ( [] )
          // transition to themselves.  In the parser, this is handled by
          // checking the Completion.
          // Here, we short-circuit since there is no edge out of one of those
          // nodes.
          continue;
        }
        p = p.pop();
        c = p.stack.hd();
        break;
      }

      boolean glossingOverReference = false;
      EnumSet<TransitionType> tts = EnumSet.of(stepTT);

      if (c instanceof ReferenceCombinator) {
        switch (stepTT) {
          case ENTER:
            // Maybe gloss over, depending on whether the reference is
            // entered and whether it enters a prefix of a branch stack.
            ReferenceCombinator rc = (ReferenceCombinator) c;
            FList<ProdName> referentChain = FList.cons(
                rc.name, step.key.chain);
            if (DEBUG) {
              System.out.println(
                  "Checking referentChain " + referentChain
                  + " against nonTerminalsToEnter " + nonTerminalsToEnter);
            }
            if (!nonTerminalsToEnter.contains(referentChain)) {
              glossingOverReference = true;
              switch (lang.lali.consumesInput(rc.name)) {
                case ALWAYS:
                  tts = EnumSet.of(TransitionType.EXIT_FAIL);
                  break;
                case NEVER:
                case SOMETIMES:
                  tts = EnumSet.of(
                      TransitionType.EXIT_FAIL, TransitionType.EXIT_PASS);
                  break;
              }
            }
            break;
          case EXIT_FAIL:
          case EXIT_PASS:
            break;
        }
      }
      if (DEBUG) {
        System.out.println("\nbranchIndex=" + branchIndex);
      }

      for (TransitionType tt : tts) {
        if (DEBUG) {
          System.out.println(
              "c=" + c + " : " + c.getClass().getSimpleName()
              + ", tt=" + tt + ", p.stack=" + p.stack);
        }

        Iterable<ParseDelta> results = null;
        if (glossingOverReference) {

          // We're glossing over a reference.
          Preconditions.checkState(c instanceof ReferenceCombinator);
          Preconditions.checkState(stepTT == TransitionType.ENTER);

          // If we pass or fail out of a glossed-over reference then we
          // need to pretend that that reference was pushed onto the stack.
          switch (tt) {
          case EXIT_PASS:
            // We're expanding a reference.
            final Optional<FList<Output>> expandedOutput = expandReference(
              (ReferenceCombinator) c, step.p.out);
            if (expandedOutput.isPresent()) {
              results = ImmutableList.copyOf(Lists.transform(
                  c.epsilonTransition(TransitionType.EXIT_PASS, lang, ctx),
                  new SubstituteExpandedOutput(expandedOutput.get())));
            } else {
              results = ImmutableList.of();
            }
            break;
          case EXIT_FAIL:
            break;
          case ENTER:
            throw new AssertionError();
          }
        }
        if (results == null) {
          results = c.epsilonTransition(tt, lang, ctx);
        }

        for (ParseDelta result : results) {
          if (DEBUG) {
            System.out.println("index=" + step.branchIndex
                + " " + branchNames.get(step.branchIndex)
                + "\n\tresult=" + result + ", tt=" + tt);
          }

          TransitionType nextTT = TransitionType.of(result.c);
          Parse nextParse = p.apply(result);

          FList<ProdName> nextChain = prodNameChainOf(
              nextTT == TransitionType.ENTER
              // If we're entering a reference, the chain should not include the
              // head until the body has been entered.
              ? nextParse.stack.tl()
              : nextParse.stack
              );
          if (!nextParse.inp.isEmpty()) {
            // If it's not empty, then we're in the process of failing over
            // to an alternative that we committed to and so which will
            // eventually lead to failure of the whole parse.
            // Just short-circuit out.
            // The test above is conflating
            // 1. states that join to failure due to rollback of a committed OR,
            // 2. states which would not join even if we hadn't committed
            // but there's no practical value in doing so.
          } else {
            if (DEBUG) {
              System.out.println(
                  "key=" + step.key
                  + ", tt=" + tt + ", result=" + result
                  + ", nextChain=" + nextChain);
              TextTables.appendTable(
                  System.out,
                  column("Before", step.p.stack),
                  column("Stack", p.stack),
                  column("After", nextParse.stack));
            }

            Combinator nextC = null;
            switch (nextTT) {
            case ENTER:
              nextC = nextParse.stack.hd();
              break;
            case EXIT_PASS: case EXIT_FAIL:
              FList<Combinator> tl = nextParse.stack.tl();
              if (tl.isEmpty()) {
                // If the stack is empty, then nextParse represents a parse
                // end state.
                nextC = nextParse.stack.hd();
              } else {
                nextC = tl.hd();
              }
              break;
            }
            Preconditions.checkNotNull(nextC);
            NodeKey nextNodeKey = new NodeKey(nextChain, nextC, nextTT);

            checkParseStateValid(nextParse);

            int nNewRollbacks =
                nextParse.out.length() < p.out.length()
                ? 1 : 0;

            Preconditions.checkState(
                !(nextC instanceof SingletonCombinator)
                || nextParse.stack.tl().isEmpty(),
                "Lost position information."
                );

            periphery.add(new SearchStep(
                branchIndex,
                nextNodeKey,
                nextParse,
                step.nRollbacks + nNewRollbacks,
                step));
          }
        }
      }
    }

    // Extract the join states.
    if (this.bestMatch == null) {
      return new JoinResult(
          branchEndStates,
          branchEndStates,
          new BitSet());
    }
    if (DEBUG) {
      System.out.println(
          "Joined at " + bestMatchKey + " with " + bestMatch.nReaching()
          + " reaching branches");
    }
    ImmutableList.Builder<Parse> joinStates = ImmutableList.builder();
    BitSet joinedSuccessfully = new BitSet();
    List<Optional<SearchStep>> reaching = bestMatch.getReaching();
    for (int i = 0; i < nBranches; ++i) {
      Optional<SearchStep> reachingStepOpt = i < reaching.size()
          ? reaching.get(i) : Optional.<SearchStep>absent();
      if (!reachingStepOpt.isPresent()) {
        if (DEBUG) { System.out.println("Could not join branch " + i); }
        joinStates.add(branchEndStates.get(i));
      } else {
        SearchStep reachingStep = reachingStepOpt.get();
        joinedSuccessfully.set(i);
        Parse joinState = reachingStep.p;
        Combinator joinStateHd = joinState.stack.hd();
        TransitionType tt = TransitionType.of(joinStateHd);
        switch (tt) {
          case ENTER:
            break;
          case EXIT_PASS:
          case EXIT_FAIL:
            if (!joinState.stack.tl().isEmpty()) {
              joinState = joinState.pop();
              ImmutableList<ParseDelta> deltas = joinState.stack.hd()
                  .epsilonTransition(tt, lang, joinState);
              if (deltas.size() == 1) {
                joinState = joinState.apply(deltas.get(0));
              } else {
                // Loops have two ways of passing : break or continue.
                // TODO: We might have to pick one based on other join states.
              }
            }
            break;
        }
        joinStates.add(joinState.pause());
      }
    }
    return new JoinResult(
        branchEndStates,
        joinStates.build(),
        joinedSuccessfully);
  }

  /**
   * Checks that there are all and only ephemeral outputs needed by the
   * stack elements.
   */
  private static void checkParseStateValid(Parse p) {
    if (DEBUG || true) {
      FList<Output> ephemerals = p.out.filter(
          Predicates.instanceOf(EphemeralOutput.class));
      for (Combinator c : p.stack.tl()) {
        EphemeralOutput target = null;
        // TODO: this is brittle.  Ideally, we wouldn't filter out ephemrals
        // not here, or have this automatically extend to other combinator
        // types.
        if (c instanceof OrCombinator) {
          target = BranchMarker.INSTANCE;
        } else if (c instanceof LoopCombinator) {
          target = LoopMarker.INSTANCE;
        } else if (c instanceof LookaheadCombinator) {
          target = LookaheadMarker.INSTANCE;
        }
        if (target != null) {
          Optional<Output> hd = ephemerals.hdOpt();
          if (!(hd.isPresent() && target.equals(hd.get()))) {
            TextTables.appendTable(
                System.out,
                TextTables.column("problem stack", p.stack),
                TextTables.column("out", p.out));
            throw new AssertionError(
                "Invalid parse state.  Expected " + target + " got " + hd);
          } else {
            ephemerals = ephemerals.tl();
          }
        }
      }
    }
  }

  private final Table<ProdName, FList<Output>, Optional<FList<Output>>>
      expandedPassingReferences;
  private Optional<FList<Output>> expandReference(
      ReferenceCombinator ref, FList<Output> out) {
    ProdName name = ref.name;
    FList<Output> varOuts = out.filter(
         Predicates.instanceOf(VariableOutput.class));
    Optional<FList<Output>> expansion =
        expandedPassingReferences.get(name, varOuts);
    if (expansion == null) {
      SeqCombinator refThenStop =
          (SeqCombinator) lang.getRefBeforeAnyChar(name);
      Combinator stop = refThenStop.second;
      Parse stateBeforeEnteringReference = Parse.builder(lang)
          .withStack(FList.of(
              refThenStop,
              ref,
              Pause.pause(lang.get(name))))
          .withOutput(FList.cons(NO_COALESCE, varOuts))
          .build();
      Parse stateAfterLeavingReference = Parse.builder(lang)
          .withStack(FList.cons(Pause.pause(stop), FList.<Combinator>empty()))
          .withOutput(FList.cons(NO_COALESCE, varOuts))
          .build();

      ImmutableList<Parse> statesBeforeAndAfterReference = ImmutableList.of(
          stateBeforeEnteringReference, stateAfterLeavingReference);

      GrammarSearchGraph expander = new GrammarSearchGraph(
          ImmutableList.<Branch>of(
              new NonTerminalBoundaryBranch("enter:", name),
              new NonTerminalBoundaryBranch("exit:", name)
              ),
          statesBeforeAndAfterReference,
          expandedPassingReferences);
      JoinResult joinResult = expander.join();
      Parse joined0 = joinResult.branchJoinStates.get(0);
      if (joined0.stack.equals(stateAfterLeavingReference.stack)) {
        int nInExpansion = joined0.out.length()
            - stateBeforeEnteringReference.out.length();
        FList<Output> expansionRev = FList.empty();
        FList<Output> joined0Out = joined0.out;
        for (int i = 0; i < nInExpansion; ++i) {
          expansionRev = Parse.consOutput(joined0Out.hd(), expansionRev);
          joined0Out = joined0Out.tl();
        }
        expansion = Optional.of(expansionRev.rev());
      } else {
        expansion = Optional.absent();
      }
      this.expandedPassingReferences.put(name, varOuts, expansion);
    }
    return expansion;
  }

  private static final Predicate<Output> NOT_BRANCH_MARKER =
      new Predicate<Output>() {
    @Override
    public boolean apply(Output x) {
      return x != BranchMarker.INSTANCE;
    }
  };

  private static final Output NO_COALESCE = new UnaryOutput() {

    @Override
    public Optional<Output> coalesceWithFollower(Output next) {
      return Optional.absent();
    }

    @Override
    public boolean isParseRelevant(PartialOutput po) {
      return true;
    }

    @Override
    protected int compareToSameClass(UnaryOutput that) {
      return 0;
    }

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    protected String getVizTypeClassName() {
      return "no-coalesce";
    }

    @Override
    protected void visualizeBody(DetailLevel lvl, VizOutput out)
    throws IOException {
      out.text("{no-coalesce}");
    }
  };

  static Parse filterOrsThatPushbackContentOnFailure(Parse p) {
    // Count the number of branch markers before the first string output.
    // All OrCombinators past that would push-back unconsumed input violating
    // our epsilon-transition assumptions, so we commit to them not failing.
    FList<Output> filteredOutputRev = FList.empty();
    final int nSafeBranchMarkers;
    {
      int nSafe = 0;
      FList<Output> unfilteredOutput = p.out;
      while (!unfilteredOutput.isEmpty()) {
        Output o = unfilteredOutput.hd();

        unfilteredOutput = unfilteredOutput.tl();
        filteredOutputRev = Parse.consOutput(o, filteredOutputRev);

        if (o instanceof StringOutput) {
          // Filter out all branch markers from the remainder.
          filteredOutputRev = unfilteredOutput.filterRevOnto(
              NOT_BRANCH_MARKER, filteredOutputRev);
          break;
        }
        if (o == BranchMarker.INSTANCE) {
          ++nSafe;
        }
      }
      nSafeBranchMarkers = nSafe;
    }

    FList<Combinator> filteredStackRev = p.stack.filterRev(
        new Predicate<Combinator>() {
          int nSafeOrsRemaining = nSafeBranchMarkers;

          @Override
          public boolean apply(Combinator c) {
            if (c instanceof OrCombinator) {
              if (nSafeOrsRemaining == 0) {
                return false;
              }
              --nSafeOrsRemaining;
            }
            return true;
          }
        });

    FList<Output> filteredOutput = FList.empty();
    for (Output fo : filteredOutputRev) {
      filteredOutput = Parse.consOutput(fo, filteredOutput);
    }

    return p.builder()
        .withOutput(filteredOutput)
        .withStack(filteredStackRev.rev())
        .build();
  }

  static final class MatchComparator implements Comparator<Node>, Serializable {
    private static final long serialVersionUID = -851358832778765717L;

    static final MatchComparator INSTANCE = new MatchComparator();

    @Override
    public int compare(Node a, Node b) {
      if (a == null) {
        return b == null ? 0 : -1;
      } else if (b == null) {
        return 1;
      }
      int delta = Integer.compare(a.nReaching(), b.nReaching());
      if (delta == 0) {
        delta = Integer.compare(b.maxNRollbacks(), a.maxNRollbacks());
      }
      return delta;
    }

    static boolean isBestPossible(
        @Nullable Node bestMatch, int nBranchEndStates) {
      return bestMatch != null
          && bestMatch.nReaching() == nBranchEndStates
          && bestMatch.maxNRollbacks() == 0;
    }

  }
}


final class NonTerminalBoundaryBranch
extends AbstractVisualizable implements ParseWatcher.Branch {
  final String prefix;
  final ProdName prodName;

  NonTerminalBoundaryBranch(String prefix, ProdName prodName) {
    this.prefix = prefix;
    this.prodName = prodName;
  }

  @Override
  protected String getVizTypeClassName() {
    return "non-terminal-boundary";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
      throws IOException {
    out.text(prefix + prodName.text);
  }

}


final class SubstituteExpandedOutput
implements Function<ParseDelta, ParseDelta> {
  final FList<Output> output;

  SubstituteExpandedOutput(FList<Output> output) {
    this.output = output;
  }

  @Override
  public ParseDelta apply(ParseDelta enterResult) {
    if (enterResult == null) {
      throw new IllegalArgumentException();
    }
    return ParseDelta
        .pass()
        .withIOTransform(enterResult.ioTransform)
        .withOutputs(output)
        .build();
  }
}
