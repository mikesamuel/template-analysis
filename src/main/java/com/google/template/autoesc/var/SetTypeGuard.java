package com.google.template.autoesc.var;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/**
 * A type guard for multi-valued variable values.
 * This allows immutable sets as values after delegating to another guard to
 * vet the elements.
 */
public final class SetTypeGuard<T extends Enum<T>>
implements TypeGuard<ImmutableSet<T>> {
  /** A guard for the element type. */
  public final TypeGuard<T> elementGuard;

  SetTypeGuard(TypeGuard<T> elementGuard) {
    this.elementGuard = elementGuard;
  }

  @Override
  public Optional<ImmutableSet<T>> check(Object o) {
    if (!(o instanceof ImmutableSet<?>)) {
      return Optional.absent();
    }
    for (Object el : ((ImmutableSet<?>) o)) {
      if (!elementGuard.check(el).isPresent()) { return Optional.absent(); }
    }
    @SuppressWarnings("unchecked")
    ImmutableSet<T> s = (ImmutableSet<T>) o;
    return Optional.of(s);
  }

  @Override
  public String getTypeName() {
    return elementGuard.getTypeName() + "*";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SetTypeGuard<?>)) { return false; }
    SetTypeGuard<?> that = (SetTypeGuard<?>) o;
    return this.elementGuard.equals(that.elementGuard);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getClass(), elementGuard);
  }
}