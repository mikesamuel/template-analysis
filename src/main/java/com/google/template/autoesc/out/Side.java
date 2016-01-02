package com.google.template.autoesc.out;


/** A side of a continous region of the output. */
public enum Side {
  /**
   * The left side.
   * Since {@link com.google.template.autoesc.Parse#out} is built on a
   * left-to-right parse, a left-side is farther from the head of the list than
   * its corresponding right-hand-side.
   */
  LEFT,
  /** The right side. */
  RIGHT,
}