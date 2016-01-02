package com.google.template.autoesc.out;

import java.io.IOException;
import java.util.Arrays;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.FList;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;
import com.google.template.autoesc.viz.Visualizable;

/**
 * A complete output should have a forest structure.
 * An output being built might have lefts with no rights, so we
 * build a partial forest keeping track of whether a right is present or
 * still expected.
 */
public abstract class PartialOutput {
  private Optional<SpanningPartialOutput> parent = Optional.absent();
  /** The output most closely associated with this if any. */
  public abstract Optional<Output> getOutput();
  /** The right side output associated with this if any. */
  public abstract Optional<BinaryOutput> getRightSideOutput();
  /** Nested partial outputs in order. */
  public abstract ImmutableList<PartialOutput> getBody();

  PartialOutput() {}

  protected void setParent(SpanningPartialOutput parent) {
    Preconditions.checkState(!this.parent.isPresent());
    this.parent = Optional.of(parent);
  }

  /** A spanning output that contains this if any. */
  public final Optional<SpanningPartialOutput> getParent() {
    return parent;
  }

  /** An indented diagnostic string. */
  @Override
  public final String toString() {
    return toString(0);
  }

  /** An indented diagnostic string. */
  public final String toString(int indentLevel) {
    StringBuilder sb = new StringBuilder();
    appendToStringBuilder(indentLevel, sb);
    return sb.toString();
  }

  protected abstract void appendToStringBuilder(
      int indentLevel, StringBuilder sb);


  /**
   * A partial output that corresponds to a non-binary output that is complete
   * by itself.
   */
  public static final class StandalonePartialOutput extends PartialOutput {
    /** The output. */
    public final Output output;

    /** */
    public StandalonePartialOutput(Output output) {
      Preconditions.checkArgument(!(output instanceof BinaryOutput));
      this.output = output;
    }

    @Override
    public Optional<Output> getOutput() { return Optional.of(output); }

    @Override
    public Optional<BinaryOutput> getRightSideOutput() {
      return Optional.absent();
    }

    @Override
    public ImmutableList<PartialOutput> getBody() { return ImmutableList.of(); }

    @Override
    protected void appendToStringBuilder(int indentLevel, StringBuilder sb) {
      appendIndent(indentLevel, sb);
      appendVisualizable(output, sb);
      sb.append('\n');
    }
  }

  /** A partial output that encloses others. */
  public static abstract class SpanningPartialOutput extends PartialOutput {
    /** The contents that is spanned. */
    public final ImmutableList<PartialOutput> body;

    SpanningPartialOutput(ImmutableList<PartialOutput> body) {
      this.body = body;

      for (PartialOutput bodyPart : body) {
        bodyPart.setParent(this);
      }
    }

    @Override
    public final ImmutableList<PartialOutput> getBody() { return body; }
  }

  /**
   * An output with a left, a bunch of stuff in the middle and possibly a right.
   */
  public static final class BoundedRegion extends SpanningPartialOutput {
    /** */
    public final BinaryOutput left;
    /** */
    public final Optional<BinaryOutput> right;

    BoundedRegion(
        BinaryOutput left,
        ImmutableList<PartialOutput> body,
        Optional<BinaryOutput> right) {
      super(body);
      this.left = left;
      this.right = right;
    }

    @Override
    public Optional<Output> getOutput() { return Optional.<Output>of(left); }

    @Override
    public Optional<BinaryOutput> getRightSideOutput() { return right; }

    @Override
    protected void appendToStringBuilder(int indentLevel, StringBuilder sb) {
      appendIndent(indentLevel, sb);
      appendVisualizable(left, sb);
      sb.append('\n');
      for (PartialOutput po : body) {
        po.appendToStringBuilder(indentLevel + 1, sb);
      }
      if (right.isPresent()) {
        appendIndent(indentLevel, sb);
        appendVisualizable(right.get(), sb);
        sb.append('\n');
      }
    }
  }

