package com.google.template.autoesc;


/**
 * A kind of transition from one parse state to another.
 */
public enum TransitionType {
  /** Corresponds to a {@link Combinator#exit} with {@link Success#FAIL}. */
  EXIT_FAIL,
  /** Corresponds to a {@link Combinator#exit} with {@link Success#PASS}. */
  EXIT_PASS,
  /** Corresponds to a {@link Combinator#enter}. */
  ENTER,
}
