package com.google.template.autoesc.out;

import java.io.IOException;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;

/**
 * A marker for a side of a region that is annotated with the name of the
 * production matched.
 */
public final class Boundary extends BinaryOutput {
  /** A non-terminal name. */
  public final ProdName prodName;

  /** */
  public Boundary(Side side, ProdName prodName) {
    super(side);
    this.prodName = prodName;
  }

  @Override
  public Optional<Output> coalesceWithFollower(Output next) {
    return Optional.absent();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof Boundary)) { return false; }
    Boundary that = (Boundary) o;
    return this.side == that.side && this.prodName.equals(that.prodName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(side, prodName);
  }

  @Override
  protected String getVizTypeClassName() {
    return "tkn";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text(side == Side.LEFT ? prodName.text : "/" + prodName.text);
  }

  @Override
  public boolean isOtherSide(BinaryOutput b) {
    return side != b.side
        && getClass() == b.getClass()
        && prodName.equals(((Boundary) b).prodName);
  }
}