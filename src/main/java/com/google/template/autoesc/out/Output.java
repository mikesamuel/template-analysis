package com.google.template.autoesc.out;

import com.google.common.base.Optional;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.viz.Visualizable;


/**
 * Parsing produces an output which is a series of events.
 * <p>
 * Instead of producing side-effects during parsing, and dealing with
 * rolling back side-effects when backtracking, we produce a stream of events.
 * <p>
 * Backtracking then simply requires truncating
 * {@link com.google.template.autoesc.Parse#out that list}.
 */
public interface Output extends Visualizable {
  /**
   * If present, a single output that is equivalent to this followed immediately
   * by next.
   */
  Optional<Output> coalesceWithFollower(Output next);

  /**
   * True if a parser might need to keep this state around to operate correctly.
   * <p>
   * There are a few broad kinds of parse relevant output:
   * <ol>
   *   <li>That which is used to decide which branches to take or to properly
   *     like {@link Variable values} in open scopes.
   *   <li>Cues that are used to join two forked parser states.</li>
   * </ol>
   */
  boolean isParseRelevant(PartialOutput po);
}
