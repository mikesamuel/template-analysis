package com.google.template.autoesc.var;

import java.io.IOException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.template.autoesc.out.BinaryOutput;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;

/**
 * An output marker that introduces a scope.
 * Since languages can embed themselves directly or indirectly, scoping allows
 * a grammar to make sure that it is examining the value at the same level
 * of nesting.
 */
public final class Scope extends BinaryOutput implements VariableOutput {
  /** The associated variable. */
  public final Variable<?> var;

  /** */
  public Scope(Side side, Variable<?> var) {
    super(side);
    this.var = var;
  }

  @Override
  public Optional<Output> coalesceWithFollower(Output next) {
    return Optional.absent();
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    if (side == Side.LEFT) {
      out.text("var ");
      var.visualize(lvl, out);
    } else {
      out.text("/var");
    }
  }

  @Override
  protected String getVizTypeClassName() {
    return "scope";
  }

  @Override
  public Variable<?> getVariable() {
    return var;
  }

  @SuppressFBWarnings(
      value="BC_UNCONFIRMED_CAST",
      justification="getClass() == that.getClass() -> that instanceof C")
  @Override
  public boolean isOtherSide(BinaryOutput b) {
    return side != b.side && getClass() == b.getClass()
        && var.equals(((Scope) b).var);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Scope)) { return false; }
    Scope that = (Scope) o;
    return this.var.equals(that.var) && this.side == that.side;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(side, var);
  }
}
