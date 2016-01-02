package com.google.template.autoesc.inp;

import java.util.List;

import javax.annotation.CheckReturnValue;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.viz.AbstractVisualizable;


/**
 * A portion of a parser input.
 */
public abstract class InputCursor extends AbstractVisualizable {

  /**
   * True if {@link #getAvailable()} is the complete input.
   * No more can follow a pause.
   */
  public abstract boolean isComplete();

  /**
   * The available characters.
   * The characters may not specify full unicode scalar values.
   */
  public abstract CharSequence getAvailable();

  /**
   * An input cursor after nChars characters have been consumed.
   */
  @CheckReturnValue
  public abstract InputCursor advance(int nChars);

  /**
   * The sources of the characters on
   * {code getAvailable().subSequence(0, nChars)}.
   */
  public abstract List<Source> sources(int nChars);

  /**
   * The raw characters corresponding to
   * {code getAvailable().subSequence(0, nChars)}.
   * <p>
   * These characters may differ from the original
   * when a language is {@link Combinators#embed embedded}.
   */
  public abstract CharSequence getRawChars(int nChars);

  /**
   * An input cursor that has available characters derived
   * from the raw characters backing this cursor followed
   * by the given raw characters.
   */
  @CheckReturnValue
  public abstract InputCursor extend(String rawChars, Source source);

  /**
   * An input cursor that has available characters derived
   * from the raw characters backing this cursor <b>preceded</b>
   * by the given raw characters.
   */
  @CheckReturnValue
  public abstract InputCursor insertBefore(String rawChars);

  /**
   * An input cursor like this one but which {@link #isComplete}.
   */
  @CheckReturnValue
  public abstract InputCursor finish();

  @Override
  public abstract int hashCode();

  /**
   * Must be implemented structurally so that
   * {@link Combinators#plus loops} can early out when an iteration
   * consumes no input.
   */
  @Override
  public abstract boolean equals(Object o);

  /** True if there are no available bytes. */
  public final boolean isEmpty() {
    return getAvailable().length() == 0;
  }
}