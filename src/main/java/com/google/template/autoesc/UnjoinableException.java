package com.google.template.autoesc;

import java.io.IOException;

import javax.annotation.Nullable;

import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;
import com.google.template.autoesc.viz.Visualizables;
import com.google.template.autoesc.viz.VizOutput;

/** Raised when two branches cannot be joined to continue a parse. */
public class UnjoinableException extends Exception {
  private static final long serialVersionUID = -1200464708627500462L;

  /** The parse state that couldn't be joined with {@link #b}. */
  public transient final Parse a;
  /** The parse state that couldn't be joined with {@link #a}. */
  public transient final Parse b;
  /** Part of {@link #a} that couldn't be reconciled with {@link #bElement}. */
  public final Object aElement;
  /** Part of {@link #b} that couldn't be reconciled with {@link #aElement}. */
  public final Object bElement;

  private static String messageWithDifference(
      String message, Object aElement, Object bElement) {
    StringBuilder sb = new StringBuilder(message);
    try {
      TextVizOutput out = new TextVizOutput(sb);
      if (message.length() != 0) {
        out.text(message);
        out.text(" : ");
      }
      Visualizables.ofObject(aElement).visualize(DetailLevel.SHORT, out);
      out.text(" != ");
      Visualizables.ofObject(bElement).visualize(DetailLevel.SHORT, out);
      return sb.toString();
    } catch (IOException ex) {
      // When the backing buffer is a StringBuilder, its the responsibility of
      // TextVizOutput and Visualizable implementors not to throw IOException.
      throw (AssertionError) new AssertionError().initCause(ex);
    }
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
