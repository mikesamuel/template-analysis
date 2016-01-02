package com.google.template.autoesc.inp;

import javax.annotation.Nullable;

import com.google.common.base.Objects;


/**
 * The source of a portion of an input to a parser which
 * allows diagnostic measures to refer to the source of a problem.
 */
public final class Source {
  /**
   * For diagnostic purposes only.
   * Typically a file path or URL.
   */
  public final String source;
  /** The line number in source of the start. */
  public final int lineNum;

  /** */
  public Source(String source, int lineNum) {
    this.source = source;
    this.lineNum = lineNum;
  }

  @Override
  public String toString() {
    return source + ":" + lineNum;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof Source)) {
      return false;
    }
    Source that = (Source) o;
    return source.equals(that.source) && lineNum == that.lineNum;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(source, lineNum);
  }

  /** A cop-out.  TODO: make this unnecessary */
  public static final Source UNKNOWN = new Source("?", 0);
}
