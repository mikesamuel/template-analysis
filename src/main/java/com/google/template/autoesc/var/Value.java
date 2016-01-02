package com.google.template.autoesc.var;

import java.io.IOException;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.out.BinaryOutput;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.out.PartialOutput;
import com.google.template.autoesc.out.UnaryOutput;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.Visualizable;
import com.google.template.autoesc.viz.VizOutput;

/**
 * Indicates that a variable has a specific value within the smallest
 * enclosing scope for that variable.
 */
public final class Value<T> extends UnaryOutput implements VariableOutput {
  /** The associated variable. */
  public final Variable<T> var;
  /** The value. */
  public final T val;

  /** */
  public Value(Variable<T> var, T val) {
    this.var = var;
    this.val = val;
  }

  @Override
  public Optional<Output> coalesceWithFollower(Output next) {
    if (next instanceof Value<?>) {
      Value<?> that = (Value<?>) next;
      if (this.var.equals(that.var)) {
        return Optional.of(next);
      }
    }
    return Optional.absent();
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    var.visualize(DetailLevel.TINY, out);  // TINY -> no type.
    out.text(" = ");
    visualizeValue(val, lvl, out);
  }

  /**
   * Utility for visualizing a value.
   */
  public static void visualizeValue(
      Object value, DetailLevel lvl, VizOutput out)
  throws IOException {
    if (value instanceof Visualizable) {
      ((Visualizable) value).visualize(lvl, out);
    } else if (value instanceof Iterable<?>) {
      boolean first = true;
      out.text("(");
      for (Object element : (Iterable<?>) value) {
        if (first) {
          first = false;
        } else {
          out.text(", ");
        }
        visualizeValue(element, lvl, out);
      }
      out.text(")");
    } else {
      out.text(String.valueOf(value));
    }

  }

  @Override
  public Variable<T> getVariable() {
    return var;
  }

  @Override
  protected String getVizTypeClassName() {
    return "val";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Value)) { return false; }
    Value<?> that = (Value<?>) o;
    return this.var.equals(that.var) && Objects.equal(this.val, that.val);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(var, val);
  }

  @Override
  public boolean isParseRelevant(PartialOutput po) {
    // Relevant if in an open scope, and there is no subsequent set of the
    // same variable.
    assert (po.getOutput().get() == this);
    return isScopeStillOpen(po) && isSetLaterInScopeRootward(po);
  }

  private boolean isScopeStillOpen(PartialOutput po) {
    Optional<Output> outputOpt = po.getOutput();
    if (outputOpt.isPresent()) {
      Output o = outputOpt.get();
      if (o instanceof Scope) {
        if (var.equals(((Scope) o).var)) {
          return !po.getRightSideOutput().isPresent();
        }
      }
    }
    Optional<PartialOutput.SpanningPartialOutput> parentOpt = po.getParent();
    return !parentOpt.isPresent() || isScopeStillOpen(parentOpt.get());
  }

  private boolean isSetLaterInScopeRootward(PartialOutput po) {
    PartialOutput.SpanningPartialOutput parent = po.getParent().get();
    // Look right from parent.
    ImmutableList<PartialOutput> siblings = parent.body;
    int indexInParent = siblings.indexOf(po);
    assert (indexInParent >= 0);
    for (int i = indexInParent + 1, n = siblings.size(); i < n; ++i) {
      PartialOutput laterSibling = siblings.get(i);
      if (isSetOfSameVar(laterSibling)) {
        return true;
      }
      if (isSetLaterInScopeLeafward(laterSibling)) { return true; }
    }
    Optional<Output> parentOutOpt = parent.getOutput();
    if (parentOutOpt.isPresent()) {
      Output parentOut = parentOutOpt.get();
      if (parentOut instanceof Scope && var.equals(((Scope) parentOut).var)) {
        return false;
      }
    }
    return parent.getParent().isPresent()
        && isSetLaterInScopeRootward(parent);
  }

  private boolean isSetLaterInScopeLeafward(PartialOutput po) {
    ImmutableList<PartialOutput> body = po.getBody();
    for (PartialOutput bodyPart : body) {
      if (isSetOfSameVar(bodyPart)) { return true; }
    }
    for (PartialOutput bodyPart : body) {
      if (isSetLaterInScopeLeafward(bodyPart)) { return true; }
    }
    return false;
  }

  private boolean isSetOfSameVar(PartialOutput po) {
    Optional<Output> oopt = po.getOutput();
    if (!oopt.isPresent()) { return false; }
    Output o = oopt.get();
    return o instanceof Value && ((Value<?>) o).var.equals(this.var);
  }


  /**
   * The most recent value of var in-scope at the current stage of the parse.
   */
  public static <T> Optional<T> valueOf(
      final Variable<T> var, OutputContext ctx) {
    Optional<Output> output = ctx.lastInScope(
        new Predicate<BinaryOutput>() {
          @Override
          public boolean apply(BinaryOutput o) {
            return o instanceof Scope
                && var.equals(((Scope) o).var);
          }
        },
        new Predicate<Output>() {
          @Override
          public boolean apply(Output o) {
            return o instanceof Value
                && var.equals(((Value<?>) o).var);
          }
        });
    if (output.isPresent()) {
      Value<?> value = (Value<?>) output.get();
      Optional<T> result = var.typeGuard.check(value.val);
      Preconditions.checkState(result.isPresent());
      return result;
    } else {
      return Optional.absent();
    }
  }

  @Override
  protected int compareToSameClass(UnaryOutput that) {
    return this.var.name.compareTo(((Value<?>) that).var.name);
  }

  @Override
  public boolean canReorder() {
    return true;
  }
}