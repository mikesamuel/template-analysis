package com.google.template.autoesc.combimpl;

import java.io.Closeable;
import java.io.IOException;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Success;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.Precedence;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.out.LoopMarker;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Kleene plus which matches body one or more times.
 * <p>
 * This passes if body passes at least once.
 * It terminates the first time body fails or the first time body passes but
 * without consuming any input, whichever happens first.
 */
public final class LoopCombinator extends UnaryCombinator {

  /** */
  public LoopCombinator(Supplier<NodeMetadata> mds, Combinator body) {
    this(mds, new OrCombinator(mds, body, EmptyCombinator.INSTANCE), null);
  }

  /**
   * @param v serves to differentiate the static signature of this constructor
   *     which does not wrap its argument in an option from the one that does.
   */
  private LoopCombinator(
      Supplier<NodeMetadata> mds, OrCombinator optionalBody, Void v) {
    super(mds, optionalBody);
    Preconditions.checkArgument(
        optionalBody.second == EmptyCombinator.INSTANCE);
  }

  @Override
  protected LoopCombinator unfold(
      NodeMetadata newMetadata,
      Function<ProdName, ProdName> renamer,
      Combinator newBody) {
    if (newBody == this.body && newMetadata.equals(this.md)) {
      return this;
    }
    if (newBody instanceof OrCombinator
        && EmptyCombinator.INSTANCE == ((OrCombinator) newBody).second) {
      return new LoopCombinator(
          Suppliers.ofInstance(newMetadata),
          (OrCombinator) newBody,
          (Void) null);
    } else {
      throw new IllegalArgumentException(newBody.toString());
    }
  }

  /**
   * The body of this loop which must appear at least once.
   */
  public Combinator getLoopBody() {
    return getOptionalBody().first;
  }

  /**
   * An option containing the {@linkplain #getLoopBody body} which is used in
   * the second and subsequent iterations.
   */
  public OrCombinator getOptionalBody() {
    return (OrCombinator) body;
  }

  @Override
  public ParseDelta enter(Parse p) {
    return enter();
  }

  private ParseDelta enter() {
    return ParseDelta.builder(getLoopBody())
        .push()
        .withOutput(LoopMarker.INSTANCE)
        .build();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    switch (s) {
      case FAIL:
        // Look for the loop start marker and remove it
        return exitFail();
      case PASS:
        boolean didConsumeInput = false;
        for (Output o : p.out) {
          if (LoopMarker.INSTANCE.equals(o)) { break; }
          if (o instanceof StringOutput
              && ((StringOutput) o).rawChars.length() != 0) {
            didConsumeInput = true;
            break;
          }
        }
        if (didConsumeInput) {
          return exitContinue();
        } else {
          // The loop body succeeded but did nothing.
          // An infinite number of matches of an empty string is the same as
          // one match of an empty string, so stop.
          return exitBreak();
        }
    }
    throw new AssertionError(s);
  }

  private static ParseDelta exitFail() {
    return ParseDelta.fail()
        .withIOTransform(LoopMarker.INSTANCE.rollback())
        .build();
  }

  private ParseDelta exitContinue() {
    // Push the body back and wrap it in an optional check so that a
    // failure doesn't cause the loop as a whole to fail.
    // This works because
    //   x+
    // is equivalent to
    //   x x*
    // The OrCombinator also cleans up after the last, failing iteration.
    // instance.
    return ParseDelta
        .builder(getOptionalBody())
        .push()
        .commit(LoopMarker.INSTANCE)
        .withOutput(LoopMarker.INSTANCE)
        .build();
  }

  private static ParseDelta exitBreak() {
    return ParseDelta.pass()
        .commit(LoopMarker.INSTANCE)
        .build();
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(enter());
      case EXIT_FAIL:
        return ImmutableList.of(exitFail());
      case EXIT_PASS:
        return ImmutableList.of(
            // Try re-entering to see if we catch up with a branch that's
            // already in the loop.
            exitContinue(),
            // If the last run of the body consumed nothing.
            exitBreak());
    }
    throw new AssertionError(tt);
  }


  @Override
  protected String getVizTypeClassName() {
    return "loop";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    Combinator loopBody = getLoopBody();

    boolean parenthesize = false;
    if (0 < Precedence.SELF_CONTAINED.compareTo(loopBody.precedence())) {
      parenthesize = true;
    }

    if (parenthesize) { out.text("("); }
    loopBody.visualize(lvl, out);
    if (parenthesize) { out.text(")"); }
    try (Closeable sup = out.open(TagName.SUP)) {
      out.text("+");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LoopCombinator)) { return false; }
    LoopCombinator that = (LoopCombinator) o;
    return this.body.equals(that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(body, getClass());
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return lang.lali.consumesInput(getLoopBody());
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return lang.lali.lookahead(getLoopBody());
  }


  /**
   * Can be applied at commit to tell whether a loop run made progress.
   * This check allows us to only re-enter a loop when the last iteration did
   * something.
   */
  static final class DidConsumeInputChecker implements Function<Output, Void> {
    private boolean didConsumeOutput;

    /** True if called with a non-empty string output. */
    public boolean didConsumeOutput() {
      return didConsumeOutput;
    }

    @Override
    public Void apply(Output o) {
      if (o instanceof StringOutput && ((StringOutput) o).s.length() != 0) {
        didConsumeOutput = true;
      }
      return null;
    }
  }
}
