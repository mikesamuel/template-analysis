package com.google.template.autoesc.inp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.template.autoesc.inp.TransformedString.Builder;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;

import org.apache.commons.lang3.StringEscapeUtils;


/**
 * Some useful string transforms.
 */
public final class StringTransforms {
  private StringTransforms() {}

  static final Pattern HTML_CHAR_REFERENCE_PATTERN =
      Pattern.compile(
          "&(?:[a-zA-Z0-9\\-]+|#(?:[0-9]+|[xX][A-Fa-f0-9]+));?");

  /** Encoding and decoding via HTML character references. */
  public static final StringTransform HTML = new HtmlStringTransform();

  /** Percent encodes all code-points not listed as unreserved in RFC 3986. */
  public static final StringTransform URI_QUERY =
      new PercentStringTransform(true);

  /**
   * Percent encodes all code-points not listed as unreserved in RFC 3986
   * except {@code '/'}.
   */
  public static final StringTransform URI_PATH =
      new PercentStringTransform(false);


  static abstract class AbstractStringTransform implements StringTransform {

    abstract UncertainPosition findNextPossibleLongSequence(
        CharSequence s, int position);

    /** @return -1 to indicate not found. */
    abstract UncertainPosition findLongSequenceEnd(
        CharSequence s, int start, boolean isComplete);

    /** @return -1 to indicate not an encoding sequence. */
    abstract int decodeLongSequence(CharSequence s, int start, int end);

    /**
     * Decodes single characters not handled by {@link #decodeLongSequence}.
     * <p>
     * May be overridden to decode single characters to a value other than
     * themselves.
     */
    @SuppressWarnings("static-method")
    char decodeSingleChar(char ch) {
      return ch;
    }

    @Override
    public final void decode(
        CharSequence s, TransformedString.Builder out, boolean isComplete) {
      int n = s.length();
      int position = 0;
      while (position < n) {
        UncertainPosition startPos = findNextPossibleLongSequence(s, position);
        if (startPos == UnPosition.NEED_MORE_INPUT) { break; }
        int start = startPos.toInt();
        Preconditions.checkState(start <= n);
        if (start == -1) { start = n; }
        Preconditions.checkState(start >= position);
        while (position < start) {
          out.append(decodeSingleChar(s.charAt(position)), 1);
          ++position;
        }
        if (start == n) { break; }
        int cp = -1;
        int nCharsInOriginal = -1;
        UncertainPosition endPos = findLongSequenceEnd(s, start, isComplete);
        if (endPos == UnPosition.NEED_MORE_INPUT) { break; }
        int end = endPos.toInt();
        if (end > start) {
          nCharsInOriginal = end - start;
          cp = decodeLongSequence(s, start, end);
        }
        if (cp >= 0 && nCharsInOriginal > 0) {
          out.appendCodePoint(cp, nCharsInOriginal);
          position = end;
        } else {
          out.append(decodeSingleChar(s.charAt(position)), 1);
          ++position;
        }
      }
    }
  }


  interface UncertainPosition {
    int toInt();
  }


  static final class CertainPosition implements UncertainPosition {
    final int index;
    CertainPosition(int index) {
      this.index = index;
    }

    @Override
    public int toInt() {
      return index;
    }
  }


  enum UnPosition implements UncertainPosition {
    NONE,
    NEED_MORE_INPUT,
    ;

    @Override
    public int toInt() { return -1; }
  }


  static final class HtmlStringTransform extends AbstractStringTransform {

    @Override
    public void visualize(DetailLevel lvl, VizOutput out) throws IOException {
      out.text("html");
    }

    @Override
    public String toString() {
      return "HtmlStringTransform";
    }

    @Override
    public void encode(CharSequence s, TransformedString.Builder out) {
      for (int i = 0, n = s.length(), nch; i < n; i += nch) {
        int cp = Character.codePointAt(s, i);
        nch = Character.charCount(cp);
        String charReference;
        switch (cp) {
          case '&':  charReference = "&amp;"; break;
          case '<':  charReference = "&lt;"; break;
          case '>':  charReference = "&gt;"; break;
          case '"':  charReference = "&quot;"; break;
          case '\'': charReference = "&#22;"; break;
          case '\0': charReference = "&#0;"; break;
          default:
            out.appendCodePoint(cp, nch);
            continue;
        }
        out.append('&', nch);
        for (int j = 0, m = charReference.length(); j < m; ++j) {
          out.append(charReference.charAt(j), 0);
        }
        out.append(';', nch);
      }
    }

    @Override
    UncertainPosition findNextPossibleLongSequence(
        CharSequence s, int position) {
      for (int i = position, n = s.length(); i < n; ++i) {
        if (s.charAt(i) == '&') { return new CertainPosition(i); }
      }
      return UnPosition.NONE;
    }

    @Override
    UncertainPosition findLongSequenceEnd(
        CharSequence s, int start, boolean isComplete) {
      Matcher m = HTML_CHAR_REFERENCE_PATTERN.matcher(s)
          .region(start, s.length());
      boolean found = m.find();
      if (!isComplete && m.hitEnd()) {
        return UnPosition.NEED_MORE_INPUT;
      }
      if (found && m.start() == start) {
        return new CertainPosition(m.end());
      }
      return UnPosition.NONE;
    }

