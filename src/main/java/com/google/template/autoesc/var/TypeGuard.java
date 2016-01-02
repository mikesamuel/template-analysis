package com.google.template.autoesc.var;

import javax.annotation.Nullable;

import com.google.common.base.Optional;

/** Checks at runtime that a value is of the right type. */
public interface TypeGuard<T> {
  /** Present when o is of the right type. */
  Optional<T> check(@Nullable Object o);
  /** A diagnostic description of the type. */
  String getTypeName();
  @Override int hashCode();
  @Override boolean equals(Object o);
}