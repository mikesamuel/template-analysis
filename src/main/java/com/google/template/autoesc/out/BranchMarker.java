package com.google.template.autoesc.out;

import java.io.IOException;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * An ephemeral marker that marks the start of a branch that might have to
 * be rolled back.
 */
public final class BranchMarker extends EphemeralOutput {
  private BranchMarker() {}

  /** Singleton. */
  public static final BranchMarker INSTANCE = new BranchMarker();

  @Override
  public boolean equals(@Nullable Object o) {
    return o != null && o.getClass() == getClass();
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  protected String getVizTypeClassName() {
    return "branch-marker";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("{/}");
  }

  @Override
  protected int compareToSameClass(UnaryOutput o) {
    Preconditions.checkState(this == o);
    return 0;
  }
}
