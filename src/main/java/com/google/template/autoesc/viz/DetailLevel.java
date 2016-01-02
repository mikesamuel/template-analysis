package com.google.template.autoesc.viz;

/** The amount of detail about an object in a visualization. */
public enum DetailLevel {
  /** For limited space, usually just a link to more detail. */
  TINY,
  /**
   * A short representation that makes obvious a broad classification like its
   * type.
   */
  SHORT,
  /**
   * Full detail about the object which might be linked to by shorter
   * representations.
   */
  LONG,
}
