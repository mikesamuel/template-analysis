package com.google.template.autoesc.inp;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;


/**
 * Builders for ranges of code-points.
 */
public final class UniRanges {

  /** All code-points in [lt, rt]. */
  public static ImmutableRangeSet<Integer> btw(int lt, int rt) {
    return ImmutableRangeSet.<Integer>builder()
        .add(Range.closedOpen(lt, rt + 1))
        .build();
  }

  /** Just the given code-point. */
  public static ImmutableRangeSet<Integer> of(int cp) {
    return btw(cp, cp);
  }

  /** Just the given code-points. */
  public static ImmutableRangeSet<Integer> of(int... cps) {
    ImmutableRangeSet.Builder<Integer> b = ImmutableRangeSet.<Integer>builder();
    for (int cp : cps) {
      b.add(Range.singleton(cp));
    }
    return b.build();
  }

  /** The union of the given code-point sets. */
  @SafeVarargs
  public static ImmutableRangeSet<Integer> union(RangeSet<Integer>... rsets) {
    ImmutableRangeSet.Builder<Integer> b = ImmutableRangeSet.<Integer>builder();
    for (RangeSet<Integer> rset : rsets) {
      b.addAll(rset);
    }
    return b.build();
  }

  /** The union of the given code-point sets. */
  public static ImmutableRangeSet<Integer> union(
      Iterable<? extends RangeSet<Integer>> rsets) {
    ImmutableRangeSet.Builder<Integer> b = ImmutableRangeSet.<Integer>builder();
    for (RangeSet<Integer> rset : rsets) {
      b.addAll(rset);
    }
    return b.build();
  }

  /** The code-points in the given Unicode categories. */
  public static ImmutableRangeSet<Integer> categories(String... names) {
    // TODO: There's got to be a better way to query Java's unicode DB than
    // running a regex match over all possible code-points.
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (String category : names) {
      sb.append("\\p{").append(category).append("}");
    }
    sb.append("}]+");
    Pattern p = Pattern.compile(sb.toString());
    CharSequence ALL = new CharSequence() {

      @Override
      public int length() {
        // 1 char for each basic plane CP and 2 chars for each supplemental cp.
        return Character.MAX_CODE_POINT * 2 - 0x10000;
      }

      @Override
      public char charAt(int index) {
        if (index < 0x10000) {
          return (char) index;
        }
        boolean isLeading = index % 2 == 0;
        int cp = 0x10000 + ((index - 0x10000) / 2);
        return isLeading
            ? Character.highSurrogate(cp)
            : Character.lowSurrogate(cp);
      }

      @Override
      public CharSequence subSequence(int start, int end) {
        return new StringBuilder().append(this, start, end);
      }
    };

    ImmutableRangeSet.Builder<Integer> ranges = ImmutableRangeSet.builder();
    Matcher m = p.matcher(ALL);
    while (m.find()) {
      ranges.add(Range.closedOpen(
          Character.codePointAt(ALL, m.start()),
          Character.codePointBefore(ALL, m.end()) + 1));
    }

    return ranges.build();
  }

  /** All code-points except the given ones. */
  public static ImmutableRangeSet<Integer> invert(RangeSet<Integer> rs) {
    return ImmutableRangeSet.copyOf(
        rs.complement().subRangeSet(ALL_CODEPOINTS_RANGE));
  }


  /** All code-points. */
  public static final Range<Integer> ALL_CODEPOINTS_RANGE =
      Range.closed(0, Character.MAX_CODE_POINT)
      .canonical(CodepointsDomain.INSTANCE);

  /** All code-points in the basic-plane. */
  public static final Range<Integer> BASIC_PLANE_RANGE =
      Range.closed(0, 0xFFFF)
      .canonical(CodepointsDomain.INSTANCE);

  /** All code-points. */
  public static final ImmutableRangeSet<Integer> ALL_CODEPOINTS =
      ImmutableRangeSet.of(ALL_CODEPOINTS_RANGE);

  private UniRanges() {}


  /** A discrete domain for code-points. */
  public static final class CodepointsDomain extends DiscreteDomain<Integer>
  implements Serializable {

    private CodepointsDomain() {
      // Uninstantiable
    }

    /** Singleton */
    public static final CodepointsDomain INSTANCE = new CodepointsDomain();

    @Override
    public long distance(Integer start, Integer end) {
      return (long) end - start;
    }

    @Override
    public Integer next(Integer value) {
      int i = value;
      return i == Character.MAX_CODE_POINT ? null : i + 1;
    }

    @Override
    public Integer previous(Integer value) {
      int i = value;
      return i == 0 ? null : i - 1;
    }

    @Override
    public Integer minValue() {
      return 0;
    }

    @Override
    public Integer maxValue() {
      return Character.MAX_CODE_POINT;
    }

    @SuppressWarnings("static-method")
    private Object readResolve() {
      return INSTANCE;
    }

    @Override
    public String toString() {
      return "CodepointsDomain.INSTANCE";
    }

    private static final long serialVersionUID = 0;
  }
}
