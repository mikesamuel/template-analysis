package com.google.template.autoesc;

import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;

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
  ;

  /**
   * The kind of transition performed when the head of the stack is c.
   * @param c the head of the parse stack.
   */
  public static TransitionType of(Combinator c) {
    if (c instanceof EmptyCombinator) {
      return EXIT_PASS;
    }
    if (c instanceof ErrorCombinator) {
      return EXIT_FAIL;
    }
    return ENTER;
  }
}
