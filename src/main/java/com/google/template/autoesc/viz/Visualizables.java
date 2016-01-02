package com.google.template.autoesc.viz;

import java.io.IOException;

/**
 * Factories for instances of Visualizable.
 */
public final class Visualizables {

  /**
   * A visualizable that simply emits the given text.
   */
  public static Visualizable text(String s) {
    return new TextVisualizable(s);
  }
}

final class TextVisualizable implements Visualizable {
  final String s;
  TextVisualizable(String s) {
    this.s = s;
  }
  @Override
  public void visualize(DetailLevel lvl, VizOutput out) throws IOException {
    out.text(s);
  }
}
