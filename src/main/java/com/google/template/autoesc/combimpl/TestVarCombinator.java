package com.google.template.autoesc.combimpl;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.var.Variable;


/**
 * Passes when a variable has a value which is in a specified set.
 */
public final class TestVarCombinator<T> extends AbstractVarCombinator<T> {
  /** */
  public TestVarCombinator(
      Supplier<NodeMetadata> mds, Variable<T> var, ImmutableSet<T> vals) {
    super(mds, var, vals);
  }

  @Override
  protected String getVizTypeClassName() {
    return "if";
  }

  @Override
  protected boolean checkValue(T value) {
    return vals.contains(value);
  }

  @Override
  protected String getOperator(boolean inverted) {
    return vals.size() == 1
        ? (inverted ? " != " : " == ")
        : (inverted ? " not in " : " in ");
  }

  @Override
  public TestVarCombinator<T> unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer) {
    if (newMetadata.equals(this.md)) {
      return this;
    }
    return new TestVarCombinator<>(
        Suppliers.ofInstance(newMetadata), var, vals);
  }
}
