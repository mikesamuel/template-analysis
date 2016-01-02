package com.google.template.autoesc.combimpl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Precedence;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.inp.Source;


/**
 * A combinator that may appear many times in a given grammar.
 */
public abstract class SingletonCombinator extends AbstractCombinator {
  SingletonCombinator() {
    super(SINGLETON_METADATA_SUPPLIER);
  }

  @Override
  public boolean shouldLinkTo() {
    return false;
  }

  @Override
  public final ImmutableList<Combinator> children() {
    return ImmutableList.of();
  }

  @Override
  public final Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      ImmutableList<Combinator> newChildren) {
    Preconditions.checkArgument(newChildren.isEmpty());
    return this;
  }

  @Override
  public final Precedence precedence() {
    return Precedence.SELF_CONTAINED;
  }

  private static final Supplier<NodeMetadata> SINGLETON_METADATA_SUPPLIER =
      new SingletonMetadataSupplier();

}


final class SingletonMetadataSupplier implements Supplier<NodeMetadata> {

  private static final Source BUILTIN = new Source("builtin", 0);

  private static final NodeMetadata SINGLETON_METADATA = new NodeMetadata(
      BUILTIN,
      -1,
      Optional.<String>absent());

  @Override
  public NodeMetadata get() {
    return SINGLETON_METADATA;
  }
}
