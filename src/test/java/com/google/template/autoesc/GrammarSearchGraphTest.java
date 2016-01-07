package com.google.template.autoesc;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.ParseWatcher.Branch;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.OrCombinator;
import com.google.template.autoesc.combimpl.ReferenceCombinator;
import com.google.template.autoesc.combimpl.SeqCombinator;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.BranchMarker;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.PartialOutput;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.out.UnaryOutput;
import com.google.template.autoesc.var.Scope;
import com.google.template.autoesc.var.Value;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextTables;
import com.google.template.autoesc.viz.VizOutput;

import static com.google.template.autoesc.out.Side.LEFT;
import static com.google.template.autoesc.out.Side.RIGHT;
import static com.google.template.autoesc.viz.TextTables.column;

@SuppressWarnings({"javadoc", "static-method"})
public final class GrammarSearchGraphTest {
  private static final Combinators C = Combinators.get();

  private static Parse after(Language lang, String rawInput) {
    Parser p = new Parser(
        new TestBranchName(0), lang, TeeParseWatcher.DO_NOTHING_WATCHER);
    p.startParse();
    p.addInput(rawInput, Source.UNKNOWN);
    p.continueParse();
    return p.getParse();
  }

  private static Parse advanceUntil(Parse p, Combinator c) {
    return advanceUntil(p, headMatches(c));
  }

  private static Parse advancePast(Parse p, Combinator c) {
    return advancePast(p, headMatches(c));
  }

  private static Parse advanceUntil(Parse p, Predicate<? super Parse> q) {
    return advance(p, q, Predicates.alwaysFalse());
  }

  private static Parse advancePast(Parse p, Predicate<? super Parse> q) {
    return advance(p, Predicates.alwaysFalse(), q);
  }

  private static Parse advance(
      Parse start, Predicate<? super Parse> before,
      Predicate<? super Parse> after) {
    Parse p = start;
    boolean wasPaused = p.isPaused();
    if (wasPaused) {
      p = p.resume();
    }
    while (true) {
      if (before.apply(p)) {
        break;
      }
      Parse op = p;
      p = p.smallStep(TeeParseWatcher.DO_NOTHING_WATCHER);
      if (after.apply(op)) {
        break;
      }
    }
    if (wasPaused) {
      p = p.pause();
    }
    return p;
  }

  private static Predicate<Parse> headMatches(final Combinator c) {
    return new Predicate<Parse>() {
      @Override
      public boolean apply(Parse p) {
        return c.equals(p.stack.hd());
      }
    };
  }


  /**
   * {@code
   * assertJoinStates(
   *     start0, start1, start2,
   *     join0,  join1,  join2);
   * }
   */
  private static void assertJoinStates(
      Parse... branchEndStatesAndExpectedJoinStates) {
    Preconditions.checkArgument(
        (branchEndStatesAndExpectedJoinStates.length & 1) == 0);
    ImmutableList<Joinable> joinables;
    ImmutableList<Parse> expectedBranchJoinStates;
    {
      ImmutableList.Builder<Joinable> joinablesBuilder =
          ImmutableList.builder();
      ImmutableList.Builder<Parse> expectedBranchJoinStatesBuilder =
          ImmutableList.builder();
      for (int i = 0, n = branchEndStatesAndExpectedJoinStates.length / 2;
          i < n; ++i) {
        joinablesBuilder.add(
            new TestJoinable(i, branchEndStatesAndExpectedJoinStates[i]));
        expectedBranchJoinStatesBuilder.add(
            branchEndStatesAndExpectedJoinStates[i + n]);
      }
      joinables = joinablesBuilder.build();
      expectedBranchJoinStates = expectedBranchJoinStatesBuilder.build();
    }

    ImmutableList<Parse> actualBranchJoinStates =
        GrammarSearchGraph.join(joinables).branchJoinStates;
    if (!expectedBranchJoinStates.equals(actualBranchJoinStates)) {
      int n = expectedBranchJoinStates.size();
      Assert.assertEquals(n, actualBranchJoinStates.size());
      for (int i = 0; i < n; ++i) {
        Parse expected = expectedBranchJoinStates.get(i);
        Parse actual = actualBranchJoinStates.get(i);
        if (!expected.equals(actual)) {
          if (!expected.stack.equals(actual.stack)) {
            TextTables.appendTable(
                System.err,
                column("Expected Stack " + i, expected.stack),
                column("Actual Stack " + i, actual.stack));
          }
          if (!expected.out.equals(actual.out)) {
            TextTables.appendTable(
                System.err,
                column("Expected Output " + i, expected.out),
                column("Actual Output " + i, actual.out));
          }
          if (!expected.inp.equals(actual.inp)) {
            System.err.println("Inputs don't match for branchIndex " + i);
          }
        }
      }
      Assert.fail("Join states do not match");
    }
  }