    @Override
    int decodeLongSequence(CharSequence s, int start, int end) {
      String decoded = StringEscapeUtils.unescapeHtml4(
          new StringBuilder(end - start).append(s, start, end).toString());
      int n = decoded.length();
      if (n != 0) {
        int cp = decoded.codePointAt(0);
        if (Character.charCount(cp) == n) {
          return cp;
        }
      }
      return -1;
    }
  }


  static final class PercentStringTransform extends AbstractStringTransform {
    final boolean plusMeansSpace;

    PercentStringTransform(boolean plusMeansSpace) {
      this.plusMeansSpace = plusMeansSpace;
    }

    @Override
    char decodeSingleChar(char ch) {
      return plusMeansSpace && ch == '+' ? '\u0020' : ch;
    }

    @Override
    public void encode(CharSequence s, Builder out) {
      ByteBuffer bbuf = null;

      for (int i = 0, n = s.length(), nch; i < n; i += nch) {
        int cp = Character.codePointAt(s, i);
        nch = Character.charCount(cp);
        boolean encode = (
            'A' <= cp && cp <= 'Z'
            || 'a' <= cp && cp <= 'z'
            || cp == '-' || cp == '.' || cp == '_' || cp == '~'
            );
        if (!encode) {
          out.appendCodePoint(cp, nch);
        } else {
          if (bbuf == null) { bbuf = ByteBuffer.allocate(4); }
          bbuf.clear();
          CharBuffer cb = CharBuffer.wrap(s, i, i + nch);
          CoderResult result = Charsets.UTF_8.newEncoder().encode(
              cb, bbuf, i + nch == n);
          if (!result.isError()) {
            int nBytes = bbuf.position();
            for (int j = 0; j < nBytes; ++j) {
              byte b = bbuf.get(j);
              out.append('%', j == 0 ? 1 : 0);
              out.append(HEX[(b >> 4) & 0xf], 0);
              out.append(HEX[b & 0xf], 0);
            }
          }
        }
      }
    }

    private static final char[] HEX = {
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    };

    @Override
    public void visualize(DetailLevel lvl, VizOutput out) throws IOException {
      out.text(plusMeansSpace ? "pctPlus" : "pct");
    }

    @Override
    UncertainPosition findNextPossibleLongSequence(
        CharSequence s, int position) {
      for (int i = position, n = s.length(); i < n; ++i) {
        char ch = s.charAt(i);
        if (ch == '%') {
          return new CertainPosition(i);
        }
      }
      return UnPosition.NONE;
    }

    @Override
    UncertainPosition findLongSequenceEnd(
        CharSequence s, int start, boolean isComplete) {
      char chStart = s.charAt(start);
      Preconditions.checkState(chStart == '%');
      int n = s.length();
      if (start + 3 > n) {
        return isComplete ? UnPosition.NONE : UnPosition.NEED_MORE_INPUT;
      }
      int decoded = maybeDecodeByteFromHex(s, start + 1);
      if (decoded < 0) { return UnPosition.NONE; }
      int nBytes = utf8ByteLength((byte) decoded);
      if (nBytes < 0) { return UnPosition.NONE; }
      int end = start + 3 * nBytes;
      if (end > n) {
        return UnPosition.NEED_MORE_INPUT;
      }
      return new CertainPosition(end);
    }

    static int maybeDecodeByteFromHex(CharSequence s, int position) {
      int n = s.length();
      if (position + 2 <= n) {
        char a = s.charAt(position);
        char b = s.charAt(position + 1);
        int decoded = (dechex(a) << 4) | dechex(b);
        if (decoded >= 0) { return decoded; }
      }
      return -1;
    }

    static int dechex(char ch) {
      if ('0' <= ch && ch <= '9') { return ch - '0'; }
      if ('A' <= ch && ch <= 'F') { return ch - 'A' + 10; }
      if ('a' <= ch && ch <= 'f') { return ch - 'a' + 10; }
      return -1;
    }

    @Override
    int decodeLongSequence(CharSequence s, int start, int end) {
      int nBytes = end - start / 3;
      ByteBuffer bbuf = ByteBuffer.allocate(nBytes);
      for (int i = 0; i < nBytes; ++i) {
        if (s.charAt(i * 3) != '%') { return -1; }
        int b = maybeDecodeByteFromHex(s, i * 3 + 1);
        if (b >= 0) {
          bbuf.put((byte) b);
        }
      }
      CharBuffer cbuf = CharBuffer.allocate(2);
      CoderResult result = Charsets.UTF_8.newDecoder().decode(bbuf, cbuf, true);
      if (result.isError()) {
        return -1;
      }
      cbuf.rewind();
      return Character.codePointAt(cbuf, 0);
    }

    static int utf8ByteLength(byte headerByte) {
      switch ((headerByte >>> 4) & 0xf) {
        default: return 1;
        case 0x8:
        case 0x9:
        case 0xa:
        case 0xb: return -1; // 0x10xx
        case 0xc:
        case 0xd: return 2;  // 0x110x
        case 0xe: return 3;  // 0x1110
        case 0xf:
          if ((headerByte & 0x08) == 0) {
            return 4;        // 0x11110
          } else {
            return -1;
          }
      }
    }
  }
}
