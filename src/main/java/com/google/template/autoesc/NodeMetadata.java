package com.google.template.autoesc;

import com.google.common.base.Optional;
import com.google.template.autoesc.inp.Source;


/**
 * Metadata related to a grammar node.
 */
public final class NodeMetadata {
  /** Non-normative. */
  public final Source source;
  /**
   * Normative index of a node that is unique within a given
   * {@link Language} instance unless that node is
   * {@linkplain com.google.template.autoesc.combimpl.SingletonCombinator
   *   singleton}.
   */
  public final int nodeIndex;
  /** Non-normative. */
  public final Optional<String> docComment;

  /** ctor */
  public NodeMetadata(
      Source source,
      int nodeIndex,
      Optional<String> docComment) {
    this.source = source;
    this.nodeIndex = nodeIndex;
    this.docComment = docComment;
  }

  @Override
  public int hashCode() {
    return nodeIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) { return false; }
    return this.nodeIndex == ((NodeMetadata) o).nodeIndex;
  }
}
