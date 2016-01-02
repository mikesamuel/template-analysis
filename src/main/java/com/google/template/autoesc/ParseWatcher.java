package com.google.template.autoesc;

import com.google.template.autoesc.viz.Visualizable;

/**
 * Receives events as a parser manipulated the {@linkplain Parse parse state}.
 */
public interface ParseWatcher {
  /** Called when the parse starts with the initial state. */
  void started(Parse p);
  /**
   * Called as a grammar node is entered.
   * @param c the node entered.
   * @param p the state on entry.
   */
  void entered(Combinator c, Parse p);
  /**
   * Called as a grammar node is re-entered as the stack unwinds due to passing.
   * @param c the node re-entered.
   * @param p the state on re-entry.
   */
  void passed(Combinator c, Parse p);
  /**
   * Called as a grammar node is re-entered as the stack unwinds due to failure.
   * @param c the node re-entered.
   * @param p the state on re-entry.
   */
  void failed(Combinator c, Parse p);
  /**
   * Called when control enters a node which needs more input before entering
   * is appropriate and a whole code-point is not available on the input.
   * @param c the node paused.
   */
  void paused(Combinator c, Parse p);
  /**
   * Called to make more input available.
   */
  void inputAdded(Parse p);
  /**
   * Called when a parser is forked from a previous parser.
   * @param start name of the starting branch.
   * @param end name of the ending branch.
   */
  void forked(Parse p, Branch start, Branch end);
  /**
   * Called when a parser is being joined to a new parser.
   * <p>
   * This may be called multiple times and indicates that parser transitions
   * that occur after it are part of attempts to join parse states to find
   * an appropriate unique start state for the to branch.
   *
   * @param from the branch whose end joins to the start of the to branch.
   * @param to the branch that starts at the join point.
   * @see #joinFinished
   */
  void joinStarted(Branch from, Branch to);
  /**
   * Called when a parser has been completely joined to a new parser.
   * This event caps a sequence of pairwise joinStarted events.
   * This may be followed by more joining when more than two branches are
   * reconciled pairwise.
   *
   * @param p the end parse state of the from branch.
   * @param from the branch that was joined to to.
   * @param q the joined parse state which is the
   *     start parse state of the to branch.
   * @param to a branch starting where the joined branches end.
   * @see #joinStarted
   */
  void joinFinished(Parse p, Branch from, Parse q, Branch to);
  /**
   * Called when parsing finishes.
   *
   * @param endState the state in which the parse finished.
   */
  void finished(Parse p, Branch b, Completion endState);


  /**
   * Identifies a branch that runs from a
   * {@link Parser#fork} or {@link Parser#join} point to another such point.
   */
  public interface Branch extends Visualizable {
    @Override String toString();
    @Override boolean equals(Object o);
    @Override int hashCode();
  }
}
