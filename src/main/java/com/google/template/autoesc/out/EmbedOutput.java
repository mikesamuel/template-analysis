package com.google.template.autoesc.out;

import java.io.IOException;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.template.autoesc.inp.StringTransform;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Marks the boundaries of an
 * {@linkplain com.google.template.autoesc.combimpl.EmbedCombinator embedded}
 * region.
 * String chunks within the region may have different
 * {@link StringOutput#rawChars} than the decoded {@link StringOutput#s}.
 */
public final class EmbedOutput extends BinaryOutput {
  /** The transform used to decode the input to the embedded grammar. */
  public final StringTransform xform;

  /** */
  public EmbedOutput(Side side, StringTransform xform) {
    super(side);
    this.xform = xform;
  }
  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    xform.visualize(lvl, out);
  }

  @Override
  protected String getVizTypeClassName() {
    return "embed-output";
  }

  @Override
  public Optional<Output> coalesceWithFollower(Output next) {
    return Optional.absent();
  }

  @Override
  public boolean isOtherSide(BinaryOutput b) {
    return side != b.side && getClass() == b.getClass()
        && xform.equals(((EmbedOutput) b).xform);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof EmbedOutput)) { return false; }
    EmbedOutput that = (EmbedOutput) o;
    return this.side == that.side && this.xform.equals(that.xform);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(side, xform);
  }
}
