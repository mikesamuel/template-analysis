package com.google.template.autoesc;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.ParseWatcher.Branch;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.out.BranchMarker;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.viz.TextTables;

/**
 * A pausable parser that consumes an {@link InputCursor extensible input}
 * and produces a stream of {@link Output output events}.
 */
public final class Parser implements Joinable {
  /** A logger that receives notifications as the parse progresses. */
  final ParseWatcher watcher;
  final Branch current;
  private Parse parse;

  /** */
  public Parser(Branch current, Language lang, ParseWatcher watcher) {
    this(
        current,
        Parse.builder(lang)
            .withStack(FList.of(lang.get(lang.defaultStartProdName)))
            .build(),
        watcher);
  }

  /** */
  public Parser(Branch current, Parse parse, ParseWatcher watcher) {
    this.current = current;
    this.watcher = watcher;
    this.parse = parse;
  }

  /**
   * Starts parsing at the
   * {@link Language#defaultStartProdName default start production}.
   */
  public void startParse() {
    startParse(parse.lang.defaultStartProdName);
  }

  /** Starts parsing at the named production. */
  public void startParse(ProdName startName) {
    startParse(parse.lang.get(startName));
  }

  /** Starts parsing at the given combinator. */
  public void startParse(Combinator start) {
    parse = parse.builder().withStack(FList.of(start)).build();
    watcher.started(parse);
    continueParse();
  }

  /** Continues parsing, usually after more input has been provided. */
  public void continueParse() {
    // Continue unless we've reached a PASS/FAIL state.
    while (getState() == Completion.IN_PROGRESS) {
      if (parse.stack.isEmpty()) {
        throw new IllegalStateException();
      }
      // Pop the top and re-enter it.
      // This is OK because if the stack was paused, then the top is a sequence
      // starting with EMPTY as the first operand which we can safely
      // re-enter since EMPTY is idempotent.
      parse = parse.smallStep(watcher);
      if (parse.couldPause()) {
        parse = parse.pause();
      }
      if (parse.isPaused()) {
        break;
      }
    }
  }

  /**
    * Makes more input available to the parser.
    * @param src describes the source of the input which makes error
    *   messages easier to understand.
    */
  public void addInput(final String input, final Source src) {
    parse = parse.builder().withInput(parse.inp.extend(input, src)).build();
    watcher.inputAdded(parse);
  }

  /**
   * Finish parsing.
   * This makes it clear that no more input will be available.
   */
  public void finishParse() {
    parse = parse.builder().withInput(parse.inp.finish()).build();
    continueParse();
    watcher.finished(parse, current, getState());
  }

  /**
   * The output produced thus far.
   * If {@link #getState} is {@link Completion#PASSED} and the
   * {@link InputCursor#isEmpty input is empty} then the output is
   * a consistent view of the whole input.
   */
  public FList<Output> getOutput() {
    return parse.out;
  }

  /**
   * The name of the branch.
   */
  @Override
  public ParseWatcher.Branch getBranch() {
    return current;
  }

  /**
   * The current parse state.
   */
  @Override
  public Parse getParse() {
    return parse;
  }

  /**
   * The parse watcher.
   */
  public ParseWatcher getWatcher() {
    return watcher;
  }

  /** The state of completion of the current parse. */
  public Completion getState() {
    return Completion.of(parse);
  }

  /**
   * Split the parse so two branches through a template can be handled
   * independently.
   */
  public Parser fork(Branch to) {
    Parse forkParse = commit(parse);
    watcher.forked(forkParse, current, to);
    return new Parser(to, forkParse, watcher);
  }

  private static final Language FAIL_LANG =
      new Language.Builder().define("fail", Combinators.error()).build();

  /**
   * Joins {@link #fork forked} parses as parsing progresses through
   * the larger template.
   */
  public static Parser join(ImmutableList<Parser> forks, Branch to)
  throws UnjoinableException {
    int nForks = forks.size();
    if (nForks == 0) {
      return new Parser(
          to,
          Parse
              .builder(FAIL_LANG)
              .withStack(FList.of(Combinators.error()))
              .build(),
          TeeParseWatcher.DO_NOTHING_WATCHER);
    }

    if (GrammarSearchGraph.DEBUG) {
      ImmutableList.Builder<TextTables.Col> cols = ImmutableList.builder();
      for (Parser fork : forks) {
        cols.add(TextTables.column(fork.current.toString(), fork.parse.stack));
      }
      TextTables.appendTable(System.out, cols.build());
    }

    GrammarSearchGraph.JoinResult joinResult = GrammarSearchGraph.join(forks);

    if (!joinResult.allJoined()) {
      if (GrammarSearchGraph.DEBUG) {
        ImmutableList.Builder<TextTables.Col> cols = ImmutableList.builder();
        for (int i = 0; i < nForks; ++i) {
          Parser fork = forks.get(i);
          cols.add(TextTables.column(
              fork.current.toString(),
              joinResult.branchJoinStates.get(i).stack));
        }
        TextTables.appendTable(System.out, cols.build());
      }

      int problemBranch = joinResult.firstNotJoined();
      int exampleBranch = problemBranch == 0 ? 1 : 0;
      checkReconciledParseStates(
          joinResult.branchJoinStates.get(exampleBranch),
          joinResult.branchJoinStates.get(problemBranch));
      throw new AssertionError();
    }

    ImmutableList<Parse> joinStates = joinResult.branchJoinStates;

    Parse finalJoinState = joinStates.get(0).builder()
        .withOutput(Reconciliation.reconcileOutputs(joinStates))
        .build();
    finalJoinState = commit(finalJoinState);

    if (GrammarSearchGraph.DEBUG) {
      ImmutableList.Builder<TextTables.Col> cols = ImmutableList.builder();
      for (Parser fork : forks) {
        cols.add(TextTables.column(fork.current.toString(), fork.parse.stack));
      }
      cols.add(TextTables.column("final", finalJoinState.stack));
      TextTables.appendTable(System.out, cols.build());
    }

    for (int i = 0; i < nForks; ++i) {
      Parser fork = forks.get(i);
      fork.watcher.joinFinished(
          joinResult.branchJoinStates.get(i), fork.current,
          finalJoinState, to);
    }

    return new Parser(to, finalJoinState, forks.get(0).watcher);
  }


  private static Parse commit(Parse parse) {
    FList<Combinator> committedStack = Reconciliation.filterStack(
        parse.stack);
    // TODO: convert loops to seq(empty, opt(loop)) so that we can commit to
    // the iteration and then allow n iterations afterwards without needing
    // loop markers.
    FList<Output> committedOutput = Reconciliation.filterOutput(
        parse.out.filter(
            new Predicate<Output>() {
              @Override
              public boolean apply(Output o) {
                return !BranchMarker.INSTANCE.equals(o);
              }
            }));
    return parse.builder()
        .withStack(committedStack).withOutput(committedOutput)
        .build();
  }

  private static Parse checkReconciledParseStates(Parse a, Parse b)
  throws UnjoinableException {
    // TODO: move lookaheads that have not consumed any output to the end and
    // union them.
    Reconciliation.dumpState(a, b, null, " ", false);

    if (!a.inp.equals(b.inp)) {
      throw new UnjoinableException("Inputs differ", a, a.inp, b, b.inp);
    }
    if (!a.stack.equals(b.stack)) {
      throw new UnjoinableException("Stacks differ", a, a.stack, b, b.stack);
    }
    if (!a.out.equals(b.out)) {
      throw new UnjoinableException("Outputs differ", a, a.out, b, b.out);
    }
    return a;
  }
}