  @Test
  public void testSimpleStacks() {
    ProdName start = new ProdName("start");
    Language lang = new Language.Builder()
        .define(start, C.anyChar())
        .build();

    Combinator anyChar = lang.get(start);

    Parse branch = Parse.builder(lang)
        .withStack(FList.<Combinator>of(Pause.pause(anyChar)))
        .build();
    assertJoinStates(
        branch, branch,
        branch, branch);
  }

  @Test
  public void testSimpleTransition() {
    ProdName emptyAtEnd = new ProdName("emptyAtEnd");
    ProdName empty = new ProdName("empty");
    Language lang = new Language.Builder()
        .define(emptyAtEnd,
            C.seq(C.ref(empty), C.endOfInput()))
        .define(empty, EmptyCombinator.INSTANCE)
        .build();
    Combinator seq = lang.get(emptyAtEnd);
    Combinator ref = seq.children().get(0);
    Parse branch0 = Parse.builder(lang)
        .withStack(FList.of(Pause.pause(seq)))
        .build();
    Parse branch1 = Parse.builder(lang)
        .withStack(FList.of(seq, Pause.pause(ref)))
        .build();
    assertJoinStates(
        branch0, branch1,
        branch1, branch1);
  }

  @Test
  public void testJoinThatDescendsIntoReferent() {
    Combinator fooLit, aRef;

    Language lang = new Language.Builder()
        .define("Start", C.seq(
            C.opt(fooLit = C.lit("foo")),
            C.or(aRef = C.ref("A"), C.ref("B"))))
        .unbounded()
        .define("A", C.opt(C.chars('a')))
        .unbounded()
        .define("B", C.opt(C.chars('b')))
        .unbounded()
        .build();

    // A branch at the very start
    Parse branch0 = after(lang, "");
    // A branch at the start of "foo"
    Parse branch1 = advanceUntil(after(lang, ""), fooLit);
    // A branch at the start in non-terminal A.
    Parse branch2 = advancePast(after(lang, "foo"), aRef);

    Parse branch2NoFoo = branch2.builder()
        .withOutput(
            branch2.out.filter(
                Predicates.not(Predicates.instanceOf(StringOutput.class))))
        .build();

    assertJoinStates(
        branch0, branch1, branch2,
        branch2NoFoo, branch2NoFoo, branch2);
  }

  @Test
  public void testJoinThatGlossesOverAReference() {
    // Start := "foo"? (A | B);
    // A := "a";
    // B := "b"?;
    Language lang = new Language.Builder()
        .define("Start", C.seq(
            C.opt(C.lit("foo")),
            C.or(C.ref("A"), C.ref("B"))))
        .unbounded()
        .define("A", C.chars('a'))
        .unbounded()
        .define("B", C.opt(C.chars('b')))
        .unbounded()
        .build();

    SeqCombinator start = (SeqCombinator) lang.get(new ProdName("Start"));
    OrCombinator aOrB = (OrCombinator) start.second;
    ReferenceCombinator bRef = (ReferenceCombinator) aOrB.second;

    // A branch at the very start
    Parse branch0 = Parse.builder(lang)
        .withStack(FList.of(Pause.pause(start)))
        .build();

    // A branch before the start of ref B
    Parse branch1 = Parse.builder(lang)
        .withStack(FList.of(Pause.pause(bRef)))
        .build();

    // To join start to refB, we need to assume that A fails.

    assertJoinStates(
        branch0, branch1,
        branch1, branch1);
  }

  @Test
  public void testJoinThatGlossesOverAReferenceAndLaterExpandsIt() {
    ProdName start = new ProdName("Start");
    ProdName aName = new ProdName("A");
    ProdName bName = new ProdName("B");

    Language lang = new Language.Builder()
        .define(start, C.seq(C.ref(aName), C.ref(bName)))
        .unbounded()
        .define(aName, C.emit(TEST_OUTPUT))
        .define(bName, C.anyChar())
        .build();

    Combinator bRef = lang.get(start).children().get(1);
    Preconditions.checkState(
        bRef instanceof ReferenceCombinator
        && bName.equals(((ReferenceCombinator) bRef).name));


    Combinator startBody = lang.get(start);
    Combinator bBody = lang.get(bName);

    // A branch at the start.
    Parse branch0 = Parse.builder(lang)
        .withStack(FList.of(Pause.pause(startBody)))
        .build();
    // A branch in B that has passed through A.
    Parse branch1 = Parse.builder(lang)
        .withStack(
            FList.of(
                bRef,
                Pause.pause(bBody)))
        .withOutput(FList.of(
            new Boundary(LEFT, aName),
            TEST_OUTPUT,
            new Boundary(RIGHT, aName)
            ))
        .build();

    assertJoinStates(
        branch0, branch1,
        branch1, branch1);
  }

