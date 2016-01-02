package com.google.template.autoesc.var;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * A type guard for an unparameterized type based on a simple class check.
 */
public final class RawTypeGuard<T> implements TypeGuard<T> {
  /** An unparameterized type. */
  public final Class<T> typeToken;
  /** ctor */
  public RawTypeGuard(Class<T> typeToken) {
    Preconditions.checkArgument(typeToken.getTypeParameters().length == 0);
    this.typeToken = typeToken;
  }
  @Override
  public Optional<T> check(@Nullable Object o) {
    if (o != null && typeToken.isInstance(o)) {
      return Optional.of(typeToken.cast(o));
    } else {
      return Optional.<T>absent();
    }
  }
  @Override
  public String getTypeName() {
    return typeToken.getSimpleName();
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RawTypeGuard)) { return false; }
    RawTypeGuard<?> that = (RawTypeGuard<?>) o;
    return this.typeToken.equals(that.typeToken);
  }
  @Override
  public int hashCode() {
    return typeToken.hashCode();
  }
}