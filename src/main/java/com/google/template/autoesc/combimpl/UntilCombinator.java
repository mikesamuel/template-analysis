package com.google.template.autoesc.combimpl;

import java.io.IOException;

import javax.annotation.Nullable;

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
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.LimitedInputCursor;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.out.LimitCheck;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A combinator that identifies a prefix of the input until limitRecognizer
 * exposes body to only that prefix, and passes when body matches all of that
 * input.
 * <p>
 * The region matched by limitRecognizer is not consumed, and if there is
 * no match before end of input, body will be exposed to the entire input.
 * Use {@link com.google.template.autoesc.Combinators#seq} to match the limit if
 * it is required.
 * <p>
 * When until combinators pass, they also append {@link LimitCheck}s so that
 * the caller can double-check that the eventual output does not match the
 * limitRecognizer earlier than it was during parse.
 * <br>
 * For example, if the output is
 * <pre>[{limit /foo/}, "x", {pause}, "o", {/limit /foo/}]</pre>
 * and the pause were filled with {@code "fo"} then the limit pattern
 * {@code /foo/} would match earlier so would violate assumptions made by
 * the parser.
 */
public final class UntilCombinator extends AbstractCombinator {
  /** Matched the portion of the input before where the limit matches. */
  public final Combinator body;
  /** Matches the limit. */
  public final Combinator limit;

  private Function<InputCursor, InputCursor> wrapper(final Language lang) {
    return new Function<InputCursor, InputCursor>() {
      @Override
      public InputCursor apply(InputCursor input) {
        return new LimitedInputCursor(input, limit, lang);
      }

      @Override
      public String toString() {
        return "limit(" + limit + ")";
      }
    };
  }

  private static final UnwrapFunction UNWRAP = new UnwrapFunction();

  /** */
  public UntilCombinator(
      Supplier<NodeMetadata> mds, Combinator body, Combinator limit) {
    super(mds);
    this.body = body;
    this.limit = limit;
  }


  @Override
  public ImmutableList<Combinator> children() {
    return ImmutableList.of(body, limit);
  }

  @Override
  public ParseDelta enter(Parse p) {
    return ParseDelta.builder(body)
        .push()
        .withInputTransform(wrapper(p.lang))
        .withOutput(new LimitCheck(Side.LEFT, limit))
        .build();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    Success sp = s;
    if (p.inp.getAvailable().length() != 0) {
      // The body must consume the whole limited input.
      sp = Success.FAIL;
    }
    switch (sp) {
      case FAIL:
        return ParseDelta.fail().withInputTransform(UNWRAP).build();
      case PASS:
        return ParseDelta.pass()
            .withInputTransform(UNWRAP)
            // Make sure we can enforce the limit against content immediately
            // after.
            .withOutput(new LimitCheck(Side.RIGHT, limit))
            .build();
    }
    throw new AssertionError(s);
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case EXIT_FAIL:
        return ImmutableList.of(
            ParseDelta.fail().withInputTransform(UNWRAP).build());
      case EXIT_PASS:
        // TODO: Assert that the limit check passes, possibly by entering
        // a sequence that has a positive lookahead of the limit if the
        // input did not actually find the limit.
        return ImmutableList.of(
            ParseDelta.pass()
            .withInputTransform(UNWRAP)
            .withOutput(new LimitCheck(Side.RIGHT, limit))
            .build());
      case ENTER:
        return ImmutableList.of(
            ParseDelta.builder(body)
            .push()
            .withInputTransform(wrapper(lang))
            .withOutput(new LimitCheck(Side.LEFT, limit))
            .build());
    }
    throw new AssertionError(tt);
  }


  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof UntilCombinator)) { return false; }
    UntilCombinator that = (UntilCombinator) o;
    return this.body.equals(that.body) && this.limit.equals(that.limit);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(body, limit);
  }

  @Override
  protected String getVizTypeClassName() {
    return "until";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("(");
    body.visualize(lvl, out);
    out.text(":until(");
    limit.visualize(lvl, out);
    out.text("))");
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return Frequency.SOMETIMES;
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return UniRanges.ALL_CODEPOINTS;
  }

  @Override
  public Precedence precedence() {
    return Precedence.SELF_CONTAINED;
  }

  @Override
  public UntilCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      ImmutableList<Combinator> newChildren) {
    Preconditions.checkState(newChildren.size() == 2);
    Combinator newBody = newChildren.get(0);
    Combinator newLimit = newChildren.get(1);
    if (newMetadata.equals(this.md)
        && this.body == newBody && this.limit == newLimit) {
      return this;
    }
    return new UntilCombinator(
        Suppliers.ofInstance(newMetadata), newBody, newLimit);
  }
}


final class UnwrapFunction implements Function<InputCursor, InputCursor> {
  @Override
  public InputCursor apply(InputCursor input) {
    return ((LimitedInputCursor) input).getUnlimited();
  }
  @Override
  public String toString() {
    return "unwrap";
  }
}
