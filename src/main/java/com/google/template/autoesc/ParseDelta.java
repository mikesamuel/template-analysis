package com.google.template.autoesc;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.out.EphemeralOutput;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.StringOutput;


/**
 * A delta that can be applied to a {@link Parse} to derive the next state.
 */
public final class ParseDelta {
  /** A combinator to push onto the stack. */
  public final Combinator c;
  /** The input after d has matched a prefix. */
  public final IOTransform ioTransform;
  /** True to push the original onto the stack behind d. */
  public final boolean push;

  ParseDelta(Combinator c, IOTransform ioTransform, boolean push) {
    this.c = c;
    this.ioTransform = ioTransform;
    this.push = push;
  }

  @Override
  public String toString() {
    return "{ParseDelta d=" + c
        + (ioTransform instanceof IdentityIOTransform
           ? "" : ", ioXform=" + ioTransform)
        + (push ? ", push" : "")
        + "}";
  }


  /** A builder for a result that passes out of the current combinator. */
  public static Builder pass() {
    return builder(EmptyCombinator.INSTANCE);
  }

  /** A builder for a result that passes out of the current combinator. */
  public static Builder fail() {
    return builder(ErrorCombinator.INSTANCE);
  }

  /** A builder for a result that enters d with the given suffix. */
  public static Builder builder(Combinator c) {
    return new Builder(c);
  }

  /** {@code builder(d).build()} is equivalent to d. */
  public static Builder builder(ParseDelta d) {
    return new Builder(d);
  }


  /** Mutable builder for {@link ParseDelta}s. */
  public static final class Builder {
    private final Combinator c;
    private IOTransform ioTransform = new IdentityIOTransform();
    private boolean push;

    Builder(ParseDelta r) {
      this.c = r.c;
      this.ioTransform = r.ioTransform;
      this.push = r.push;
    }

    Builder(Combinator c) {
      this.c = c;
    }

    /**
     * Adds a transform that wraps or unwraps the input using the given f.
     */
    public Builder withInputTransform(Function<InputCursor, InputCursor> f) {
      return withIOTransform(new InputIOTransform(f));
    }

    /**
     * Adds a transform that appends the given output.
     * @param o an output to append.
     */
    public Builder withOutput(Output o) {
      return withOutputsRev(FList.cons(o, FList.<Output>empty()));
    }

    /**
     * Adds a transform that appends the given output if present.
     * @param o any output to append.
     */
    public Builder withOutput(Optional<Output> o) {
      if (o.isPresent()) {
        return withOutput(o.get());
      }
      return this;
    }

    /**
     * Adds a transforms that appends the given outputs.
     * @param os the outputs to append.  The head, if any, of os will be the
     *   head of the transformed output.
     * @return {@code this} per builder pattern.
     */
    public Builder withOutputs(FList<Output> os) {
      return withOutputsRev(os.rev());
    }

    /**
     * Adds a transform that appends the given outputs in reverse order.
     * @param os the outputs to append.  The head, if any, of os will
     *   immediately follow the head of the transformed output.
     * @return {@code this} per builder pattern.
     */
    public Builder withOutputsRev(FList<Output> os) {
      if (!os.isEmpty()) {
        return withIOTransform(new RevAppendOutputsIOTransform(os));
      }
      return this;
    }

    /**
     * Adds the given transform.
     * @param newIOTransform a transform to apply to the input and outputs.
     */
    public Builder withIOTransform(IOTransform newIOTransform) {
      this.ioTransform = CompositionIOTransform.of(
          this.ioTransform, newIOTransform);
      return this;
    }

    /**
     * Adds a transform that commits the given ephemeral output.
     *
     * @see EphemeralOutput#commit
     */
    public Builder commit(EphemeralOutput o) {
      return withIOTransform(new CommitIOTransform(o));
    }

    /**
     * Adds a transform that advances the cursor by n chars of available input.
     *
     * @see EphemeralOutput#rollback
     */
    public Builder advance(int nChars) {
      if (nChars != 0) {
        return withIOTransform(new AdvanceCursorIOTransform(nChars));
      }
      return this;
    }

    /**
     * Indicates the combinator should be pushed onto the stack to await the
     * exit of the variant.
     */
    public Builder push() {
      this.push = true;
      return this;
    }

    /** The built result. */
    public ParseDelta build() {
      return new ParseDelta(c, ioTransform, push);
    }
  }

  static Function<FList<Output>, FList<Output>> append(final Output output) {
    return new Function<FList<Output>, FList<Output>>() {
      @Override
      public FList<Output> apply(FList<Output> outputs) {
        return FList.cons(output, outputs);
      }

      @Override
      public String toString() {
        return "append(" + output + ")";
      }
    };
  }


  /**
   * Converts input and output.
   */
  public interface IOTransform {
    /** Derives a transformed input cursor and output from the initial IO. */
    void apply(InputCursor input, FList<Output> output);
    /** The transformed input cursor. */
    InputCursor getTransformedCursor();
    /** The transformed output. */
    FList<Output> getTransformedOutput();
    /** A string representation for debugging. */
    @Override String toString();
  }
}


