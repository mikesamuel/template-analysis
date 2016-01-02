package com.google.template.autoesc.file;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.template.autoesc.inp.Source;

/**
 * Relates character indices in the parsed input to source/line.
 */
final class LineMap {
  /**
   * For each SourceLineMap in sources, the index in the combined output of
   * its start.
   */
  private final List<Integer> sourceBreakIndices = new ArrayList<>();
  private final List<SourceLineMap> sourceLineMaps = new ArrayList<>();


  void addInput(String rawChars, Source src) {
    int n = sourceBreakIndices.size();
    int start = n != 0 ? sourceBreakIndices.get(n - 1) : 0;
    int end = start + rawChars.length();
    sourceBreakIndices.add(end);
    sourceLineMaps.add(new SourceLineMap(rawChars, src));
  }

  Source atCharIndex(int pos) {
    int sourceIndex = Collections.binarySearch(sourceBreakIndices, pos);
    if (sourceIndex < 0) {
      sourceIndex = ~sourceIndex;
    }
    if (sourceIndex == sourceLineMaps.size()) {
      return Source.UNKNOWN;
    }
    SourceLineMap sourceLineMap = sourceLineMaps.get(sourceIndex);
    int sourceStart =
        sourceIndex == 0 ? 0 : sourceBreakIndices.get(sourceIndex - 1);
    int relPos = pos - sourceStart;
    int lineIndex = Arrays.binarySearch(sourceLineMap.lineBreaks, relPos);
    if (lineIndex < 0) {
      lineIndex = ~lineIndex;
    }
    if (lineIndex == 0) {
      return sourceLineMap.src;
    }
    return new Source(
        sourceLineMap.src.source, sourceLineMap.src.lineNum + lineIndex);
  }

}


final class SourceLineMap {
  final Source src;
  final int[] lineBreaks;

  SourceLineMap(String rawChars, Source src) {
    this.src = src;
    int nLineBreaks = 0;
    int n = rawChars.length();
    for (int i = 0; i < n; ++i) {
      if (isLineBreak(rawChars, i)) {
        ++nLineBreaks;
      }
    }
    this.lineBreaks = new int[nLineBreaks];
    int k = 0;
    for (int i = 0; i < n; ++i) {
      if (isLineBreak(rawChars, i)) {
        lineBreaks[k++] = i;
      }
    }
  }

  private static boolean isLineBreak(String s, int i) {
    char ch = s.charAt(i);
    return (
        ch == '\n'
        || ch == '\r' && (i + 1 == s.length() || s.charAt(i + 1) != '\n'));
  }
}
