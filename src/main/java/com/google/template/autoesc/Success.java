package com.google.template.autoesc;

/**
 * Indicates whether a combinators is {@linkplain Combinator#exit exiting} on a
 * passing or failing branch.
 */
public enum Success {
  /** Exit occurs on a failing branch. */
  FAIL,
  /** Exit occurs on a passing branch. */
  PASS,
  ;

  /**
   * The opposite value.
   */
  public Success inverse() {
    switch (this) {
      case FAIL: return PASS;
      case PASS: return FAIL;
    }
    throw new AssertionError(name());
  }
}
