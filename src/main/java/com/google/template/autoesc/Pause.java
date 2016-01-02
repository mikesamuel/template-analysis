package com.google.template.autoesc;

import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;
import com.google.template.autoesc.combimpl.SeqCombinator;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.Source;

/**
 * Functions relating to pausing -- interrupting parsing until more input is
 * available.
 */
public final class Pause {
  private Pause() {
    // Not instantiable
  }

  private static final Supplier<NodeMetadata> PAUSE_METADATA_SUPPLIER =
      Suppliers.ofInstance(new NodeMetadata(
          new Source("<pause>", 0),
          0,
          Optional.<String>absent()
          ));

  /**
   * A paused version of the given combinator.
   */
  public static Combinator pause(Combinator c) {
    if (isPaused(c)) { return c; }
    return new SeqCombinator(
        PAUSE_METADATA_SUPPLIER, EmptyCombinator.INSTANCE, c);
  }

  /**
   * The opposite of {@link #pause}.
   */
  public static Combinator resume(Combinator c) {
    if (c instanceof SeqCombinator) {
      SeqCombinator sd = (SeqCombinator) c;
      List<Combinator> children = sd.children();
      if (!children.isEmpty() && children.get(0) instanceof EmptyCombinator) {
        return resume(sd.second);
      }
    }
    return c;
  }

  /**
   * True if pausing might be appropriate.
   */
  public static boolean couldPause(InputCursor inp, FList<Combinator> stack) {
    return !inp.isComplete()
        && !hasWholeCodepoint(inp.getAvailable())
        && !isExiting(stack);
  }

  /**
   * True if the parse state is paused waiting for input.
   * @see Parse#smallStep
   * @see com.google.template.autoesc.combimpl.AbstractCombinator#pause
   */
  public static boolean isPaused(Combinator c) {
    return c instanceof SeqCombinator
        && EmptyCombinator.INSTANCE.equals(
            ((SeqCombinator) c).children().get(0));
  }


  /**
   * True if a small step would result in a call to {@link Combinator#exit}
   * instead of {@link Combinator#enter}.
   * @see #smallStep
   */
  private static boolean isExiting(FList<Combinator> stack) {
    if (stack.isEmpty()) {
      return false;
    }
    Combinator c = stack.hd();
    return c instanceof ErrorCombinator || c instanceof EmptyCombinator;
  }

  /** True if an entire code-point is available at the start of cs. */
  private static boolean hasWholeCodepoint(CharSequence cs) {
    switch (cs.length()) {
      case 0:  return false;
      case 1:  return !Character.isHighSurrogate(cs.charAt(0));
      default: return true;
    }
  }

}
