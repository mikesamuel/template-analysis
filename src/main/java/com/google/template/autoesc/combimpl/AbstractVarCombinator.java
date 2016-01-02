package com.google.template.autoesc.combimpl;

import java.io.IOException;
import java.util.EnumSet;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.var.Value;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Passes when a variable has a value which is in a specified set.
 */
public abstract class AbstractVarCombinator<T> extends AtomicCombinator {
  /** The variable to test. */
  public final Variable<T> var;
  /** The set of values to relate to the actual value of var. */
  public final ImmutableSet<T> vals;

  /** */
  AbstractVarCombinator(
      Supplier<NodeMetadata> mds, Variable<T> var, ImmutableSet<T> vals) {
    super(mds);
    this.var = var;
    this.vals = vals;
  }

  @Override
  public final ParseDelta enter(Parse p) {
    Optional<T> val = Value.valueOf(var, p);
    if (!val.isPresent()) {
      throw new IllegalStateException("No value for " + var.name);
    } else if (checkValue(val.get())) {
      return ParseDelta.pass().build();
    } else {
      return ParseDelta.fail().build();
    }
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        // Use the value so we can predict whether or not we should pass or
        // fail.
        Optional<T> value = Value.valueOf(var, ctx);

        boolean canPass = true;
        boolean canFail = true;
        if (value.isPresent()) {
          if (checkValue(value.get())) {
            canFail = false;
          } else {
            canPass = false;
          }
        } else {
          throw new IllegalStateException(
              "Missing variable " + var + " in output context: "
              + getMetadata().source);
        }

        ImmutableList.Builder<ParseDelta> b = ImmutableList.builder();
        if (canFail) {
          b.add(ParseDelta.fail().build());
        }
        if (canPass) {
          b.add(ParseDelta.pass().build());
        }
        return b.build();
      case EXIT_FAIL: case EXIT_PASS:
        break;
    }
    throw new AssertionError(tt);
  }

  protected abstract boolean checkValue(T value);

  @Override
  public final boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) { return false; }
    AbstractVarCombinator<?> that = (AbstractVarCombinator<?>) o;
    return this.var.equals(that.var) && this.vals.equals(that.vals);
  }

  @Override
  public final int hashCode() {
    return Objects.hashCode(getClass(), var, vals);
  }

  @Override
  protected final void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    Optional<ImmutableSet<T>> invertedValuesOpt = getInvertedValues();
    if (invertedValuesOpt.isPresent()
        && vals.size() <= invertedValuesOpt.get().size()) {
      invertedValuesOpt = Optional.absent();
    }

    out.text("{if ");
    var.visualize(DetailLevel.TINY, out);  // Skip type annotation.
    out.text(getOperator(invertedValuesOpt.isPresent()));
    boolean isFirst = true;
    for (T val : invertedValuesOpt.or(vals)) {
      if (isFirst) {
        isFirst = false;
      } else {
        out.text(", ");
      }
      Value.visualizeValue(val, lvl, out);
    }
    out.text("}");
  }

  protected abstract String getOperator(boolean inverted);

  protected Optional<ImmutableSet<T>> getInvertedValues() {
    Class<?> enumClass = null;
    boolean isFirst = true;
    for (T val : vals) {
      if (val instanceof Enum<?>) {
        Class<?> valClass = val.getClass();
        if (isFirst) {
          enumClass = valClass;
        } else if (enumClass != null && !val.getClass().equals(enumClass)) {
          enumClass = null;
        }
      }
    }
    if (enumClass != null) {
      // Since vals is an immutable set, we know that enumClass is still the
      // concrete type of every element.
      // Since java.lang.Enum is abstract, we know that enumClass is not Enum.
      // Therefore, this cast is type-safe.
      @SuppressWarnings("unchecked")
      ImmutableSet<T> complement =
          immutableComplementOf(enumClass.asSubclass(Enum.class), vals);
      return Optional.of(complement);
    }
    return Optional.absent();
  }

  private static <E extends Enum<E>, T>
  ImmutableSet<E> immutableComplementOf(Class<E> clazz, ImmutableSet<T> vals) {
    EnumSet<E> valsAsEnums = EnumSet.noneOf(clazz);
    for (T x : vals) {
      valsAsEnums.add(clazz.cast(x));
    }
    return ImmutableSet.copyOf(EnumSet.complementOf(valsAsEnums));
  }


  @Override
  public final Frequency consumesInput(Language lang) {
    return Frequency.NEVER;
  }

  @Override
  public final ImmutableRangeSet<Integer> lookahead(Language lang) {
    return ImmutableRangeSet.of();
  }
}
