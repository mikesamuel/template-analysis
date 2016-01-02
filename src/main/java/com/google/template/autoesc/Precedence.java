package com.google.template.autoesc;


/**
 * An operator precedence used to parenthesize the
 * {@link com.google.template.autoesc.viz.Visualizable} form
 * of a grammar.
 */
public enum Precedence {
  /** A safe worst-case value. */
  UNKNOWN,
  /** Precedence of OR. */
  OR,
  /** Precedence of concatenation. */
  SEQUENCE,
  /** Precedence of a parenthesized or unary sub-expression. */
  SELF_CONTAINED,
  ;
}