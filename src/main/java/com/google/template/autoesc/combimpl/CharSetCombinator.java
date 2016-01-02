package com.google.template.autoesc.combimpl;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.AttribName;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A set of characters.
 */
public final class CharSetCombinator extends AtomicCombinator {
  /**
   * A set of code-points.
   */
  public final ImmutableRangeSet<Integer> codePoints;

  /** */
  public CharSetCombinator(
      Supplier<NodeMetadata> mds, RangeSet<Integer> codePoints) {
    super(mds);
    this.codePoints = normalize(codePoints);
  }

  /** */
  public CharSetCombinator(
      Supplier<NodeMetadata> mds, CharSetCombinator original) {
    super(mds);
    this.codePoints = original.codePoints;  // Skip re-normalize.
  }


  @Override
  protected AtomicCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer) {
    if (this.md.equals(newMetadata)) {
      return this;
    }
    return new CharSetCombinator(Suppliers.ofInstance(newMetadata), this);
  }


  /**
   * Passes when there is a whole code-point on the input, copies it to the
   * output, and consumes it.
   * <p>
   * Pauses if there is not enough input.
   */
  @Override
  public ParseDelta enter(Parse p) {
    CharSequence availableInput = p.inp.getAvailable();
    int availableInputLength = availableInput.length();

    if (availableInputLength == 0) {
      if (p.inp.isComplete()) {
        return ParseDelta.fail().build();
      } else {
        // If the input isn't complete, then pause until more input is
        // available or we know that we will see no more input.
        return pause();
      }
    }

    if (availableInputLength == 1) {
      char utf16CodeUnit = availableInput.charAt(0);
      if (Character.isHighSurrogate(utf16CodeUnit)) {
        // If there's no code-point in codePoints that encodes to a surrogate
        // pair that starts with utf16CodeUnit then fail early.
        int minCp = 0x10000 + ((utf16CodeUnit - 0xD800) << 10);
        int maxCp = minCp + (0xDFFF - 0xDC00);
        if (codePoints.subRangeSet(Range.closed(minCp, maxCp)).isEmpty()) {
          return ParseDelta.fail().build();
        } else {
          // Wait for a trailing surrogate.
          return pause();
        }
      }
    }

    // We have a whole code-point.
    int cp = Character.codePointAt(availableInput, 0);
    if (!codePoints.contains(cp)) {
      return ParseDelta.fail().build();
    }

    // Consume the character and append the matched code-point to the output.
    return ParseDelta.pass().advance(Character.charCount(cp)).build();
  }

  @Override
  public final ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(ParseDelta.fail().build());
      case EXIT_PASS:
      case EXIT_FAIL:
        break;  // We should never enter a charset.
    }
    throw new AssertionError(tt.name() + " : " + this);
  }

  static void charSetString(RangeSet<Integer> codePoints, VizOutput out)
  throws IOException {
    if (codePoints.equals(UniRanges.ALL_CODEPOINTS)) {
      out.text(".");
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    int posStart = sb.length();
    int posAsciiSep = rangesToStringBuilder(codePoints, sb);
    int negStart = sb.length();
    sb.append('^');
    int negAsciiSep = rangesToStringBuilder(UniRanges.invert(codePoints), sb);
    int negEnd = sb.length();
    int asciiSep;
    if (negEnd - negStart < negStart - posStart) {
      sb.replace(posStart, negStart, "");
      asciiSep = negAsciiSep >= 0 ? negAsciiSep - (negStart - posStart) : -1;
    } else {
      sb.replace(negStart, negEnd, "");
      asciiSep = posAsciiSep;
    }
    sb.append(']');

    if (asciiSep > 2 && sb.length() - asciiSep > 20) {
      // Allow the HTML to abbreviate the non-ascii portion of very large
      // charsets and show the details on hover.
      out.text(sb.substring(0, asciiSep));
      try (Closeable nonAscii = out.open(
          TagName.SPAN, AttribName.CLASS, "non-ascii-chars")) {
        out.text(sb.substring(asciiSep, sb.length() - 1));
      }
      assert sb.charAt(sb.length() - 1) == ']';
      out.text("]");
    } else {
      out.text(sb.toString());
    }
  }

  private static int rangesToStringBuilder(
      RangeSet<Integer> ranges, StringBuilder sb) {
    int asciiSep = -1;
    for (Range<Integer> r : ranges.asRanges()) {
      int lt = r.hasLowerBound() ? r.lowerEndpoint() : 0;
      int rt = r.hasUpperBound() ? r.upperEndpoint() : Character.MAX_CODE_POINT;
      if (!r.contains(lt)) { ++lt; }
      if (!r.contains(rt)) { --rt; }
      if (lt > rt) {
        continue;
      }

      if (asciiSep == -1 && lt >= 0x80) {
        asciiSep = sb.length();
      }

      appendRange(lt, rt, sb);
    }
    return asciiSep;
  }

  private static void appendRange(int lt, int rt, StringBuilder sb) {
    switch (rt - lt) {
      case 0:
        appendEndpoint(lt, sb);
        break;
      case 1:
        appendEndpoint(lt, sb);
        appendEndpoint(rt, sb);
        break;
      default:
        appendEndpoint(lt, sb);
        sb.append('-');
        appendEndpoint(rt, sb);
        break;
    }
  }

  private static void appendEndpoint(int cp, StringBuilder sb) {
    switch (cp) {
      case '[': case ']': case '^': case '-': case '\\': case '"':
        sb.append('\\');
        sb.appendCodePoint(cp);
        break;
      case '\0':
        sb.append("\\0");
        break;
      case '\t':
        sb.append("\\t");
        break;
      case '\n':
        sb.append("\\n");
        break;
      case '\f':
        sb.append("\\f");
        break;
      case '\r':
        sb.append("\\r");
        break;
      default:
        if (cp < 0x80) {
          sb.append((char) cp);
        } else if (cp < 0x10000) {
          sb.append("\\u");
          hex(cp, 4, sb);
        } else {
          sb.append("\\U{");
          hex(cp, 8, sb);
          sb.append("}");
        }
        break;
    }
  }

  private static final void hex(int n, int nDigits, StringBuilder sb) {
    for (int i = 0; i < nDigits; ++i) {
      int d = (n >>> ((nDigits - 1 - i) * 4)) & 0xf;
      sb.append("0123456789abcdef".charAt(d));
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof CharSetCombinator)) { return false; }
    CharSetCombinator that = (CharSetCombinator) o;
    return this.codePoints.equals(that.codePoints);
  }

  @Override
  public int hashCode() {
    return this.codePoints.hashCode();
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    charSetString(codePoints, out);
  }

  @Override
  protected String getVizTypeClassName() {
    return "charset";
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return Frequency.ALWAYS;
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return this.codePoints;
  }

  /**
   * Simplify things by making sure ranges are closed, non-empty, and disjoint.
   */
  private static final ImmutableRangeSet<Integer> normalize(
      RangeSet<Integer> ranges) {
    ImmutableRangeSet.Builder<Integer> b = ImmutableRangeSet.builder();
    int lastLt = 0, lastRt = -1;
    for (Range<Integer> r : ranges.asRanges()) {
      int lt = r.hasLowerBound() ? r.lowerEndpoint() : 0;
      int rt = r.hasUpperBound() ? r.upperEndpoint() : Character.MAX_CODE_POINT;
      if (!r.contains(lt)) { ++lt; }
      if (!r.contains(rt)) { --rt; }
      if (lt > rt) {
        // Test whether the range contains any code points.
        // Testing r.isEmpty() doesn't work since the open-range
        // (0..1) is only empty when you recognize that integers are
        // discrete.  Java Generics don't allow specializing
        // Range<Integer>.isEmpty() to take into account that some type
        // parameters are dense and some are discrete.
        continue;
      }
      if (lastLt <= lastRt) {
        if (lt - 1 <= lastRt) {  // Adjacent or overlapping
          lastLt = Math.min(lastLt, lt);
          lastRt = Math.max(lastRt, rt);
          continue;
        }
      }
      if (lastLt <= lastRt) {
        b.add(Range.closed(lastLt, lastRt));
      }
      lastLt = lt;
      lastRt = rt;
    }
    if (lastLt <= lastRt) {
      b.add(Range.closed(lastLt, lastRt));
    }
    return b.build();
  }
}
