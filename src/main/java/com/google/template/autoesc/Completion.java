package com.google.template.autoesc;

import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;

/**
 * The state of a parse.
 */
public enum Completion {
  /** A state before {@link Parser#startParse} */
  NOT_STARTED,
  /** A state while parsing is paused for lack of input. */
  IN_PROGRESS,
  /**
   * A state when parsing has concluded that the input is not a prefix of
   * any string in the language being parsed.
   */
  FAILED,
  /**
   * A state when parsing has found a maximal <b>prefix</b> of the input
   * in the language.
   * <p>
   * To check that the whole input is in the language,
   * additionally check that the input,
   * <tt>{@link Parser#getParse getParse}().{@link Parse#inp inp}</tt>,
   * is {@link com.google.template.autoesc.inp.InputCursor#isEmpty empty} and
   * {@link com.google.template.autoesc.inp.InputCursor#isComplete complete}.
   */
  PASSED,
  ;

  /**
   * The completion state for the given parse state.
   */
  public static Completion of(Parse p) {
    if (p.stack.isEmpty()) { return Completion.NOT_STARTED; }
    if (p.stack.tl().isEmpty()) {
      Combinator top = p.stack.hd();
      if (top instanceof ErrorCombinator) {
        return Completion.FAILED;
      }
      if (top instanceof EmptyCombinator) {
        return Completion.PASSED;
      }
    }
    return Completion.IN_PROGRESS;
  }
}
