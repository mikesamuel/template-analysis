package com.google.template.autoesc;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.RawCharsInputCursor;
import com.google.template.autoesc.out.BinaryOutput;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;


/**
 * The state of a parse.
 */
public final class Parse implements OutputContext {
  /** Definitions used to resolve references. */
  public final Language lang;
  /**
   * The input being parsed.  If not {@link InputCursor#isComplete complete}
   * then the parse state may be {@linkplain InputCursor#extend extended} with
   * more input.
   */
  public final InputCursor inp;
  /**
   * The result of the parse.
   * <p>
   * Instead of performing side-effects, like attaching nodes to a parse
   * tree, {@link Combinator}s
   * {@linkplain ParseDelta.Builder#withOutput append} events to the output
   * list.
   * <p>
   * If a parse branch fails, the list can be truncated, and if the parse
   * succeeds then the output is a consistent view that can be replayed to
   * effect only those side-effects implied by passing grammar nodes.
   * <p>
   * Since out is built on a left-to-right parse, the output chunks derived
   * from a piece of input on the left is farther from the head of the list than
   * those derived from pieces of input farther to the right.
   */
  public final FList<Output> out;
  /**
   * A path through the grammar that includes all nodes that have been entered
   * and not subsequently exited.
   * <p>
   * A recursive descent parser uses the function call stack to preserve
   * equivalent state.  This stack reifies that state so that the parser can be
   * paused and resumed.
   * <p>
   * This path specifies a language that is a subset of the language
   * specified by the grammar but which includes every string which has
   * the parsed portion of the input as a prefix.
   */
  public final FList<Combinator> stack;


  private Parse(
      Language lang, InputCursor inp, FList<Output> out,
      FList<Combinator> stack) {
    this.lang = lang;
    this.inp = inp;
    this.out = out;
    this.stack = stack;
  }

  /**
   * One iteration of the parsing algorithm.
   */
  @CheckReturnValue
  public Parse smallStep(ParseWatcher watcher) {
    if (stack.isEmpty()) {
      throw new IllegalStateException();
    }
    Combinator hd = stack.hd();
    FList<Combinator> tl = stack.tl();
    Parse tlParse = builder().withStack(tl).build();
    ParseDelta result;

    // The combinator being operated upon.
    Combinator c;
    // The base of the stack -- the suffix onto which result.push would cause
    // d to be pushed.
    FList<Combinator> base;
    // Figure out the kind of change -- whether to expand or reduce the stack.
    if (hd instanceof EmptyCombinator) {
      if (tl.isEmpty()) { return this; }  // completion state is PASS
      c = tl.hd();
      base = tl.tl();
      watcher.passed(c, tlParse);
      result = c.exit(tlParse, Success.PASS);
    } else if (hd instanceof ErrorCombinator) {
      if (tl.isEmpty()) { return this; }  // completion state is FAIL
      c = tl.hd();
      base = tl.tl();
      watcher.failed(c, tlParse);
      result = c.exit(tlParse, Success.FAIL);
    } else {
      c = hd;
      base = tl;
      watcher.entered(c, tlParse);
      result = c.enter(tlParse);
    }

    // Apply the result.
    FList<Combinator> resultStack = base;
    if (result.push) {
      resultStack = FList.cons(c, resultStack);
    }
    resultStack = FList.cons(result.c, resultStack);

    ParseDelta.IOTransform ioTransform = result.ioTransform;
    ioTransform.apply(inp, out);

    Parse afterStep = builder()
        .withStack(resultStack)
        .withInput(ioTransform.getTransformedCursor())
        .withOutput(ioTransform.getTransformedOutput())
        .build();

    return afterStep;
  }

  /** A builder that initially has the same state as this. */
  @CheckReturnValue
  public Builder builder() {
    return new Builder(this);
  }

  /**
   * A builder that produces a parse state that resolves references in lang.
   * It is initialized to the empty stack, an empty input, and empty output.
   */
  @CheckReturnValue
  public static Builder builder(Language lang) {
    return new Builder(lang);
  }

  /**
   * True if this is an error state.
   */
  public boolean isError() {
    return !stack.isEmpty() && stack.tl().isEmpty()
        && stack.hd() instanceof ErrorCombinator;
  }

  /**
   * Replaces the stack so
   * @return an x that x.{@link #isError isError} but that all else is as
   *     similar to this as possible.
   */
  public Parse toError() {
    return builder()
        .withStack(FList.cons(
            ErrorCombinator.INSTANCE, FList.<Combinator>empty()))
        .build();
  }

