package com.google.template.autoesc.out;

import javax.annotation.Nullable;

import com.google.template.autoesc.viz.AbstractVisualizable;

/**
 * An output that is a side of a continuous region.
 */
public abstract class BinaryOutput
extends AbstractVisualizable implements Output {
  /**
   * The start of the region is the left side, and the end of the region is
   * the right side.
   */
  public final Side side;

  /** */
  public BinaryOutput(Side side) {
    this.side = side;
  }

  @Override
  protected String getExtraVizTypeClasses() {
    return side.name();
  }

  /** True iff b is the same in every detail but has the opposite side. */
  public abstract boolean isOtherSide(BinaryOutput b);

  @Override
  public abstract boolean equals(@Nullable Object o);
  @Override
  public abstract int hashCode();


  /** By default, only relevant if still open. */
  @Override
  public boolean isParseRelevant(PartialOutput po) {
    // Only relevant if open.
    assert (po.getOutput().get() == this);
    return !((PartialOutput.BoundedRegion) po).right.isPresent();
  }
}