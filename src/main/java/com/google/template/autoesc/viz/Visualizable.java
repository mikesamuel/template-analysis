package com.google.template.autoesc.viz;

import java.io.IOException;


/**
 * Something that can be displayed to a human.
 */
public interface Visualizable {
  /**
   * Appends a visual representation of this onto out at the given level of
   * detail.
   */
  void visualize(DetailLevel lvl, VizOutput out) throws IOException;
}
