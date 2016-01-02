package com.google.template.autoesc.out;

import javax.annotation.Nullable;

import com.google.template.autoesc.viz.AbstractVisualizable;


/**
 * An output that is not paired with one with the opposite side.
 */
public abstract class UnaryOutput
extends AbstractVisualizable implements Output, Comparable<UnaryOutput> {

  protected UnaryOutput() {
  }

  @Override
  public final int compareTo(UnaryOutput that) {
    Class<? extends UnaryOutput> thisClass = this.getClass();
    Class<? extends UnaryOutput> thatClass = that.getClass();
    if (thisClass == thatClass) {
      return compareToSameClass(that);
    }
    assert thisClass.getClassLoader() == thatClass.getClassLoader();
    return thisClass.getName().compareTo(thatClass.getName());
  }

  protected abstract int compareToSameClass(UnaryOutput that);

  /**
   * True if the output can be reordered without changing semantics.
   * <p>
   * If the output can be reordered, then its position will still not be
   * swapped relative to any item that is equivalent to it according to
   * {@link #compareTo}.
   * <p>
   * Reordering a run of unary output involves moving all reorderable unary
   * outputs to the left of a block and sorting (stable) them using
   * {@link #compareTo} and then coalescing adjacent ones.
   */
  @SuppressWarnings("static-method")
  public boolean canReorder() {
    return false;
  }

  @Override
  public abstract boolean equals(@Nullable Object o);
  @Override
  public abstract int hashCode();
}