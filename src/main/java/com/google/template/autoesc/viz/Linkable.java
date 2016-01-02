package com.google.template.autoesc.viz;


/**
 * A visualizable that has an ID that can be used to identify it within a
 * document.
 */
public interface Linkable extends Visualizable {
  /**
   * An ID that distinguishes this from other objects and concepts that should
   * be distinct in the mind of the person viewing the visualization.
   */
  boolean shouldLinkTo();
}
