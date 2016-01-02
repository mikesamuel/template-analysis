package com.google.template.autoesc.combimpl;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.Pause;
import com.google.template.autoesc.Success;
import com.google.template.autoesc.viz.AbstractVisualizable;


/**
 * Abstract base class for combinator implementations.
 */
public abstract class AbstractCombinator
extends AbstractVisualizable implements Combinator {
  final NodeMetadata md;

  AbstractCombinator(Supplier<NodeMetadata> mds) {
    this.md = Preconditions.checkNotNull(mds.get());
  }

  @Override
  public boolean shouldLinkTo() {
    return true;
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    throw new IllegalStateException("Should not be o stack");
  }

  protected final ParseDelta pause() {
    return ParseDelta.builder(Pause.pause(this)).build();
  }

  @Override
  public abstract boolean equals(@Nullable Object that);

  @Override
  public abstract int hashCode();

  @Override
  public boolean reachesWithoutConsuming(Combinator target, Language lang) {
    return this.equals(target);
  }

  @Override
  public final NodeMetadata getMetadata() {
    return md;
  }
}
