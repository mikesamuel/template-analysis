package com.google.template.autoesc.combimpl;

import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Matches no strings.
 */
public final class ErrorCombinator extends SingletonCombinator {
  /** Singleton. */
  public static final ErrorCombinator INSTANCE = new ErrorCombinator();

  private ErrorCombinator() {
  }

  @Override
  public ParseDelta enter(Parse p) {
    return ParseDelta.fail().build();
  }

  @Override
  public final ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(ParseDelta.fail().build());
      case EXIT_FAIL: case EXIT_PASS:
        break;
    }
    throw new AssertionError(tt);
  }

  @Override
  public boolean equals(Object that) {
    return that instanceof ErrorCombinator;
  }

  @Override
  public int hashCode() {
    return ErrorCombinator.class.hashCode();
  }

  @Override
  protected String getVizTypeClassName() {
    return "error";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("[]");
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
