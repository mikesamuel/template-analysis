package com.google.template.autoesc.combimpl;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Precedence;
import com.google.template.autoesc.ProdName;


/**
 * A combinator that wraps one other combinator.
 */
public abstract class UnaryCombinator extends AbstractCombinator {
  /** The sole child. */
  public final Combinator body;

  UnaryCombinator(Supplier<NodeMetadata> mds, Combinator body) {
    super(mds);
    this.body = body;
  }

  @Override
  public final ImmutableList<Combinator> children() {
    return ImmutableList.of(body);
  }

  @Override
  public final Combinator unfold(
      NodeMetadata newMetadata,
      Function<ProdName, ProdName> renamer,
      ImmutableList<Combinator> newChildren) {
    Preconditions.checkState(newChildren.size() == 1);
    return unfold(newMetadata, renamer, newChildren.get(0));
  }

  protected abstract Combinator unfold(
      NodeMetadata newMetadata,
      Function<ProdName, ProdName> renamer,
      Combinator newBody);

  @Override
  public Precedence precedence() {
    return Precedence.SELF_CONTAINED;
  }
}
