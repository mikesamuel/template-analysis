package com.google.template.autoesc.combimpl;

import java.io.IOException;

import javax.annotation.Nullable;

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
 * Matches the empty string which is a prefix of every input.
 */
public final class EmptyCombinator extends SingletonCombinator {
  /** Singleton */
  public static final EmptyCombinator INSTANCE = new EmptyCombinator();

  private EmptyCombinator() {
  }

  @Override
  public ParseDelta enter(Parse p) {
    return ParseDelta.pass().build();
  }

  @Override
  public final ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(ParseDelta.pass().build());
      case EXIT_FAIL: case EXIT_PASS:
        break;
    }
    throw new AssertionError(tt);
  }

  @Override
  public boolean equals(@Nullable Object that) {
    return that instanceof EmptyCombinator;
  }

  @Override
  public int hashCode() {
    return EmptyCombinator.class.hashCode();
  }

  @Override
  protected String getVizTypeClassName() {
    return "empty";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("\"\"");
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
