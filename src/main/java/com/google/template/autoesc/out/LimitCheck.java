package com.google.template.autoesc.out;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Marks a range that must not start a substring matched by the limit pattern.
 * @see com.google.template.autoesc.Combinators#until
 */
public final class LimitCheck extends BinaryOutput {
  /** The limit pattern. */
  public final Combinator limit;

  /** */
  public LimitCheck(Side side, Combinator limit) {
    super(side);
    this.limit = limit;
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    try (Closeable code = out.open(TagName.CODE)) {
      switch (side) {
        case LEFT:  out.text("{until "); break;
        case RIGHT: out.text("{/until "); break;
      }
      limit.visualize(lvl, out);
      out.text("}");
    }
  }

  @Override
  protected String getVizTypeClassName() {
    return "limit-check";
  }

  @Override
  public Optional<Output> coalesceWithFollower(Output next) {
    return Optional.absent();
  }

  @Override
  public boolean isOtherSide(BinaryOutput b) {
    return side != b.side && getClass() == b.getClass()
        && limit.equals(((LimitCheck) b).limit);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof LimitCheck)) { return false; }
    LimitCheck that = (LimitCheck) o;
    return this.side == that.side && this.limit.equals(that.limit);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(side, limit);
  }
}
