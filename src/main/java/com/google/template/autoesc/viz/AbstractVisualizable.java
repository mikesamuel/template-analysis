package com.google.template.autoesc.viz;

import java.io.Closeable;
import java.io.IOException;

/**
 * A Visualizable that uses a {@code <span class="...">} to bracket its content
 * and which relates long and short forms using linkable IDs where available.
 */
public abstract class AbstractVisualizable implements Visualizable {
  /**
   * Wraps the output in a span with the viz type class name in the class
   * attribute.
   */
  public static void visualize(
      Visualizable v, String typeClasses, DetailLevel lvl, VizOutput out,
      Visualizable body)
  throws IOException {
    VizOutput.Tag.Builder wrapper = VizOutput.tag(TagName.SPAN);
    if (v instanceof Linkable) {
      Linkable linkable = (Linkable) v;
      if (linkable.shouldLinkTo()) {
        String id = out.getIdFor(linkable);
        if (lvl == DetailLevel.LONG) {
          wrapper.attr(AttribName.ID, id);
        } else {
          wrapper.dataAttr("ref-id", id);
        }
      }
    }
    StringBuilder className = new StringBuilder(typeClasses);
    switch (lvl) {
      case TINY:  className.append(" detail:tiny abv"); break;
      case SHORT: className.append(" detail:short abv"); break;
      case LONG:  className.append(" detail:long"); break;
    }
    wrapper.attr(AttribName.CLASS, className.toString());
    try (Closeable wrapperC = out.open(wrapper.build())) {
      body.visualize(lvl, out);
    }
  }

  @Override
  public final void visualize(DetailLevel lvl, VizOutput out)
  throws IOException {
    String typeClasses = getVizTypeClassName();
    String extraTypeClasses = getExtraVizTypeClasses();
    if (!"".equals(extraTypeClasses)) {
      typeClasses += extraTypeClasses;
    }
    visualize(this, typeClasses, lvl, out, new Visualizable() {
      @Override
      public void visualize(DetailLevel ilvl, VizOutput iout)
      throws IOException {
        visualizeBody(ilvl, iout);
      }
    });
  }

  protected abstract String getVizTypeClassName();

  @SuppressWarnings("static-method")
  protected String getExtraVizTypeClasses() {
    return "";
  }

  protected abstract void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException;

  @Override
  public String toString() {
    return TextVizOutput.vizToString(this, DetailLevel.LONG);
  }
}
