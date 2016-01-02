package com.google.template.autoesc.out;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Marks the beginning of a loop body so that the loop combinator can early out
 * of loop runs that consume no input.
 */
public final class LoopMarker extends EphemeralOutput {
  /** Singleton */
  public static final LoopMarker INSTANCE = new LoopMarker();

  private LoopMarker() {}

  @Override
  public boolean equals(Object o) {
    return o != null && o.getClass() == getClass();
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  protected String getVizTypeClassName() {
    return "loop-marker";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("{+}");
  }

  @Override
  protected int compareToSameClass(UnaryOutput o) {
    Preconditions.checkState(this == o);
    return 0;
  }
}