  /** The roots of the forest. */
  public static final class Root extends SpanningPartialOutput {
    /** */
    public Root(ImmutableList<PartialOutput> body) {
      super(body);
    }

    @Override
    protected void setParent(SpanningPartialOutput r) {
      throw new IllegalArgumentException("Root cannot have parent");
    }

    @Override
    public Optional<Output> getOutput() { return Optional.absent(); }

    @Override
    public Optional<BinaryOutput> getRightSideOutput() {
      return Optional.absent();
    }

    @Override
    protected void appendToStringBuilder(int indentLevel, StringBuilder sb) {
      for (PartialOutput po : body) {
        po.appendToStringBuilder(indentLevel + 1, sb);
      }
    }
  }

  /**
   * Creates one from a reverse output list like that built on
   * {@link com.google.template.autoesc.Parse#out}
   */
  public static PartialOutput.Root of(FList<Output> outReverse) {
    ImmutableList<Output> outputsInOrder = outReverse.toReverseList();
    ImmutableList.Builder<PartialOutput> b = ImmutableList.builder();
    int[] ltOut = new int[1];  // receives updated index
    int rt = outputsInOrder.size();
    for (int lt = 0; lt < rt; lt = ltOut[0]) {
      appendBodyPartsUntil(
          outputsInOrder, lt, rt, Predicates.<Output>alwaysTrue(),
          b, ltOut);
    }
    return new Root(b.build());
  }

  private static void appendBodyPartsUntil(
      ImmutableList<Output> outputsInOrder, final int lt, final int rt,
      Predicate<Output> limitCondition,
      ImmutableList.Builder<PartialOutput> b, final int[] ltOut) {
    assert lt < rt;
    final Output o = outputsInOrder.get(lt);
    if (!limitCondition.apply(o)) {
      ltOut[0] = lt;
    } else if (o instanceof BinaryOutput) {
      final BinaryOutput bo = (BinaryOutput) o;
      if (bo.side != Side.LEFT) {
        throw new IllegalArgumentException("Orphaned right side " + bo);
      }
      ImmutableList.Builder<PartialOutput> subB = ImmutableList.builder();
      Optional<BinaryOutput> rightSideOpt = Optional.absent();
      int pos = lt + 1;
      while (pos < rt) {
        appendBodyPartsUntil(
            outputsInOrder, pos, rt,
            new Predicate<Output>() {
              @Override
              public boolean apply(Output x) {
                return !(x instanceof BinaryOutput
                    && bo.isOtherSide((BinaryOutput) x));
              }
            },
            subB,
            ltOut);
        int posBefore = pos;
        assert ltOut[0] >= pos;
        pos = ltOut[0];
        if (pos == rt) { break; }
        Output oNext = outputsInOrder.get(pos);
        if (oNext instanceof BinaryOutput
            && bo.isOtherSide((BinaryOutput) oNext)) {
          ++pos;
          rightSideOpt = Optional.of((BinaryOutput) oNext);
          break;
        }
        assert pos > posBefore;
      }
      b.add(new BoundedRegion(bo, subB.build(), rightSideOpt));
      ltOut[0] = pos;
      Preconditions.checkState(
          ltOut[0] > lt,
          new Object() {
            @Override public String toString() {
              return "lt=" + lt + ", ltOut=" + Arrays.toString(ltOut)
                  + ", rt=" + rt + ", o=" + o;
            }
          });
    } else {
      b.add(new StandalonePartialOutput(o));
      ltOut[0] = lt + 1;
    }
    assert lt <= ltOut[0] && ltOut[0] <= rt;
  }

  static void appendIndent(int n, StringBuilder sb) {
    char[] spaces = new char[n * 2];
    Arrays.fill(spaces, ' ');
    sb.append(spaces);
  }

  static void appendVisualizable(Visualizable v, StringBuilder sb) {
    TextVizOutput to = new TextVizOutput(sb);
    try {
      v.visualize(DetailLevel.LONG, to);
    } catch (IOException ex) {
      Throwables.propagate(ex);
    }
  }
}
