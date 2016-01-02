package com.google.template.autoesc.file;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.autoesc.var.RawTypeGuard;
import com.google.template.autoesc.var.TypeGuard;

/**
 * Maps names to nominal types.
 */
public final class Types {
  private final ImmutableMap<String, TypeGuard<?>> nominalTypes;

  Types(Iterable<TypeGuard<?>> guards) {
    ImmutableMap.Builder<String, TypeGuard<?>> b = ImmutableMap.builder();
    for (TypeGuard<?> t : guards) {
      b.put(t.getTypeName(), t);
    }
    this.nominalTypes = b.build();
  }

  /** A type {@code t} such that {@code name.equals(t.getTypeName())} */
  public Optional<TypeGuard<?>> getNominalType(String name) {
    return Optional.<TypeGuard<?>>fromNullable(this.nominalTypes.get(name));
  }

  /** A builder for {@link Types}. */
  public static final class Builder {
    private final ImmutableList.Builder<TypeGuard<?>> guards =
        ImmutableList.builder();

    /**
     * Defines a type for instances of the given class that uses the
     * class's simple name as the type name.
     */
    public <T> Builder define(Class<T> typeToken) {
      return define(new RawTypeGuard<>(typeToken));
    }

    /**
     * Defines a type based on the given guard.
     * @param guard must have a stable {@link TypeGuard#getTypeName()}.
     */
    public Builder define(TypeGuard<?> guard) {
      guards.add(guard);
      return this;
    }

    /** A {@link Types} with the previously defined types. */
    public Types build() {
      return new Types(guards.build());
    }
  }

  /** Contains no type guards. */
  public static final Types EMPTY = new Builder().build();
}
