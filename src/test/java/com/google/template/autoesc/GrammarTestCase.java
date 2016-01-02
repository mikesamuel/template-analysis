package com.google.template.autoesc;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckReturnValue;

import org.junit.Assert;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.demo.BranchRunner;
import com.google.template.autoesc.demo.BranchRunner.Branch;
import com.google.template.autoesc.demo.BranchRunner.StringSourcePair;
import com.google.template.autoesc.demo.DemoServer;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.out.EphemeralOutput;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;
import com.google.template.autoesc.viz.Visualizable;


/**
 * A test for a parser run.
 */
public final class GrammarTestCase {
  /** Versions of the grammar to try. */
  public final ImmutableList<Language> allLangs;
  /** @see StepLimitingParseWatcher */
  public final int stepLimit;
  /** The start production name. */
  public final ProdName startProduction;
  /**
   * Used to filter the actual output before comparing it against
   * {@link TestBranch#expectedOutputs}.
   */
  public final Predicate<Output> outputFilter;
  /** The start branch. */
  public final TestBranch startBranch;

  /**
   * Parses can fork and join so a branch is an edge in that DAG.
   */
  public static final class TestBranch extends Branch<TestBranch> {
    /** Expected end state. */
    public final Completion expectedEndState;
    /** Expected output. */
    public final Optional<ImmutableList<Output>> expectedOutputs;
    /** Expected unparsed suffix of the input. */
    public final String expectedUnparsedInput;

    TestBranch(
        String name,
        ImmutableList<StringSourcePair> inputs,
        Completion expectedEndState,
        Optional<ImmutableList<Output>> expectedOutputs,
        String expectedUnparsedInput,
        ImmutableList<TestBranch> followers) {
      super(name, inputs, followers);
      this.expectedEndState = expectedEndState;
      this.expectedOutputs = expectedOutputs;
      this.expectedUnparsedInput = expectedUnparsedInput;
    }
  }

  /** */
  public GrammarTestCase(
      ImmutableList<Language> allLangs,
      int stepLimit,
      ProdName startProduction,
      Predicate<Output> outputFilter,
      TestBranch startBranch) {
    this.allLangs = allLangs;
    this.stepLimit = stepLimit;
    this.startProduction = startProduction;
    this.outputFilter = outputFilter;
    this.startBranch = startBranch;
  }

  /** Actually run the test. */
  public void run() throws UnjoinableException {
    for (Language lang : allLangs) {
      run(lang);
    }
  }

  private void run(Language lang) throws UnjoinableException {
    StepLimitingParseWatcher stepLimiter = new StepLimitingParseWatcher();
    SilentLoggingWatcher silentWatcher = new SilentLoggingWatcher();
    BranchTerminationAssertionWatcher btaWatcher =
        new BranchTerminationAssertionWatcher(this);
    stepLimiter.setStepLimit(stepLimit);
    ParseWatcher watcher = TeeParseWatcher.create(
        stepLimiter, silentWatcher, btaWatcher);
    Parser p = new Parser(startBranch, lang, watcher);
    BranchRunner<TestBranch> br = new BranchRunner<>(
        startBranch, startProduction);
    try {
      br.run(p);
    } catch (RuntimeException | Error | UnjoinableException e) {
      doParseFailureCleanup(lang, silentWatcher);
      throw e;
    }

    final Set<TestBranch> untested = btaWatcher.getUntested();
    Assert.assertTrue("Untested: " + untested, untested.isEmpty());
  }

