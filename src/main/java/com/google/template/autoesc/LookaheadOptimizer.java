package com.google.template.autoesc;

import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.combimpl.LaOrCombinator;
import com.google.template.autoesc.combimpl.OrCombinator;
import com.google.template.autoesc.inp.UniRanges;

import java.util.Map;


final class LookaheadOptimizer {
  static Language optimize(Language lang) {
    Language.Builder optimized = new Language.Builder();
    optimized.defaultStartProdName(lang.defaultStartProdName);
    if (lang.demoServerQuery.isPresent()) {
      optimized.demoServerQuery(
          Optional.of(
              DemoServerQuery.builder(lang.demoServerQuery.get())
              .optimized().build()));
    }
    LookaheadLookinto lali = new LookaheadLookinto(lang);
    for (Map.Entry<ProdName, Combinator> e : lang.byName().entrySet()) {
      optimized.define(
          e.getKey(),
          insertLookaheads(e.getValue(), lali));
    }
    return optimized.build();
  }

  private static Combinator insertLookaheads(
      Combinator c, LookaheadLookinto lali) {
    ImmutableList<Combinator> children = c.children();
    int n = children.size();

    // Recurse to children.
    ImmutableList.Builder<Combinator> newChildrenBuilder = null;
    for (int i = 0; i < n; ++i) {
      Combinator child = children.get(i);
      Combinator newChild = insertLookaheads(child, lali);
      if (child != newChild && newChildrenBuilder == null) {
        newChildrenBuilder = ImmutableList.builder();
        newChildrenBuilder.addAll(children.subList(0, i));
      }
      if (newChildrenBuilder != null) {
        newChildrenBuilder.add(newChild);
      }
    }
    ImmutableList<Combinator> newChildren =
      newChildrenBuilder != null ? newChildrenBuilder.build() : children;
    Combinator cOpt = newChildren == children
        ? c
        : c.unfold(
            c.getMetadata(), Functions.<ProdName>identity(), newChildren);

    // Replace ORs with ORs that prune using lookahead at runtime.
    if (cOpt instanceof OrCombinator) {
      OrCombinator orC = (OrCombinator) cOpt;
      Optional<ImmutableRangeSet<Integer>> firstLa =
          consumedLa(orC.first, lali);
      Optional<ImmutableRangeSet<Integer>> secondLa =
          consumedLa(orC.second, lali);
      if (firstLa.isPresent() || secondLa.isPresent()) {
        return new LaOrCombinator(
            Suppliers.ofInstance(orC.getMetadata()),
            orC.first, firstLa, orC.second, secondLa);
      }
    }

    return cOpt;
  }

  private static Optional<ImmutableRangeSet<Integer>> consumedLa(
      Combinator c, LookaheadLookinto lali) {
    // If the branch reliably consumes a code-point, then we can prune
    // based on the code-point on the input.
    if (Frequency.ALWAYS == lali.consumesInput(c)) {
      ImmutableRangeSet<Integer> la = lali.lookahead(c).subRangeSet(
          UniRanges.ALL_CODEPOINTS_RANGE);
      if (!UniRanges.ALL_CODEPOINTS.equals(la)) {
        return Optional.of(la);
      }
    }
    return Optional.absent();
  }
}
