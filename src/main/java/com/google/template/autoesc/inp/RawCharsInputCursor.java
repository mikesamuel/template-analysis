package com.google.template.autoesc.inp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A simple input cursor that just wraps a string.
 * The raw form is the same as the available chars.
 */
public final class RawCharsInputCursor
extends InputCursor implements CharSequence {
  private final String s;

  private final int position;
  private final int limit;
  /** True if no more input can be appended. */
  private final boolean isComplete;
  private final ImmutableRangeMap<Integer, Source> sources;

  /** An input with no characters that can be extended. */
  public static final RawCharsInputCursor EMPTY = new RawCharsInputCursor(
      "", 0, 0, false, ImmutableRangeMap.<Integer, Source>of());

  /**
   * @param s contains the available characters
   * @param position index in s of the first available character if any.
   * @param limit index in s just past the last available character.
   * @param isComplete true if the input can't be extended with more input.
   * @param source describes the origin of s for diagnostic messages.
   */
  public RawCharsInputCursor(
      String s, int position, int limit, boolean isComplete, Source source) {
    this(s, position, limit, isComplete,
         ImmutableRangeMap.of(Range.closedOpen(position, s.length()), source));
  }
  private RawCharsInputCursor(
        String s, int position, int limit,
        boolean isComplete, ImmutableRangeMap<Integer, Source> sources) {
    if (position > s.length()) { throw new IndexOutOfBoundsException(); }
    this.s = s;
    this.position = position;
    this.limit = limit;
    this.isComplete = isComplete;
    this.sources = sources;
  }

  @Override
  public CharSequence getAvailable() {
    return this;
  }

  @Override
  public int length() {
    return limit - position;
  }

  @Override
  public char charAt(int index) {
    if (index < 0) { throw new IndexOutOfBoundsException(); }
    return s.charAt(index + position);
  }

  @Override
  public RawCharsInputCursor subSequence(int start, int end) {
    if (start < 0 || end > limit) { throw new IllegalArgumentException(); }
    return new RawCharsInputCursor(
        s, position + start, position + end, isComplete, sources);
  }

  @Override
  public RawCharsInputCursor advance(int nChars) {
    if (nChars < 0 || nChars > length()) {
      throw new IndexOutOfBoundsException(
          "nChars=" + nChars + ", length=" + length());
    }
    return new RawCharsInputCursor(
        s, position + nChars, limit, isComplete, sources);
  }

  @Override
  public boolean isComplete() {
    return isComplete;
  }

  @Override
  public String toString() {
    return s.substring(position, limit);
  }

  @Override
  public RawCharsInputCursor extend(String rawChars, Source source) {
    if (isComplete) { throw new IllegalStateException(); }
    int nRawChars = rawChars.length();
    if (nRawChars == 0) { return this; }
    String sExt = new StringBuilder(limit - position + nRawChars)
        .append(s, position, limit).append(rawChars)
        .toString();
    int posExt = 0;
    int limExt = limit - position + nRawChars;
    ImmutableRangeMap.Builder<Integer, Source> sourcesExtB
        = ImmutableRangeMap.builder();
    for (Map.Entry<Range<Integer>, Source> e :
         sources.subRangeMap(Range.closedOpen(position, limit))
         .asMapOfRanges().entrySet()) {
      sourcesExtB.put(shift(e.getKey(), -position), e.getValue());
    }
    sourcesExtB.put(Range.closedOpen(limit - position, limExt), source);
    return new RawCharsInputCursor(
        sExt, posExt, limExt, false, sourcesExtB.build());
  }

  @Override
  public RawCharsInputCursor insertBefore(String rawChars) {
    int nRawChars = rawChars.length();
    if (nRawChars == 0) { return this; }

    ImmutableRangeMap.Builder<Integer, Source> b
        = ImmutableRangeMap.builder();
    int shiftDelta = nRawChars - position;
    for (Map.Entry<Range<Integer>, Source> e :
      sources.subRangeMap(Range.closedOpen(position, limit))
      .asMapOfRanges().entrySet()) {
      b.put(shift(e.getKey(), shiftDelta), e.getValue());
    }
    b.put(Range.closedOpen(0, nRawChars), Source.UNKNOWN);

    return new RawCharsInputCursor(
        rawChars + s.substring(position, limit),
        0,
        limit - position + nRawChars,
        isComplete,
        b.build());
  }

  @Override
  public List<Source> sources(int nChars) {
    return ImmutableList.copyOf(
        sources.subRangeMap(Range.closedOpen(position, position + nChars))
            .asMapOfRanges()
            .values());
  }

  @Override
  public CharSequence getRawChars(int nChars) {
    return getAvailable().subSequence(0, nChars);
  }

  @Override
  public RawCharsInputCursor finish() {
    return isComplete
        ? this
        : new RawCharsInputCursor(s, position, limit, true, sources);
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text(
        "\"" + STRING_ESCAPER.escape(s.substring(position)) + "\"");
    if (isComplete) {
      out.text("\u23da");  // Ground to earth symbol
    }
  }

  @Override
  protected String getVizTypeClassName() {
    return "string-input";
  }

  private static Range<Integer> shift(Range<Integer> r, int delta) {
    if (r.isEmpty()) { return r; }
    boolean hasLower = r.hasLowerBound();
    boolean hasUpper = r.hasUpperBound();
    if (!hasLower) {
      if (!hasUpper) {
        return r;
      } else {
        return Range.upTo(r.upperEndpoint() + delta, r.upperBoundType());
      }
    } else if (!hasUpper) {
      return Range.downTo(r.lowerEndpoint() + delta, r.lowerBoundType());
    }
    int lower = r.lowerEndpoint() + delta;
    int upper = r.upperEndpoint() + delta;
    switch (r.lowerBoundType()) {
      case OPEN:
        switch (r.upperBoundType()) {
          case OPEN:   return Range.open(lower, upper);
          case CLOSED: return Range.openClosed(lower, upper);
        }
        break;
      case CLOSED:
        switch (r.upperBoundType()) {
          case OPEN:   return Range.closedOpen(lower, upper);
          case CLOSED: return Range.closed(lower, upper);
        }
        break;
    }
    throw new AssertionError();
  }
  @Override
  public int hashCode() {
    return Objects.hashCode(s.substring(position, limit), length(), isComplete);
  }
  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof RawCharsInputCursor)) { return false; }
    RawCharsInputCursor that = (RawCharsInputCursor) o;
    int n = length();
    return n == that.length()
        && this.isComplete == that.isComplete
        && this.s.regionMatches(this.position, that.s, that.position, n);
  }


  /** C-style quoted string escaper. */
  public static final Escaper STRING_ESCAPER = Escapers.builder()
      .addEscape('\u0000', "\\u0000")
      .addEscape('\b', "\\b")
      .addEscape('\t', "\\t")
      .addEscape('\n', "\\n")
      .addEscape('\f', "\\f")
      .addEscape('\r', "\\r")
      .addEscape('"', "\\\"")
      .addEscape('\\', "\\\\")
      .addEscape('\u201C', "\\u201C")
      .addEscape('\u201D', "\\u201D")
      .addEscape('\u2028', "\\u2028")
      .addEscape('\u2029', "\\u2029")
      .build();
}
