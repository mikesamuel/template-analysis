package com.google.template.autoesc.out;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.template.autoesc.FList;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.inp.InputCursor;


/**
 * A marker that is used by combinators to reason about what happened while
 * their bodies were operating and which should not appear in the final output.
 */
public abstract class EphemeralOutput extends UnaryOutput {
  EphemeralOutput() {
  }

  @Override
  public final boolean isParseRelevant(PartialOutput o) {
    return true;
  }

  @Override
  public final Optional<Output> coalesceWithFollower(Output next) {
    return Optional.absent();
  }


  /**
   * A transform that rolls back the state to the most recent marker that
   * matches this.
   * <p>
   * Removes all following output items, and collects the chunks of consumed
   * input so that they can be prepended on the input.
   */
  public ParseDelta.IOTransform rollback() {
    return new ParseDelta.IOTransform() {
      private InputCursor transformedCursor;
      private FList<Output> transformedOutput;

      @Override
      public FList<Output> getTransformedOutput() {
        return transformedOutput;
      }

      @Override
      public InputCursor getTransformedCursor() {
        return transformedCursor;
      }

      @Override
      public void apply(InputCursor input, FList<Output> output) {
        FList<Output> out = output;
        FList<String> consumedStringChunks = FList.empty();
        int nConsumedChars = 0;
        while (!out.isEmpty()) {
          Output hd = out.hd();
          out = out.tl();
          if (EphemeralOutput.this.equals(hd)) {
            break;
          }
          if (hd instanceof StringOutput) {
            String consumedChars = ((StringOutput) hd).rawChars;
            consumedStringChunks = FList.cons(
                consumedChars, consumedStringChunks);
            nConsumedChars += consumedChars.length();
          }
        }
        StringBuilder consumed = new StringBuilder(nConsumedChars);
        for (String rawChars : consumedStringChunks) {
          consumed.append(rawChars);
        }
        this.transformedOutput = out;
        this.transformedCursor = input.insertBefore(consumed.toString());
      }

      @Override
      public String toString() {
        return "rollback(" + EphemeralOutput.this + ")";
      }
    };
  }

  /**
   * Removes the most recent occurrence of the marker usually because the
   * operation passed so the placeholder is no longer relevant.
   */
  public FList<Output> commit(FList<Output> ls) {
    return commit(ls, IGNORE_OUTPUT);
  }

  /**
   * Removes the most recent occurrence of the marker usually because the
   * operation passed so the placeholder is no longer relevant.
   * @param observeOutput called on each output after the marker head first.
   */
  public FList<Output> commit(
      FList<Output> ls, Function<Output, Void> observeOutput) {
    FList<Output> uncommitted = ls;
    FList<Output> committed = FList.empty();
    while (!uncommitted.isEmpty()) {
      Output hd = uncommitted.hd();
      uncommitted = uncommitted.tl();
      if (this.equals(hd)) {
        // Push committed back onto committed coalescing around where the
        // marker was.
        FList<Output> reconstituted = uncommitted;
        if (!reconstituted.isEmpty() && !committed.isEmpty()) {
          Optional<Output> coalesced =
              reconstituted.hd().coalesceWithFollower(committed.hd());
          if (coalesced.isPresent()) {
            reconstituted = FList.cons(coalesced.get(), reconstituted.tl());
            committed = committed.tl();
          }
        }

        return reconstituted.appendRev(committed);
      }
      observeOutput.apply(hd);
      committed = FList.cons(hd, committed);
    }
    throw new IllegalStateException(this + " not on output");
  }

  private static final Function<Output, Void> IGNORE_OUTPUT =
      new Function<Output, Void>() {
    @Override
    public Void apply(Output o) { return null; }
  };

}
