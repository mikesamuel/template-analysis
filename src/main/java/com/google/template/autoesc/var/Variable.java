package com.google.template.autoesc.var;

import java.io.IOException;

import com.google.common.base.Objects;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A name that can be associated with a value in scope that allows one
 * {@link com.google.template.autoesc.Combinator} to communicate with a
 * {@link com.google.template.autoesc.Combinators#in distant one} by pushing
 * scope and value markers onto the
 * {@link com.google.template.autoesc.Parse#out output}.
 */
public class Variable<T> extends AbstractVisualizable {
  /** Name for diagnostic purposes. */
  public final String name;
  /** The type of the value. */
  public final TypeGuard<T> typeGuard;

  Variable(String name, TypeGuard<T> typeGuard) {
    this.name = name;
    this.typeGuard = typeGuard;
  }

  /** Factory */
  public static <T> Variable<T> create(String name, final Class<T> typeToken) {
    return new Variable<>(name, new RawTypeGuard<>(typeToken));
  }

  /** Factory */
  public static <T> Variable<T> create(String name, TypeGuard<T> typeGuard) {
    return new Variable<>(name, typeGuard);
  }

  /** Factory */
  public static <T extends Enum<T>>
  MultiVariable<T> createMulti(String name, final Class<T> typeToken) {
    return new MultiVariable<>(name, new RawTypeGuard<>(typeToken));
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != this.getClass()) { return false; }
    Variable<?> that = (Variable<?>) o;
    return this.name.equals(that.name) && this.typeGuard.equals(that.typeGuard);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, typeGuard);
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text(name);
    if (lvl == DetailLevel.LONG) {
      out.text(" : ");
      out.text(typeGuard.getTypeName());
    }
  }

  @Override
  protected String getVizTypeClassName() {
    return "var";
  }

}
