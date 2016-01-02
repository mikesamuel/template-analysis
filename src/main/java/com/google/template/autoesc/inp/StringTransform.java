package com.google.template.autoesc.inp;

import com.google.template.autoesc.viz.Visualizable;


/**
 * A mapping from one string to another that keeps track of which characters
 * in the output correspond to which characters in the input.
 *
 * @see StringTransforms
 */
public interface StringTransform extends Visualizable {
  /**
   * A transformation that allows a string in one language to be embedded
   * within a substring of a string in another language.
   */
  void encode(CharSequence s, TransformedString.Builder out);
  /**
   * The reverse of {@link #encode}.  Unpacks an embedded substring.
   */
  void decode(
      CharSequence s, TransformedString.Builder out, boolean wholeInput);
}
