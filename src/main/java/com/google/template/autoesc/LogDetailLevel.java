package com.google.template.autoesc;

enum LogDetailLevel {
  NONE,
  HIGH_LEVEL,
  MODERATE,
  DETAILED,
  ALL,
  ;

  /** {@code logLevel.meetsThreshhold(threshold)}. */
  boolean shouldLogEventAtLevel(LogDetailLevel eventLevel) {
    return this.compareTo(eventLevel) >= 0;
  }
}
