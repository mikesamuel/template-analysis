package com.google.template.autoesc.inp;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;

/**
 * A relationship between code-points and case-normalized versions of those
 * code-points.
 * <p>
 * This is not a full locale-sensitive Unicode case-folding scheme because
 * we are primarily concerned with case-insensitive keywords in programming
 * languages, not arbitrary human-language text.
 */
public enum CaseSensitivity {
  /** Ignore case for 7-bit ASCII letters.  Normalized [A-Z] to [a-z]. */
  IGNORE {

    @Override
    public int normalizeCodepoint(int cp) {
      if ('A' <= cp && cp <= 'Z') { return cp | 32; }
      return cp;
    }

    @Override
    public ImmutableRangeSet<Integer> enumerate(int cp) {
      if ('A' <= cp && cp <= 'Z' || 'a' <= cp && cp <= 'z') {
        return ImmutableRangeSet.<Integer>builder()
            .add(Range.singleton(cp & ~32))
            .add(Range.singleton(cp |  32))
            .build();
      }
      return ImmutableRangeSet.of(Range.singleton(cp));
    }
  },
  /** Case sensitive.  Normalizes each code-point to itself. */
  SENSITIVE {

    @Override
    public int normalizeCodepoint(int cp) {
      return cp;
    }

    @Override
    public ImmutableRangeSet<Integer> enumerate(int cp) {
      return ImmutableRangeSet.of(Range.singleton(cp));
    }
  },
  ;

  /**
   * A canonical code-point.
   *
   * @param cp a code-point.
   * @return the normalized code-point.
   */
  public abstract int normalizeCodepoint(int cp);

  /**
   * Applies {@link #normalizeCodepoint} to each code-point in the input to
   * return a canonical form of the input.
   */
  public final String normalizeCharSequence(CharSequence cs) {
    int n = cs.length();
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0, cp; i < n; i += Character.charCount(cp)) {
      cp = Character.codePointAt(cs, i);
      sb.appendCodePoint(normalizeCodepoint(cp));
    }
    return sb.toString();
  }

  /**
   * The reverse-relation from {@link #normalizeCodepoint}.
   * Since that's a canonicalizing function, this returns cp's
   * equivalence-class.
   *
   * @param cp a code-point.
   * @return The minimal set of x's such that
   *   {@code enumerate(cp).contains(normalizeCodepoint(x))}.
   */
  public abstract ImmutableRangeSet<Integer> enumerate(int cp);
}
