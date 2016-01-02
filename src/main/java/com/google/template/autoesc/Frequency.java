package com.google.template.autoesc;


/**
 * A best guess at how often a state of affairs can be observed.
 * Useful for conservative approximations of undecidable functions.
 */
public enum Frequency {
  /** Never occurs. */
  NEVER,
  /**
   * May occur or may not, or it is unknown whether
   * NEVER or ALWAYS is more appropriate.
   */
  SOMETIMES,
  /** Always occurs. */
  ALWAYS,
}
