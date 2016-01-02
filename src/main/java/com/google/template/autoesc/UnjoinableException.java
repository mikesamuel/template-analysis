package com.google.template.autoesc;

import javax.annotation.Nullable;

/** Raised when two branches cannot be joined to continue a parse. */
public class UnjoinableException extends Exception {
  private static final long serialVersionUID = -1200464708627500462L;

  /** The parse state that couldn't be joined with {@link #b}. */
  public final Parse a;
  /** The parse state that couldn't be joined with {@link #a}. */
  public final Parse b;
  /** Part of {@link #a} that couldn't be reconciled with {@link #bElement}. */
  public final Object aElement;
  /** Part of {@link #b} that couldn't be reconciled with {@link #aElement}. */
  public final Object bElement;

  private static String messageWithDifference(
      String message, Object aElement, Object bElement) {
    StringBuilder sb = new StringBuilder(message);
    if (sb.length() != 0) {
      sb.append(" : ");
    }
    sb.append(aElement).append(" != ").append(bElement);
    return sb.toString();
  }

  /** */
  public UnjoinableException(
      String message,
      @Nullable Throwable cause,
      Parse a, Object aElement,
      Parse b, Object bElement) {
    super(messageWithDifference(message, aElement, bElement), cause);
    this.a = a;
    this.aElement = aElement;
    this.b = b;
    this.bElement = bElement;
  }

  /** */
  public UnjoinableException(
      @Nullable Throwable cause,
      Parse a, Object aElement,
      Parse b, Object bElement) {
    this("", cause, a, aElement, b, bElement);
  }

  /** */
  public UnjoinableException(
      String message,
      Parse a, Object aElement,
      Parse b, Object bElement) {
    this(message, null, a, aElement, b, bElement);
  }

  /** */
  public UnjoinableException(
      Parse a, Object aElement,
      Parse b, Object bElement) {
    this("", null, a, aElement, b, bElement);
  }
}