  private void doParseFailureCleanup(
      Language lang, SilentLoggingWatcher watcher) {
    Optional<URI> testDemoUri = getTestUrl(lang);
    maybeDumpDemoServerUri(testDemoUri);
    try {
      watcher.printLog(System.err);
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    maybeDumpDemoServerUri(testDemoUri);
  }


  private static void maybeDumpDemoServerUri(Optional<URI> demoServerUri) {
    if (demoServerUri.isPresent()) {
      System.err.println(
          "You can debug this by starting the demo server "
          + "and browsing to\n\n\t" + demoServerUri.get() + "\n");
    }
  }

  private Optional<URI> getTestUrl(Language lang) {
    Optional<String> langSource = lang.demoServerQuery;
    if (!langSource.isPresent()) { return Optional.absent(); }
    DemoServerQuery.Builder b = DemoServerQuery.builder(langSource.get());
    if (!startProduction.equals(lang.defaultStartProdName)) {
      b.startProd(startProduction);
    }
    if (stepLimit != StepLimitingParseWatcher.DEFAULT_TEST_STEP_LIMIT) {
      b.stepLimit(stepLimit);
    }
    b.branch(startBranch);
    return Optional.of(DemoServer.getTestUrl(b.build()));
  }


  @SuppressWarnings("javadoc")
  public static final class Builder {
    private ImmutableList.Builder<Language> lang = ImmutableList.builder();
    private ProdName start;
    private int stepLimit = StepLimitingParseWatcher.DEFAULT_TEST_STEP_LIMIT;
    private Predicate<Output> outputFilter = Predicates.alwaysTrue();
    private BranchBuilder startBranch = new BranchBuilder(START_BRANCH_NAME);

    public Builder(Language lang) {
      this.start = lang.defaultStartProdName;
      this.lang.add(lang);
    }

    public Builder alsoRunOn(Language newLang) {
      this.lang.add(newLang);
      return this;
    }

    @CheckReturnValue
    public Builder withStartProd(String name) {
      return withStartProd(new ProdName(name));
    }

    @CheckReturnValue
    public Builder withStartProd(ProdName name) {
      this.start = name;
      return this;
    }

    @CheckReturnValue
    public Builder withInput(String text, Source src) {
      startBranch.withInput(text, src);
      return this;
    }

    @CheckReturnValue
    public Builder withInput(String... texts) {
      startBranch.withInput(texts);
      return this;
    }

    @CheckReturnValue
    public Builder expectEndState(Completion endState) {
      startBranch.expectEndState(endState);
      return this;
    }

    @CheckReturnValue
    public Builder expectOutput(Output... out) {
      startBranch.expectOutput(out);
      return this;
    }

    @CheckReturnValue
    public Builder expectOutput(String... out) {
      startBranch.expectOutput(out);
      return this;
    }

    @CheckReturnValue
    public Builder expectUnparsedInput(String unparsedInput) {
      startBranch.expectUnparsedInput(unparsedInput);
      return this;
    }

    @CheckReturnValue
    public BranchBuilder fork() {
      return startBranch.fork();
    }

    @CheckReturnValue
    public BranchBuilder join(BranchBuilder... others) {
      return startBranch.join(others);
    }

    @CheckReturnValue
    public BranchBuilder join(Iterable<? extends BranchBuilder> others) {
      return startBranch.join(others);
    }

    @CheckReturnValue
    public Builder filterOutputs(Predicate<Output> filter) {
      this.outputFilter = Predicates.and(this.outputFilter, filter);
      return this;
    }

    @CheckReturnValue
    public Builder withStepLimit(int newStepLimit) {
      this.stepLimit = newStepLimit;
      return this;
    }

    @CheckReturnValue
    public GrammarTestCase build() {
      return new GrammarTestCase(
          lang.build(),
          stepLimit,
          start,
          outputFilter,
          startBranch.build());
    }

    public void run() throws UnjoinableException {
      build().run();
    }
  }


  /**
   * Used to test Parser forks and joins by building {@link Branch}es.
   */
  @SuppressWarnings("javadoc")
  public static final class BranchBuilder {
    private final String name;
    private final List<StringSourcePair> inputs = new ArrayList<>();
    private Optional<Completion> expectedEndState = Optional.absent();
    private Optional<List<Output>> expectedOutputs = Optional.absent();
    private String expectedUnparsedInput = "";
    private List<BranchBuilder> followers = new ArrayList<>();

    BranchBuilder(String name) {
      this.name = name;
    }

    public BranchBuilder withInput(String text, Source src) {
      inputs.add(new StringSourcePair(text, src));
      return this;
    }

    public BranchBuilder withInput(String... texts) {
      for (String text : texts) {
        inputs.add(
            new StringSourcePair(
                text,
                new Source("test", inputs.size())));
      }
      return this;
    }

    public BranchBuilder expectEndState(Completion newExpectedEndState) {
      this.expectedEndState = Optional.of(newExpectedEndState);
      return this;
    }

    public BranchBuilder expectOutput(Output... out) {
      if (!expectedOutputs.isPresent()) {
       expectedOutputs = Optional.<List<Output>>of(new ArrayList<Output>());
      }
      expectedOutputs.get().addAll(Arrays.asList(out));
      return this;
    }

    public BranchBuilder expectOutput(String... out) {
      Output[] outputs = new Output[out.length];
      for (int i = 0; i < out.length; ++i) {
        outputs[i] = new StringOutput(out[i], out[i]);
      }
      return expectOutput(outputs);
    }

    public BranchBuilder expectUnparsedInput(String additionalUnparsedInput) {
      this.expectedUnparsedInput += additionalUnparsedInput;
      return this;
    }

    @CheckReturnValue
    public BranchBuilder fork() {
      BranchBuilder fork = new BranchBuilder(name + "." + followers.size());
      followers.add(fork);
      return fork;
    }

    @CheckReturnValue
    public BranchBuilder join(BranchBuilder... others) {
      return join(Arrays.asList(others));
    }

    @CheckReturnValue
    public BranchBuilder join(Iterable<? extends BranchBuilder> others) {
      BranchBuilder join = new BranchBuilder(name + "." + followers.size());
      this.followers.add(join);
      for (BranchBuilder other : others) {
        other.followers.add(join);
      }
      return join;
    }

    /**
     * Produces a branch.
     */
    @CheckReturnValue
    public TestBranch build() {
      return build(new IdentityHashMap<BranchBuilder, TestBranch>());
    }

    @CheckReturnValue
    private TestBranch build(IdentityHashMap<BranchBuilder, TestBranch> im) {
      // Handle joins by building at most one branch per builder within the
      // context of a public build() call.
      if (im.containsKey(this)) { return im.get(this); }
      im.put(this, null);  // Make otherwise inf. loops error out below.

      Completion finalExpectedEndState = this.expectedEndState.isPresent()
          ? this.expectedEndState.get()
          : followers.isEmpty()
          ? Completion.PASSED
          : Completion.IN_PROGRESS;

      Optional<ImmutableList<Output>> finalExpectedOutputs = Optional.absent();
      if (this.expectedOutputs.isPresent()) {
        finalExpectedOutputs = Optional.of(
            ImmutableList.copyOf(this.expectedOutputs.get()));
      } else if (finalExpectedEndState != Completion.FAILED) {
        finalExpectedOutputs = Optional.of(ImmutableList.<Output>of());
      }

      ImmutableList.Builder<TestBranch> followersB = ImmutableList.builder();
      for (BranchBuilder follower : followers) {
        followersB.add(follower.build(im));
      }

      TestBranch b = new TestBranch(
          name,
          ImmutableList.copyOf(inputs),
          finalExpectedEndState,
          finalExpectedOutputs,
          expectedUnparsedInput,
          followersB.build()
          );

      if (im.put(this, b) != null) {
        throw new IllegalStateException();  // Not acyclic.
      }
      return b;
    }
  }

  static final String START_BRANCH_NAME = "B";
}


final class BranchTerminationAssertionWatcher implements ParseWatcher {
  private final GrammarTestCase tc;
  private final Set<GrammarTestCase.TestBranch> untested
      = new LinkedHashSet<>();
  private final Map<GrammarTestCase.TestBranch, Parse> startStates
      = new LinkedHashMap<>();

