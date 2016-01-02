package com.google.template.autoesc.out;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;


/**
 * Allows querying a stream of output events.
 */
public interface OutputContext {

  /**
   * Finds the last output element matching matches that is in the current
   * scope where scopes are defined by binary outputs matching isScope.
   */
  Optional<Output> lastInScope(
      Predicate<? super BinaryOutput> isScope,
      Predicate<? super Output> matches);


  /**
   * An output context implementation that maps an
   * {@link com.google.template.autoesc.FList}.
   */
  public static final class ListOutputContext implements OutputContext {
    private final Iterable<? extends Output> outputsInReverse;

    /**
     * @param outputsInReverse outputs most recent first.
     */
    public ListOutputContext(
        Iterable<? extends Output> outputsInReverse) {
      this.outputsInReverse = outputsInReverse;
    }

    /**
     * Finds the last output element matching matches that is in the current
     * scope where scopes are defined by binary outputs matching isScope.
     */
    @Override
    public Optional<Output> lastInScope(
        Predicate<? super BinaryOutput> isScope,
        Predicate<? super Output> matches) {
      int scopeDepth = 0;
      for (Output o : outputsInReverse) {
        if (o instanceof BinaryOutput) {
          BinaryOutput bo = (BinaryOutput) o;
          if (isScope.apply(bo)) {
            switch (bo.side) {
              case LEFT:
                --scopeDepth;
                if (scopeDepth < 0) {
                  return Optional.absent();
                }
                break;
              case RIGHT:
                ++scopeDepth;
                break;
            }
            continue;
          }
        }
        if (scopeDepth == 0 && matches.apply(o)) {
          return Optional.of(o);
        }
      }
      return Optional.absent();
    }

    @Override
    public String toString() {
      return ImmutableList.copyOf(this.outputsInReverse).reverse().toString();
    }
  }
}
