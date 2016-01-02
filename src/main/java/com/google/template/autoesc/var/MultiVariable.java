package com.google.template.autoesc.var;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;


/**
 * A variable that can assume multiple values.
 */
public final class MultiVariable<T extends Enum<T>>
extends Variable<ImmutableSet<T>> {
  MultiVariable(String name, TypeGuard<T> elementGuard) {
    super(name, new SetTypeGuard<>(elementGuard));
  }

  /**
   * @param a A value whose {@link Value#var} equals this.
   * @param b A value whose {@link Value#var} equals this.
   * @return A value whose {@link Value#var} equals this that has only the
   *    elements common to both a and b.
   */
  public Value<ImmutableSet<T>> intersection(Value<?> a, Value<?> b) {
    Optional<ImmutableSet<T>> aEls = this.typeGuard.check(a.val);
    Preconditions.checkArgument(aEls.isPresent(), a);

    Optional<ImmutableSet<T>> bEls = this.typeGuard.check(b.val);
    Preconditions.checkArgument(bEls.isPresent(), b);

    ImmutableSet<T> smaller = aEls.get();
    ImmutableSet<T> larger = bEls.get();
    if (smaller.size() > larger.size()) {
      ImmutableSet<T> swap = smaller;
      smaller = larger;
      larger = swap;
    }

    ImmutableSet.Builder<T> intersection = ImmutableSet.builder();
    for (T el : smaller) {
      if (larger.contains(el)) {
        intersection.add(el);
      }
    }
    return new Value<>(this, intersection.build());
  }

  /**
   * The empty value whose {@link Value#var} is this.
   */
  public Value<ImmutableSet<T>> emptyValue() {
    return new Value<>(this, ImmutableSet.<T>of());
  }
}