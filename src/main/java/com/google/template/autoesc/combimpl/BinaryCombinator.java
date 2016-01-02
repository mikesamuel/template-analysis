package com.google.template.autoesc.combimpl;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Precedence;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.Visualizable;
import com.google.template.autoesc.viz.VizOutput;

import static com.google.template.autoesc.viz.TagName.SPAN;
import static com.google.template.autoesc.viz.AttribName.CLASS;

/**
 * A combinator that delegates large chunks of its operation to its two child
 * combinators.
 */
public abstract class BinaryCombinator extends AbstractCombinator {
  /** The first child. */
  public final Combinator first;
  /** The second child. */
  public final Combinator second;

  BinaryCombinator(
      Supplier<NodeMetadata> mds, Combinator first, Combinator second) {
    super(mds);
    this.first = first;
    this.second = second;
  }

  @Override
  public final ImmutableList<Combinator> children() {
    return ImmutableList.of(first, second);
  }

  @Override
  public final Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      ImmutableList<Combinator> newChildren) {
    Preconditions.checkArgument(newChildren.size() == 2);
    Combinator newFirst = newChildren.get(0);
    Combinator newSecond = newChildren.get(1);
    return unfold(newMetadata, renamer, newFirst, newSecond);
  }

  protected abstract Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      Combinator newFirst, Combinator newSecond);

  protected static void writeBinaryOperator(
      Combinator first, Combinator second,
      final Precedence p, final Predicate<BinaryCombinator> shouldFlatten,
      final Infixer infixer, final DetailLevel lvl, final VizOutput out)
  throws IOException {
    class Writer {
      Optional<Combinator> prev = Optional.absent();

      void writeWholeOperator(Combinator a, Combinator b) throws IOException {
        writeOperator(Optional.<Combinator>absent(), a, b);
        infixer.finish(prev, out);
      }

      void writeOperator(
          Optional<Combinator> container,
          final Combinator a, final Combinator b)
      throws IOException {
        if (container.isPresent()) {
          AbstractVisualizable.visualize(
              container.get(), "nested", lvl, out,
              new Visualizable() {
                @Override
                public void visualize(DetailLevel ilvl, VizOutput iout)
                throws IOException {
                  // TODO Auto-generated method stub
                  writeOperator(Optional.<Combinator>absent(), a, b);
                }
              });
        } else {
          writeOperand(a);
          writeOperand(b);
        }
      }

      private void writeOperand(Combinator operand) throws IOException {
        if (operand instanceof BinaryCombinator
            && shouldFlatten.apply((BinaryCombinator) operand)) {
          BinaryCombinator nestedOperand = (BinaryCombinator) operand;
          Optional<Combinator> container = Optional.of(operand);
          writeOperator(container, nestedOperand.first, nestedOperand.second);
        } else {
          infixer.visualizeOperator(prev, operand, out);

          try (Closeable c = out.open(SPAN, CLASS, "operand")) {
            boolean wrap = p.compareTo(operand.precedence()) > 0;
            if (wrap) { out.text("("); }
            infixer.visualizeOperand(prev, operand, lvl, out);
            if (wrap) { out.text(")"); }
          }
          prev = Optional.of(operand);
        }
      }
    }

    (new Writer()).writeWholeOperator(first, second);
  }


  @Override
  public boolean equals(@Nullable Object o) {
    if (o == null || o.getClass() != getClass()) { return false; }
    BinaryCombinator that = (BinaryCombinator) o;
    return this.first.equals(that.first) && this.second.equals(that.second);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getClass(), first, second);
  }

  interface Infixer {
    void visualizeOperator(
        Optional<Combinator> prev, Combinator next, VizOutput out)
    throws IOException;
    void visualizeOperand(
        Optional<Combinator> prev, Combinator next, DetailLevel lvl,
        VizOutput out)
    throws IOException;
    void finish(Optional<Combinator> prev, VizOutput out) throws IOException;
  }
}