  @Test
  public void testJoinThatLeavesBranches() {
    // A := ("fo" "o"? | "ba" "r"?) "baz"

    // Branch 0 has seen "fo".
    // Branch 1 has seen "ba".
    // They join before "baz".

    ProdName a = new ProdName("A");

    Language lang = new Language.Builder()
        .define(
            a,
            C.seq(
                C.or(
                    C.seq(
                        C.lit("fo"),
                        C.opt(C.chars('o'))),
                    C.seq(
                        C.lit("ba"),
                        C.opt(C.chars('r')))),
                C.lit("baz"))
            )
        .unbounded()
        .build();

    Parse branch0 = after(lang, "fo");
    Parse branch1 = after(lang, "ba");

    Parse beforeBar = after(lang, "foo");
    Parse joined0 = beforeBar
        .builder()
        .withOutput(FList.<Output>of(new StringOutput("fo", "fo")))
        .build();
    Parse joined1 = beforeBar
        .builder()
        .withOutput(FList.<Output>of(new StringOutput("ba", "ba")))
        .build();

    boolean spammy = false;
    if (spammy) {
      TextTables.appendTable(
          System.err,
          TextTables.column("b0", branch0.stack),
          TextTables.column("b1", branch1.stack),
          TextTables.column("j0", joined0.stack),
          TextTables.column("j1", joined1.stack));
      TextTables.appendTable(
          System.err,
          TextTables.column("b0", branch0.out),
          TextTables.column("b1", branch1.out),
          TextTables.column("j0", joined0.out),
          TextTables.column("j1", joined1.out));
    }

    assertJoinStates(
        branch0, branch1,
        joined0, joined1);
  }

  @Test
  public void testOrsThatConsumeArePrecommitted() {
    ProdName start = new ProdName("Start");
    ProdName a = new ProdName("A");
    ProdName b = new ProdName("B");

    // We need to start two branches that each are inside two ORs, and then
    // join at a point still within those two ORs.
    // Between the two ORs, some input was consumed which means we have to
    // commit to the second OR but not the first.

    // Start := A?
    // A := [x]? B?;
    // B := [y]? [z];

    // Branch 1: Seen "xy"
    // Branch 2: Has seen "x"
    // Join point, before [z] but without the branch implicit in (B?)

    final Combinator optionalACall = C.opt(C.ref(a));
    final Combinator optionalBCall = C.opt(C.ref(b));

    Language lang = new Language.Builder()
        .define(
            start,
            optionalACall
            )
        .unbounded()
        .define(
            a,
            C.seq(
                C.opt(C.chars('x')),
                optionalBCall))
        .unbounded()
        .define(b, C.seq(
            C.opt(C.chars('y')),
            C.opt(C.chars('z'))))
        .unbounded()
        .build();

    Parse parse0 = after(lang, "x");
    Parse parse1 = after(lang, "xy");

    final class NotIn<T> implements Predicate<T> {
      final ImmutableSet<T> exclusions;
      NotIn(ImmutableSet<T> exclusions) {
        this.exclusions = exclusions;
      }
      @Override
      public boolean apply(T x) {
        return !exclusions.contains(x);
      }
    }

    // Make sure that we did filter out a node.
    FList<Combinator> committedStack0 = parse1.stack
        .filter(new NotIn<>(ImmutableSet.of(optionalACall)));
    FList<Combinator> committedStack1 = parse1.stack
        .filter(new NotIn<>(ImmutableSet.of(optionalACall, optionalBCall)));
    Preconditions.checkState(parse1.stack.length() > committedStack0.length());
    Preconditions.checkState(parse1.stack.length() > committedStack1.length());

    Parse joined0 = parse1.builder()
        .withStack(committedStack0)
        .withOutput(FList.<Output>of(
            new StringOutput("x", "x"),
            BranchMarker.INSTANCE
            ))
        .build();
    Parse joined1 = parse1.builder()
        .withStack(committedStack1)
        .withOutput(FList.<Output>of(
            new StringOutput("xy", "xy")
            ))
        .build();

    assertJoinStates(
        parse0,  parse1,
        joined0, joined1);
  }

