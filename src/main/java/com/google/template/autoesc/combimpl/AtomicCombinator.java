package com.google.template.autoesc.combimpl;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Precedence;
import com.google.template.autoesc.ProdName;

abstract class AtomicCombinator extends AbstractCombinator {

  AtomicCombinator(Supplier<NodeMetadata> mds) {
    super(mds);
  }

  @Override
  public final Precedence precedence() {
    return Precedence.SELF_CONTAINED;
  }

  @Override
  public ImmutableList<Combinator> children() {
    return ImmutableList.of();
  }

  @Override
  public final Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      ImmutableList<Combinator> newChildren) {
    Preconditions.checkArgument(newChildren.isEmpty());
    return unfold(newMetadata, renamer);
  }

  protected abstract AtomicCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer);
}
