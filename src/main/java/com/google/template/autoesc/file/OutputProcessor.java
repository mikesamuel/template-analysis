package com.google.template.autoesc.file;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.out.PartialOutput;


/**
 * Converts a parse tree node to part of a language.
 *
 * @param <T> the abstract syntax tree or language part produced from
 *    parse tree node processed by this.
 */
abstract class OutputProcessor<T> {
  /**
   * Operates before output processors have been run on child parse-tree nodes.
   *
   * <p>
   * This default implementation always returns absent.
   *
   * @param body the child parse tree nodes.
   * @param t the processor that is operating with current node context
   *     relevant to the node being processed.
   * @return absent to continue to indicate no value derived so
   *     {@linkplain #post post-processing}
   *     should be attempted, or a present value to skip post-processing.
   */
  Optional<T> pre(ImmutableList<PartialOutput> body, TreeProcessor t) {
    return Optional.absent();
  }

  /**
   * Operates on processed child nodes.
   *
   * @param children the results of applying processors to child outputs
   *    in order.
   * @param t the processor that is operating with current node context
   *     relevant to the node being processed.
   * @return absent to indicate no value derived or present to indicate a
   *     value that should appear on the parent's {@code children} post list.
   */
  abstract Optional<T> post(ImmutableList<Object> children, TreeProcessor t);
}
