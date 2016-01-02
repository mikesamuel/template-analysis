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
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.Success;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.out.LookaheadMarker;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.Visualizables;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A positive lookahead that passes when d passes.
 * <p>
 * Regardless of whether it passes, it does not modify (or allow d to modify)
 * the output or consume any input.
 */
public final class LookaheadCombinator extends UnaryCombinator {
  /** True iff this is a positive lookahead. */
  public final boolean positive;

  /** */
  public LookaheadCombinator(
      Supplier<NodeMetadata> mds, Combinator body, boolean positive) {
    super(mds, body);
    this.positive = positive;
  }

  @Override
  protected LookaheadCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      Combinator newBody) {
    if (body == newBody && newMetadata.equals(md)) {
      return this;
    }
    return new LookaheadCombinator(
        Suppliers.ofInstance(newMetadata), newBody, positive);
  }


  @Override
  public ParseDelta enter(Parse p) {
    // Store the state before we enter so we can return later.
    return ParseDelta
        .builder(body)
        .withOutput(LookaheadMarker.INSTANCE)
        .push()
        .build();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    ParseDelta.Builder b = null;
    switch (positive ? s : s.inverse()) {
      case FAIL:
        b = ParseDelta.fail();
        break;
      case PASS:
        b = ParseDelta.pass();
        break;
    }
    return Preconditions.checkNotNull(b)
        .withIOTransform(LookaheadMarker.INSTANCE.rollback())
        .build();
  }

  @Override
  public final ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    ParseDelta.Builder result = null;
    switch (tt) {
      case ENTER:
        return ImmutableList.of(
            ParseDelta.builder(body)
            .push()
            .withOutput(LookaheadMarker.INSTANCE)
            .build());
      case EXIT_PASS:
        result = positive ? ParseDelta.pass() : ParseDelta.fail();
        break;
      case EXIT_FAIL:
        result = positive ? ParseDelta.fail() : ParseDelta.pass();
        break;
    }
    return ImmutableList.of(
        Preconditions.checkNotNull(result)
        .withIOTransform(LookaheadMarker.INSTANCE.rollback())
        .build());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LookaheadCombinator)) { return false; }
    LookaheadCombinator that = (LookaheadCombinator) o;
    return this.positive == that.positive
        && this.body.equals(that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(positive, body);
  }

  @Override
  protected String getVizTypeClassName() {
    return positive ? "lookahead" : "neg-lookahead";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    if (!positive && body instanceof CharSetCombinator) {
      CharSetCombinator csd = (CharSetCombinator) body;
      if (csd.codePoints.equals(UniRanges.ALL_CODEPOINTS)) {
        // Syntactic re-sugaring
        AbstractVisualizable.visualize(
            csd, csd.getVizTypeClassName() + " end-of-input", lvl, out,
            Visualizables.text("$"));
        return;
      }
    }
    out.text(positive ? "(?= " : "(?! ");
    body.visualize(lvl, out);
    out.text(")");
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return Frequency.NEVER;
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return ImmutableRangeSet.of();
  }
}
