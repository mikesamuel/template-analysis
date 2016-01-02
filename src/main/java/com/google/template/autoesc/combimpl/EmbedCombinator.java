package com.google.template.autoesc.combimpl;

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
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.inp.DecodingInputCursor;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.StringTransform;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.out.EmbedOutput;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * An embedded region that can be decoded to a string that should match an
 * embedded grammar.
 */
public final class EmbedCombinator extends UnaryCombinator {
  /** Used to decode the input to body. */
  public final StringTransform xform;

  private final Function<InputCursor, InputCursor> wrap =
      new Function<InputCursor, InputCursor>() {
        @Override
        public InputCursor apply(InputCursor input) {
          if (input == null) { throw new IllegalArgumentException(); }
          return new DecodingInputCursor(input, xform);
        }

        @Override
        public String toString() {
          return "decode(" + xform + ")";
        }
      };

  /** */
  public EmbedCombinator(
      Supplier<NodeMetadata> mds, Combinator body, StringTransform xform) {
    super(mds, body);
    this.xform = xform;
  }


  @Override
  protected Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      Combinator newBody) {
    if (newMetadata.equals(md) && newBody == body) {
      return this;
    }
    return new EmbedCombinator(
        Suppliers.ofInstance(newMetadata), newBody, xform);
  }

  @Override
  public ParseDelta enter(Parse p) {
    return ParseDelta.builder(body)
        .push()
        .withInputTransform(wrap)
        .withOutput(new EmbedOutput(Side.LEFT, xform))
        .build();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    switch (s) {
      case PASS:
        return ParseDelta.pass()
            .withInputTransform(UnembedFunction.INSTANCE)
            .withOutput(new EmbedOutput(Side.RIGHT, xform))
            .build();
      case FAIL:
        return ParseDelta.fail()
            .withInputTransform(UnembedFunction.INSTANCE)
            .build();
    }
    throw new AssertionError(s);
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    ParseDelta.Builder result = null;
    switch (tt) {
      case ENTER:
        result = ParseDelta.builder(body).push().withInputTransform(wrap);
        break;
      case EXIT_FAIL:
        result = ParseDelta.fail().withInputTransform(UnembedFunction.INSTANCE);
        break;
      case EXIT_PASS:
        result = ParseDelta.pass().withInputTransform(UnembedFunction.INSTANCE);
        break;
    }
    return ImmutableList.of(Preconditions.checkNotNull(result).build());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof EmbedCombinator)) {
      return false;
    }
    EmbedCombinator that = (EmbedCombinator) o;
    return this.body.equals(that.body)
        && this.xform.equals(that.xform);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(body, xform);
  }

  @Override
  protected String getVizTypeClassName() {
    return "embed";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("(embed ");
    xform.visualize(lvl, out);
    out.text(":");
    body.visualize(lvl, out);
    out.text(")");
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return Frequency.SOMETIMES;
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return UniRanges.ALL_CODEPOINTS;
  }
}


final class UnembedFunction implements Function<InputCursor, InputCursor> {
  static final UnembedFunction INSTANCE = new UnembedFunction();

  private UnembedFunction() {}

  @Override
  public InputCursor apply(InputCursor input) {
    // Should only receive the output of wrap above.
    if (!(input instanceof DecodingInputCursor)) {
      throw new IllegalArgumentException();
    }
    return ((DecodingInputCursor) input).getUndecoded();
  }
  @Override
  public String toString() {
    return "unwrap";
  }
}
