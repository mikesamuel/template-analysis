package com.google.template.autoesc.combimpl;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.var.MultiVariable;


/**
 * Passes when a variable has a value which is in a specified set.
 */
public final class TestMultiVarCombinator<T extends Enum<T>>
extends AbstractVarCombinator<ImmutableSet<T>> {
  /** */
  public TestMultiVarCombinator(
      Supplier<NodeMetadata> mds, MultiVariable<T> var, T value) {
    super(mds, var, ImmutableSet.of(ImmutableSet.of(value)));
  }


  @Override
  public TestMultiVarCombinator<T> unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer) {
    if (newMetadata.equals(this.md)) {
      return this;
    }
    MultiVariable<T> multivar = (MultiVariable<T>) this.var;
    T value = this.vals.iterator().next().iterator().next();
    return new TestMultiVarCombinator<>(
        Suppliers.ofInstance(newMetadata), multivar, value);
  }

  @Override
  protected String getVizTypeClassName() {
    return "has";
  }

  @Override
  protected boolean checkValue(ImmutableSet<T> x) {
    for (ImmutableSet<T> y : vals) {
      if (!x.containsAll(y)) {
	return false;
      }
    }
    return true;
  }

  @Override
  protected String getOperator(boolean inverted) {
    return inverted ? " \u220C " : " \u220B ";  // Contains as member.
  }
}
