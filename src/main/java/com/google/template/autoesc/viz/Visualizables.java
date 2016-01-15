package com.google.template.autoesc.viz;

import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.out.Output;

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

  /**
   * A visualizable that puts commas between the elements of {@code ls}.
   */
  public static Visualizable commaList(Iterable<? extends Output> ls) {
    return new CommaListVisualizable(ls);
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

  @Override
  public String toString() {
    return TextVizOutput.vizToString(this, DetailLevel.LONG);
  }
}

final class CommaListVisualizable extends AbstractVisualizable {
  final ImmutableList<Visualizable> ls;

  CommaListVisualizable(Iterable<? extends Visualizable> ls) {
    this.ls = ImmutableList.copyOf(ls);
  }

  @Override
  protected String getVizTypeClassName() {
    return "list";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
      throws IOException {
    boolean first = true;
    for (Visualizable v : ls) {
      if (first) {
        first = false;
      } else {
        out.text(", ");
      }
      v.visualize(lvl, out);
    }
  }
}
