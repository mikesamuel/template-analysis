package com.google.template.autoesc;

import javax.annotation.CheckReturnValue;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.Linkable;


/**
 * A grammar element that can manipulate a {@linkplain Parse parse state}.
 * <p>
 * A <i>combinator</i> is a grammar element that parses by examining a parse
 * state
 */
public interface Combinator extends Linkable {

  /**
   * Applies this combinator to the input to derive a suffix of the input,
   * and manipulations to the parse that put it in a position to match
   * the suffix.
   * <p>
   * This may result in this combinator being pushed onto the stack in which
   * case the {@link #exit} method must be able to produce a parse state that
   * deals with the suffix.
   *
   * @return A result with no push and which derives {@link EmptyCombinator}
   *    indicates that a prefix was successfully matched.
   *    A result that does not push and which derives {@link ErrorCombinator}
   *    indicates that no prefix was matched.
   *    A result that derives a sequence consisting of the empty string
   *    followed by something else indicates that parsing should pause until
   *    more input is available.
   */
  public ParseDelta enter(Parse parse);

  /**
   * Called after control has left this combinator to move to a position to
   * handle the suffix of the the input after that consumed by this combinator.
   * <p>
   * This method should only be called if this
   * {@linkplain ParseDelta.Builder#push pushed itself} onto the stack
   * when it {@linkplain #enter entered}.
   *
   * @return A result that does not push.
   *    A result which derives {@link EmptyCombinator} to
   *    indicate that a prefix was successfully matched.
   *    A result that derives {@link ErrorCombinator} to
   *    indicate that no prefix was matched.
   */
  public ParseDelta exit(Parse parse, Success s);

  /**
   * All the transitions that could occur given no input.
   *
   * @param tt The kind of transition that precedes the return transition.
   *    For example, for combinators that do not
   *    {@linkplain ParseDelta#push push} themselves onto a stack, an
   *    {@link TransitionType#ENTER ENTER} transition can be followed by a
   *    {@link ParseDelta#pass} or {@link ParseDelta#fail}, while an
   *    {@link TransitionType#EXIT_PASS EXIT_PASS} from a combinator
   *    that remains on the stack might cause a pass or an enter of a child.
   * @param lang referents for any non-terminals.
   */
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext octx);

  /**
   * Child nodes of this grammar node.
   */
  @CheckReturnValue
  ImmutableList<Combinator> children();

  /**
   * Equivalent to this grammar node but with the given child nodes, and any
   * local name renamed using the renamer, and with the given metadata.
   * @throws IllegalArgumentException if the child count is off.
   */
  @CheckReturnValue
  Combinator unfold(
      NodeMetadata newMetadata,
      Function<ProdName, ProdName> renamer,
      ImmutableList<Combinator> newChildren);

  /**
   * A precedence level used to parenthesize when formatting the grammar
   * to a string.
   */
  @CheckReturnValue
  Precedence precedence();

  /**
   * The frequency with which the node consumes characters from
   * the {@link Parse#inp}.
   */
  @CheckReturnValue
  Frequency consumesInput(Language lang);

  /**
   * The code-points that could be the next character on the input if this
   * passes.
   * @return the empty set is a fine result if {@link #consumesInput}
   *     is {@link Frequency#NEVER} and the input does not lookahead.
   */
  @CheckReturnValue
  ImmutableRangeSet<Integer> lookahead(Language lang);

  /**
   * True if target can be reached from this without consuming input.
   */
  boolean reachesWithoutConsuming(Combinator target, Language lang);

  /**
   * The meta-data for this grammar node.
   */
  NodeMetadata getMetadata();
}
