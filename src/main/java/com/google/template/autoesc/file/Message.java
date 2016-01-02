package com.google.template.autoesc.file;

import com.google.template.autoesc.inp.Source;

/**
 * A message about the parse process intended for the author/maintainer of
 * the content being parsed.
 */
public final class Message {
  /** Human readable text. */
  public final String text;
  /**
   * True if the message is indicative of an error that calls into question
   * any output derived from the input.
   */
  public final boolean isError;
  /**
   * The portion of the input that text pertains to.
   */
  public final Source source;

  Message(String text, boolean isError, Source source) {
    this.text = text;
    this.isError = isError;
    this.source = source;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (isError) {
      sb.append("ERROR: ");
    }
    if (!Source.UNKNOWN.source.equals(source)) {
      sb.append(source).append(": ");
    }
    sb.append(text);
    return sb.toString();
  }
}