  private static void enumerateBranchesOnto(
      GrammarTestCase.TestBranch b,
      Collection<? super GrammarTestCase.TestBranch> out) {
    if (!out.contains(b)) {
      out.add(b);
      for (GrammarTestCase.TestBranch c : b.followers) {
        enumerateBranchesOnto(c, out);
      }
    }
  }

  BranchTerminationAssertionWatcher(GrammarTestCase tc) {
    this.tc = tc;
    enumerateBranchesOnto(tc.startBranch, untested);
  }

  Set<GrammarTestCase.TestBranch> getUntested() {
    return ImmutableSet.copyOf(untested);
  }

  @Override
  public void started(Parse p) {
    branchStarted(tc.startBranch, p);
  }

  @Override
  public void entered(Combinator c, Parse p) {
    // Do nothing
  }

  @Override
  public void passed(Combinator c, Parse p) {
    // Do nothing
  }

  @Override
  public void failed(Combinator c, Parse p) {
    // Do nothing
  }

  @Override
  public void paused(Combinator c, Parse p) {
    // Do nothing
  }

  @Override
  public void inputAdded(Parse p) {
    // Do nothing
  }

  @Override
  public void forked(Parse p, Branch start, Branch end) {
    branchStarted((GrammarTestCase.TestBranch) end, p);
    if (untested.contains(start)) {
      branchFinished((GrammarTestCase.TestBranch) start, p);
    }
  }

  @Override
  public void joinStarted(Branch from, Branch to) {
    // Do nothing
  }

  @Override
  public void joinFinished(Parse p, Branch from, Parse q, Branch to) {
    branchFinished((GrammarTestCase.TestBranch) from, p);
    branchStarted((GrammarTestCase.TestBranch) to, q);
  }