  @Ignore
  @Test
  public void testNegativeLookaheadFromFailedOutOfBranches() {
    throw new AssertionError("TODO");
  }

  @Test
  public final void testExpandedReferenceThatSetsVarReadDuringSearch() {
    // A ::== {var x} B C D {/var};
    // B ::== ("x"; {x := true}) / {x := false};
    // C ::== "x" | {x == false};
    // D ::== "y"?

    ProdName aName = new ProdName("A");
    ProdName bName = new ProdName("B");
    ProdName cName = new ProdName("C");
    final ProdName dName = new ProdName("D");

    Variable<Boolean> v = Variable.create("v", Boolean.class);

    Language lang = new Language.Builder()
        .define(aName, C.decl(
            v,
            C.seq(
                C.ref(bName),
                C.ref(cName),
                C.ref(dName))
            ))
        .unbounded()
        .define(bName, C.or(
            C.seq(C.lit("x"), C.set(v, true)),
            C.set(v, false)
            ))
        .define(cName, C.or(C.lit("x"), C.in(v, false)))
        .define(dName, C.opt(C.lit("y")))
        .build();

    ReferenceCombinator ref = find(
        lang.get(aName),
        ReferenceCombinator.class,
        new Predicate<ReferenceCombinator>() {
          @Override
          public boolean apply(ReferenceCombinator c) {
            return dName.equals(c.name);
          }
        })
        .get();

    final StringOutput literalX = new StringOutput("x", "x");

    // A branch at the start.
    Combinator aBody = lang.get(aName);
    Parse branch0 = Parse.builder(lang)
        .withStack(FList.of(Pause.pause(aBody)))
        .build();
    // A branch in B that has passed through B and C.
    Parse branch1 = Parse.builder(lang)
        .withStack(
            FList.of(
                ((SeqCombinator) ((SeqCombinator) aBody).second).second,
                ref,
                Pause.pause(lang.get(dName))))
        .withOutput(FList.<Output>of(
            new Scope(LEFT, v),
            new Boundary(LEFT, bName),
            new Value<>(v, false),
            new Boundary(RIGHT, bName),
            new Boundary(LEFT, cName),
            literalX,
            new Boundary(RIGHT, cName)
            ))
        .build();

    Parse branch1NoX = branch1
        .builder()
        .withOutput(branch1.out.filter(new Predicate<Output>() {
          @Override
          public boolean apply(Output o) {
            return !literalX.equals(o);
          }
        }))
        .build();

    assertJoinStates(
        branch0,    branch1,
        branch1NoX, branch1);
  }


  static <T extends Combinator> Optional<T> find(
      Combinator root, Class<T> typ, Predicate<? super T> p) {
    Optional<T> soleResult = Optional.absent();
    if (typ.isInstance(root)) {
      T x = typ.cast(root);
      if (p.apply(x)) {
        soleResult = Optional.of(x);
      }
    }
    for (Combinator child : root.children()) {
      Optional<T> childResult = find(child, typ, p);
      if (childResult.isPresent()) {
        if (soleResult.isPresent()) {
          throw new IllegalStateException(
              "not unique : " + soleResult.get() + " and " + childResult.get());
        }
        soleResult = childResult;
      }
    }
    return soleResult;
  }


  private static final Output TEST_OUTPUT = new UnaryOutput() {
    @Override
    public Optional<Output> coalesceWithFollower(Output next) {
      return Optional.absent();
    }

    @Override
    public boolean isParseRelevant(PartialOutput po) {
      return true;
    }

    @Override
    protected String getVizTypeClassName() {
      return "test-output";
    }

    @Override
    protected void visualizeBody(DetailLevel lvl, VizOutput out)
    throws IOException {
      out.text("TestOutput");
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
    protected int compareToSameClass(UnaryOutput that) {
      Preconditions.checkState(that == this);
      return 0;
    }
  };
}

final class TestJoinable implements Joinable {
  final ParseWatcher.Branch branch;
  final Parse parse;

  TestJoinable(int branchIndex, Parse branchEndState) {
    this.branch = new TestBranchName(branchIndex);
    this.parse = branchEndState;
  }

  @Override
  public Branch getBranch() {
    return branch;
  }
  @Override
  public Parse getParse() {
    return parse;
  }
}

final class TestBranchName
extends AbstractVisualizable implements ParseWatcher.Branch {
  final int branchIndex;

  TestBranchName(int branchIndex) {
    this.branchIndex = branchIndex;
  }
  @Override
  protected String getVizTypeClassName() {
    return "test-branch-name";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
      throws IOException {
    out.text("branch-" + branchIndex);
  }

}