final class IdentityIOTransform implements ParseDelta.IOTransform {
  private InputCursor transformedCursor;
  private FList<Output> transformedOutput;

  @Override
  public void apply(InputCursor input, FList<Output> output) {
    this.transformedCursor = input;
    this.transformedOutput = output;
  }

  @Override
  public InputCursor getTransformedCursor() {
    return transformedCursor;
  }

  @Override
  public FList<Output> getTransformedOutput() {
    return transformedOutput;
  }

  @Override
  public String toString() {
    return "identity";
  }
}


final class CompositionIOTransform implements ParseDelta.IOTransform {
  private final ParseDelta.IOTransform a, b;

  static ParseDelta.IOTransform of(
      ParseDelta.IOTransform a, ParseDelta.IOTransform b) {
    if (a instanceof IdentityIOTransform) { return b; }
    if (b instanceof IdentityIOTransform) { return a; }
    return new CompositionIOTransform(a, b);
  }

  CompositionIOTransform(
      ParseDelta.IOTransform a, ParseDelta.IOTransform b) {
    this.a = a;
    this.b = b;
  }

  @Override
  public void apply(InputCursor input, FList<Output> output) {
    a.apply(input, output);
    b.apply(a.getTransformedCursor(), a.getTransformedOutput());
  }

  @Override
  public InputCursor getTransformedCursor() {
    return b.getTransformedCursor();
  }

  @Override
  public FList<Output> getTransformedOutput() {
    return b.getTransformedOutput();
  }

  @Override
  public String toString() {
    return a + " o " + b;
  }
}


final class RevAppendOutputsIOTransform implements ParseDelta.IOTransform {
  final FList<Output> os;
  private InputCursor transformedCursor;
  private FList<Output> transformedOutput;

  RevAppendOutputsIOTransform(FList<Output> os) {
    this.os = os;
  }

  @Override
  public void apply(InputCursor input, FList<Output> output) {
    this.transformedCursor = input;
    this.transformedOutput = output;
    for (Output o : os) {
      this.transformedOutput = Parse.consOutput(o, this.transformedOutput);
    }
  }

  @Override
  public InputCursor getTransformedCursor() {
    return transformedCursor;
  }

  @Override
  public FList<Output> getTransformedOutput() {
    return transformedOutput;
  }

  @Override
  public String toString() {
    return "append(" + os + ")";
  }
}


final class CommitIOTransform implements ParseDelta.IOTransform {
  final EphemeralOutput o;
  private InputCursor transformedCursor;
  private FList<Output> transformedOutput;

  CommitIOTransform(EphemeralOutput o) {
    this.o = o;
  }

  @Override
  public void apply(InputCursor input, FList<Output> output) {
    this.transformedCursor = input;
    this.transformedOutput = o.commit(output);
  }

  @Override
  public InputCursor getTransformedCursor() {
    return transformedCursor;
  }

  @Override
  public FList<Output> getTransformedOutput() {
    return transformedOutput;
  }

  @Override
  public String toString() {
    return "commit(" + o + ")";
  }
}


final class AdvanceCursorIOTransform implements ParseDelta.IOTransform {
  final int nChars;
  private InputCursor transformedCursor;
  private FList<Output> transformedOutput;

  AdvanceCursorIOTransform(int nChars) {
    this.nChars = Preconditions.checkElementIndex(
        nChars, Integer.MAX_VALUE, "char count");
  }

  @Override
  public void apply(InputCursor input, FList<Output> output) {
    if (nChars == 0) {
      this.transformedCursor = input;
      this.transformedOutput = output;
    } else {
      StringOutput chars = new StringOutput(
          new StringBuilder(nChars)
          .append(input.getAvailable(), 0, nChars)
          .toString(),
          input.getRawChars(nChars).toString());
      this.transformedCursor = input.advance(nChars);
      this.transformedOutput = FList.cons(chars, output);
    }
  }

  @Override
  public InputCursor getTransformedCursor() {
    return transformedCursor;
  }

  @Override
  public FList<Output> getTransformedOutput() {
    return transformedOutput;
  }

  @Override
  public String toString() {
    return "advance(" + nChars + ")";
  }
}


final class InputIOTransform implements ParseDelta.IOTransform {
  final Function<InputCursor, InputCursor> f;
  private InputCursor transformedCursor;
  private FList<Output> transformedOutput;

  InputIOTransform(Function<InputCursor, InputCursor> f) {
    this.f = f;
  }

  @Override
  public void apply(InputCursor input, FList<Output> output) {
    this.transformedCursor = f.apply(input);
    this.transformedOutput = output;
  }

  @Override
  public InputCursor getTransformedCursor() {
    return this.transformedCursor;
  }

  @Override
  public FList<Output> getTransformedOutput() {
    return this.transformedOutput;
  }

  @Override
  public String toString() {
    return f.toString();
  }
}