  @Override
  public void finished(Parse p, Branch b, Completion endState) {
    branchFinished((GrammarTestCase.TestBranch) b, p);
  }

  private void branchStarted(GrammarTestCase.TestBranch b, Parse p) {
    Assert.assertTrue(untested.contains(b));
    this.startStates.put(b, p);
  }

  private void branchFinished(GrammarTestCase.TestBranch b, Parse p) {
    Parse startState = startStates.get(b);
    Assert.assertTrue(untested.contains(b));
    Assert.assertTrue(startStates.containsKey(b));
    Assert.assertNotNull(startState);

    Completion actualEndState = Completion.of(p);
    Assert.assertEquals(
        "end state for branch " + b.inputs,
        b.expectedEndState, actualEndState);

    if (actualEndState != Completion.FAILED
        || b.expectedOutputs.isPresent()) {
      ImmutableList<Output> actual;
      {
        // Subtract out the stuff that was on the output from preceding
        // branches.
        FList<Output> initialOutput = startState.out.filterRev(NOT_EPHEMERAL);
        FList<Output> finalOutput = p.out.filterRev(NOT_EPHEMERAL);
        while (!initialOutput.isEmpty()) {
          Assert.assertFalse(
              "Final output does not contain all of initial branch output",
              finalOutput.isEmpty());
          Output initialHd = initialOutput.hd();
          Output finalHd = finalOutput.hd();
          if (initialHd.equals(finalHd)) {
            finalOutput = finalOutput.tl();
          } else if (
              initialHd instanceof StringOutput
              && finalHd instanceof StringOutput) {
            StringOutput initialSo = (StringOutput) initialHd;
            StringOutput finalSo = (StringOutput) finalHd;
            if (!finalSo.s.startsWith(initialSo.s)) {
              break;
            }
            finalOutput = FList.cons(
                new StringOutput(
                    finalSo.s.substring(initialSo.s.length()),
                    finalSo.rawChars.substring(initialSo.rawChars.length())
                    ),
                finalOutput.tl());
          } else {
            break;
          }
          initialOutput = initialOutput.tl();
        }
        Assert.assertTrue(
            "initialOutput " + initialOutput + " should be a prefix of "
            + finalOutput,
            initialOutput.isEmpty());

        // Apply the output filter being careful to coalesce properly so
        // that tests are not brittle.
        List<Output> filtered = new ArrayList<>();
        Output last = null;
        for (Output o : finalOutput) {
          if (!tc.outputFilter.apply(o)) { continue; }
          Optional<Output> combined = last != null
              ? last.coalesceWithFollower(o)
              : Optional.<Output>absent();
          if (combined.isPresent()) {
            last = combined.get();
          } else {
            if (last != null) {
              filtered.add(last);
            }
            last = o;
          }
        }
        if (last != null) { filtered.add(last); }
        actual = ImmutableList.copyOf(filtered);
      }

      assertEqualsWithDiff("outputs", b.expectedOutputs.get(), actual);
    }

    if (actualEndState == Completion.PASSED
        || !b.expectedUnparsedInput.isEmpty()) {
      Assert.assertEquals(
          "unparsed input",
          b.expectedUnparsedInput,
          p.inp.getAvailable().toString());

      Assert.assertTrue(
          "complete",
          p.inp.isComplete());
    }

    untested.remove(b);
  }

  static final Predicate<Output> NOT_EPHEMERAL =
      new Predicate<Output>() {
        @Override
        public boolean apply(Output o) {
          return !(o instanceof EphemeralOutput);
        }
      };

  private static void assertEqualsWithDiff(
      String message,
      Iterable<? extends Visualizable> expected,
      Iterable<? extends Visualizable> actual) {
    ImmutableList<Visualizable> a = ImmutableList.copyOf(expected);
    ImmutableList<Visualizable> b = ImmutableList.copyOf(actual);
    if (!a.equals(b)) {
      String expectedStr = onePerLine(a);
      String actualStr = onePerLine(b);
      Assert.assertEquals(message, expectedStr, actualStr);
      Assert.assertTrue(message, false);
    }
  }

  private static String onePerLine(ImmutableList<Visualizable> vs) {
    StringBuilder sb = new StringBuilder();
    TextVizOutput o = new TextVizOutput(sb);
    try {
      for (Visualizable v : vs) {
        v.visualize(DetailLevel.LONG, o);
        o.text("\n");
      }
    } catch (IOException ex) {
      Throwables.propagate(ex);
    }
    return sb.toString();
  }
}
