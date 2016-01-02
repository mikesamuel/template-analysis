package com.google.template.autoesc.viz;

import java.io.Closeable;
import java.io.IOException;


/**
 * A VizOutput that ignores HTML tags and instead produces a plain text string.
 */
public final class TextVizOutput extends VizOutput {
  private final Appendable out;

  /** @param out receives plain text. */
  public TextVizOutput(Appendable out) {
    this.out = out;
  }

  private static final Closeable DO_NOTHING_CLOSEABLE = new Closeable() {
    @Override
    public void close() {
      // Do nothing.
    }
  };

  @Override
  public Closeable open(Tag t) {
    return DO_NOTHING_CLOSEABLE;
  }

  @Override
  public void text(String s) throws IOException {
    out.append(s);
  }

  /** Convenience for getting the text of a visualizable. */
  public static String vizToString(Visualizable v, DetailLevel dl) {
    StringBuilder sb = new StringBuilder();
    TextVizOutput out = new TextVizOutput(sb);
    try {
      v.visualize(dl, out);
    } catch (IOException ex) {
      return "<" + ex + ">";
    }
    return sb.toString();
  }
}
