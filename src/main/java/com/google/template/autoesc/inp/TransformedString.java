package com.google.template.autoesc.inp;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Objects;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A result of a {@link StringTransform} that maintains a mapping between input
 * character indices and indices in the output.
 */
public final class TransformedString
extends AbstractVisualizable implements CharSequence {
  /** The text of the transformed string. */
  public final String transformed;
  private final int[] transformedIndexToOriginal;

  /** Length = 0. */
  public static final TransformedString EMPTY =
      new TransformedString("", new int[] { 0 });

  private TransformedString(
      String transformed, int[] transformedIndexToOriginal) {
    this.transformed = transformed;
    this.transformedIndexToOriginal = transformedIndexToOriginal;
  }

  /** The index in the original corresponding to the given index in
   * {@link #transformed}.
   */
  public int indexInOriginal(int indexInTransformed) {
    return transformedIndexToOriginal[indexInTransformed];
  }


  /**
   * A builder for transformed strings.
   */
  public static final class Builder {
    private final StringBuilder chars = new StringBuilder();
    private final List<Integer> indexRel = new ArrayList<>();
    private int left;

    /** */
    public Builder() { this(EMPTY); }

    /** Appends will be concatenated to s. */
    @SuppressWarnings("synthetic-access")
    public Builder(TransformedString s) {
      chars.append(s.transformed);
      int nIndices = s.transformedIndexToOriginal.length;
      left = s.transformedIndexToOriginal[nIndices - 1];
      for (int i = 0; i < nIndices - 1; ++i) {
        indexRel.add(i);
      }
    }

    /**
     * @param nCharsInOriginal the number of characters in the input which
     *     correspond to the output char c.
     */
    public Builder append(char c, int nCharsInOriginal) {
      chars.append(c);
      indexRel.add(left);
      left += nCharsInOriginal;
      return this;
    }

    /**
     * @param nCharsInOriginal the number of characters in the input which
     *     correspond to the output code-point cp.
     */
    public Builder appendCodePoint(int cp, int nCharsInOriginal) {
      chars.appendCodePoint(cp);
      int nCharsInCp = Character.charCount(cp);
      for (int i = 0; i < nCharsInCp; ++i) {
        indexRel.add(left);
      }
      left += nCharsInOriginal;
      return this;
    }

    /**
     * The built result.
     */
    @SuppressWarnings("synthetic-access")
    public TransformedString build() {
      int nRel = indexRel.size();
      int[] transformedIndexToOriginal = new int[nRel + 1];
      for (int i = 0; i < nRel; ++i) {
        transformedIndexToOriginal[i] = indexRel.get(i);
      }
      transformedIndexToOriginal[nRel] = left;
      return new TransformedString(
          chars.toString(), transformedIndexToOriginal);
    }
  }

  @Override
  public int length() {
    return transformed.length();
  }

  @Override
  public char charAt(int index) {
    return transformed.charAt(index);
  }

  @Override
  public TransformedString subSequence(int start, int end) {
    int[] subArray = new int[end - start + 1];
    int tStart = transformedIndexToOriginal[start];
    for (int i = start; i <= end; ++i) {
      subArray[i - start] = this.transformedIndexToOriginal[i] - tStart;
    }
    return new TransformedString(transformed.substring(start, end), subArray);
  }

  /**
   * The subSequence from start to length().
   */
  public final TransformedString subSequence(int start) {
    return subSequence(start, length());
  }

  @Override
  public String toString() {
    return transformed;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TransformedString)) {
      return false;
    }
    TransformedString that = (TransformedString) o;
    return this.transformed.equals(that.transformed)
        && Arrays.equals(
            this.transformedIndexToOriginal,
            that.transformedIndexToOriginal);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        transformed,
        Arrays.hashCode(transformedIndexToOriginal));
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    try (Closeable c = out.open(TagName.CODE)) {
      out.text(
          "\""
          + RawCharsInputCursor.STRING_ESCAPER.escape(transformed)
          + "\"");
    }
  }

  @Override
  protected String getVizTypeClassName() {
    return "transformed-string";
  }
}
