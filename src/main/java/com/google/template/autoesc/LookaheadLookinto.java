package com.google.template.autoesc;

import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.combimpl.LaOrCombinator;
import com.google.template.autoesc.combimpl.ReferenceCombinator;
import com.google.template.autoesc.inp.UniRanges;

import java.util.HashMap;
import java.util.Map;


/**
 * Enables {@link Combinator#lookahead} based branch prediction.
 * <p>
 * Used to optimize grammars by computing conservative lookaheads that can be
 * inserted into {@link LaOrCombinator OR branches} to prune branches that
 * cannot pass given the next code-point on the input.
 */
public final class LookaheadLookinto {
  final Language lang;
  private final Map<ProdName, Frequency> memoConsumesInputP = new HashMap<>();
  private final Map<ProdName, ImmutableRangeSet<Integer>> memoLookaheadP
    = new HashMap<>();
  private final Map<Combinator, Frequency> memoConsumesInputD
    = new HashMap<>();
  private final Map<Combinator, ImmutableRangeSet<Integer>> memoLookaheadD
    = new HashMap<>();

  LookaheadLookinto(Language lang) {
    this.lang = lang;
  }


  /**
   * Conservatively computes {@link Combinator#consumesInput}
   * for {@link ReferenceCombinator} referents.
   */
  public Frequency consumesInput(ProdName name) {
    Frequency f = memoConsumesInputP.get(name);
    if (f == null) {
      // Make a conservative assumption in case name is left-recursive.
      memoConsumesInputP.put(name, f = Frequency.SOMETIMES);
      if (lang.has(name)) {
        Combinator body = lang.get(name);
        f = consumesInput(body);
        memoConsumesInputP.put(name, f);
      }
    }
    return f;
  }

  /**
   * Conservatively computes {@link Combinator#lookahead}
   * for {@link ReferenceCombinator} referents.
   */
  public ImmutableRangeSet<Integer> lookahead(ProdName name) {
    ImmutableRangeSet<Integer> s = memoLookaheadP.get(name);
    if (s == null) {
      // Make a conservative assumption in case name is left-recursive.
      memoLookaheadP.put(name, s = UniRanges.ALL_CODEPOINTS);
      if (lang.has(name)) {
        Combinator body = lang.get(name);
        s = lookahead(body);
        memoLookaheadP.put(name, s);
      }
    }
    return s;
  }

  /**
   * Can be called to compute {@link Combinator#consumesInput} for children
   * since it memoizes.
   */
  public Frequency consumesInput(Combinator c) {
    Frequency f = memoConsumesInputD.get(c);
    if (f == null) {
      f = c.consumesInput(this.lang);
      memoConsumesInputD.put(c, f);
    }
    return f;
  }

  /**
   * Can be called to compute {@link Combinator#lookahead} for children
   * since it memoizes.
   */
  public ImmutableRangeSet<Integer> lookahead(Combinator c) {
    ImmutableRangeSet<Integer> s = memoLookaheadD.get(c);
    if (s == null) {
      s = c.lookahead(this.lang);
      memoLookaheadD.put(c, s);
    }
    return s;
  }
}
