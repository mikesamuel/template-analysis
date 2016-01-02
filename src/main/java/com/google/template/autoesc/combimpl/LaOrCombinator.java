package com.google.template.autoesc.combimpl;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.out.OutputContext;


/**
 * A version of {@link OrCombinator} that uses LA(1) to fail early on branches
 * that will eventually fail.
 */
public final class LaOrCombinator extends OrCombinator {
  private final Optional<ImmutableRangeSet<Integer>> firstLas;
  private final Optional<ImmutableRangeSet<Integer>> secondLas;

  /**
   * @param firstBranchLas
   *    If present, then the it must contain all code-points that
   *    could be first on the input if the first branch succeeds, and
   *    the first branch must not pass if the input is empty.
   * @param secondBranchLas
   *    If present, then the it must contain all code-points that
   *    could be second on the input if the second branch succeeds, and
   *    the second branch must not pass if the input is empty.
   */
  public LaOrCombinator(
      Supplier<NodeMetadata> mds,
      Combinator first,
      Optional<ImmutableRangeSet<Integer>> firstBranchLas,
      Combinator second,
      Optional<ImmutableRangeSet<Integer>> secondBranchLas) {
    super(mds, first, second);
    this.firstLas = firstBranchLas;
    this.secondLas = secondBranchLas;
  }

  @Override
  protected Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      Combinator newFirst, Combinator newSecond) {
    if (newMetadata.equals(md) && newFirst == first && newSecond == second) {
      return this;
    }
    return new LaOrCombinator(
        Suppliers.ofInstance(newMetadata),
        newFirst, firstLas, newSecond, secondLas);
  }

  @Override
  public ParseDelta enter(Parse p) {
    Combinator c = filterImpossible(p.inp);
    if (c != this) {
      return ParseDelta.builder(c).build();
    }
    return super.enter(p);
  }

  @Override public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        if (firstLas.isPresent()) {
          if (secondLas.isPresent()) {
            return ImmutableList.of(ParseDelta.fail().build());
          } else {
            return ImmutableList.of(ParseDelta.builder(second).build());
          }
        }
        break;
      case EXIT_PASS:
        break;
      case EXIT_FAIL:
        if (secondLas.isPresent()) {
          return ImmutableList.of(ParseDelta.fail().build());
        }
        break;
    }
    return super.epsilonTransition(tt, lang, ctx);
  }

  private Combinator filterImpossible(InputCursor inp) {
    int cp = -1;
    boolean canSkip;
    {
      CharSequence avail = inp.getAvailable();
      int len = avail.length();
      if (len == 0) {
        canSkip = inp.isComplete();
      } else {
        cp = Character.codePointAt(avail, 0);
        // Try to skip elements if we have a full code-point or we will
        // never see enough input to find one.
        canSkip = len > 1
            || Character.isHighSurrogate((char) cp) || inp.isComplete();
      }
    }
    if (!canSkip) {
      return this;
    }
    return filterImpossible(this, cp);
  }

  private static Combinator filterImpossible(
      Combinator c, Optional<ImmutableRangeSet<Integer>> las, int cp) {
    return
        las.isPresent() && !las.get().contains(cp)
        ? ErrorCombinator.INSTANCE
        : c instanceof LaOrCombinator
        ? filterImpossible((LaOrCombinator) c, cp)
        : c;
  }

  private static Combinator filterImpossible(LaOrCombinator c, int cp) {
    Combinator filteredFirst = filterImpossible(c.first, c.firstLas, cp);
    Combinator filteredSecond = filterImpossible(c.second, c.secondLas, cp);
    if (filteredFirst instanceof ErrorCombinator) {
      if (filteredSecond instanceof ErrorCombinator) {
        return ErrorCombinator.INSTANCE;
      } else {
        return filteredSecond;
      }
    } else if (filteredSecond instanceof ErrorCombinator) {
      return filteredFirst;
    } else {
      return c.unfold(
          c.getMetadata(), Functions.<ProdName>identity(),
          filteredFirst, filteredSecond);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) { return false; }
    LaOrCombinator that = (LaOrCombinator) o;
    return this.firstLas.equals(that.firstLas)
        && this.secondLas.equals(that.secondLas);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getClass(), first, firstLas, second, secondLas);
  }
}