  /**
   * True if pausing might be appropriate.
   */
  public boolean couldPause() {
    return Pause.couldPause(inp, stack);
  }

  /**
   * True if the parse state is paused waiting for input.
   * @see #smallStep
   * @see com.google.template.autoesc.combimpl.AbstractCombinator#pause
   */
  public boolean isPaused() {
    return !stack.isEmpty() && Pause.isPaused(stack.hd());
  }

  /**
   * The current stack, but {@linkplain Pause#pause paused}.
   */
  @CheckReturnValue
  public Parse pause() {
    return builder()
        .withStack(FList.cons(Pause.pause(stack.hd()), stack.tl()))
        .build();
  }

  /**
   * The current stack, but {@linkplain Pause#resume resumed}.
   */
  @CheckReturnValue
  public Parse resume() {
    return builder()
        .withStack(FList.cons(Pause.resume(stack.hd()), stack.tl()))
        .build();
  }

  @Override
  public Optional<Output> lastInScope(
      Predicate<? super BinaryOutput> isScope,
      Predicate<? super Output> matches) {
    return new OutputContext.ListOutputContext(out)
        .lastInScope(isScope, matches);
  }

  /** Structural equality. */
  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof Parse)) { return false; }
    Parse that = (Parse) o;
    return this.inp.equals(that.inp)
        && this.out.equals(that.out)
        && this.stack.equals(that.stack);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(inp, out, stack);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    boolean needsComma = false;
    boolean writeInput = !inp.equals(RawCharsInputCursor.EMPTY);
    boolean writeStack = !stack.isEmpty();
    boolean writeOutput = !out.isEmpty();
    boolean writeOnlyStack = writeStack && !writeInput && !writeOutput;
    sb.append("{");
    if (writeInput) {
      sb.append("inp=")
          .append(TextVizOutput.vizToString(inp, DetailLevel.SHORT));
      needsComma = true;
    }
    if (writeStack) {
      if (needsComma) { sb.append(", "); }
      if (!writeOnlyStack) {
        sb.append("stack=");
      }
      sb.append(stack);
      needsComma = true;
    }
    if (writeOutput) {
      if (needsComma) { sb.append(", "); }
      sb.append("out=");
      sb.append(out);
      needsComma = true;
    }
    sb.append('}');
    return sb.toString();
  }


  /**
   * The output list with hd as the most recent output preceded by tl.
   * <p>
   * This {@linkplain Output#coalesceWithFollower coalesces} where possible.
   */
  public static FList<Output> consOutput(Output hd, FList<Output> tl) {
    if (!tl.isEmpty()) {
      Output chd = tl.hd();
      Optional<Output> coalesced = chd.coalesceWithFollower(hd);
      if (coalesced.isPresent()) {
        return FList.cons(coalesced.get(), tl.tl());
      }
    }
    return FList.cons(hd, tl);
  }


  /** Mutable builder for immutable {@link Parse}. */
  public static final class Builder {
    private Language lang;
    private InputCursor inp;
    private FList<Output> out;
    private FList<Combinator> stack;

    Builder(Parse p) {
      this.lang = p.lang;
      this.inp = p.inp;
      this.out = p.out;
      this.stack = p.stack;
    }

    Builder(Language lang) {
      this.lang = lang;
      this.inp = RawCharsInputCursor.EMPTY;
      this.out = FList.empty();
      this.stack = FList.empty();
    }

    /** Setter for the eventual {Parse#inp} */
    @CheckReturnValue
    public Builder withInput(InputCursor newInp) {
      this.inp = Preconditions.checkNotNull(newInp);
      return this;
    }

    /** Setter for the eventual {Parse#out} */
    @CheckReturnValue
    public Builder withOutput(FList<Output> newOut) {
      this.out = Preconditions.checkNotNull(newOut);
      return this;
    }

    /** Conses o onto {Parse#out}. */
    @CheckReturnValue
    public Builder addOutput(Output o) {
      return withOutput(consOutput(o, out));
    }

    /** Setter for the eventual {Parse#stack} */
    @CheckReturnValue
    public Builder withStack(FList<Combinator> newStack) {
      this.stack = Preconditions.checkNotNull(newStack);
      return this;
    }

    /** Pushes newHd onto the stack. */
    @CheckReturnValue
    public Builder push(Combinator newHd) {
      return withStack(FList.cons(newHd, stack));
    }

    /** Builds the immutable output. */
    @SuppressWarnings("synthetic-access")
    public Parse build() {
      return new Parse(lang, inp, out, stack);
    }
  }
}
