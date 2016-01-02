package com.google.template.autoesc.demo;

import java.io.Closeable;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import com.google.common.html.HtmlEscapers;
import com.google.template.autoesc.viz.VizOutput;

/**
 * A visualization that includes HTML tags, IDs, and CLASSes.
 */
public final class HtmlVizOutput extends VizOutput {
  private final Appendable out;

  /** @param out receives HTML */
  public HtmlVizOutput(Appendable out) {
    this.out = out;
  }

  @Override
  public Closeable open(Tag t) throws IOException {
    final String tagName = t.name.name().toLowerCase(Locale.ROOT);
    out.append('<').append(tagName);
    for (Map.Entry<String, String> e : t.attribs.entrySet()) {
      out.append(' ').append(e.getKey()).append("=\"");
      out.append(esc(e.getValue()));
      out.append('"');
    }
    out.append('>');
    return new Closeable() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void close() throws IOException {
        out.append("</").append(tagName).append('>');
      }
    };
  }

  @Override
  public void text(String s) throws IOException {
    out.append(esc(s));
  }

  private static String esc(String s) {
    return HtmlEscapers.htmlEscaper().escape(s);
  }
}
