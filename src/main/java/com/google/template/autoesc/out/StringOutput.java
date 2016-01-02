package com.google.template.autoesc.out;

import java.io.Closeable;
import java.io.IOException;

import com.google.common.base.Optional;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.RawCharsInputCursor;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;

/**
 * A chunk of matched text.
 * <p>
 * This must only be used for portions of the input that have been matched
 * and not artificially generated since, when rolling back the effect of a
 * failed branch, we {@link InputCursor#insertBefore push back} string outputs.
 */
public final class StringOutput extends UnaryOutput {
  /** The matched chars. */
  public final String s;
  /**
   * The encoded form of {@link #s}.
   * @see com.google.template.autoesc.Combinators#embed
   */
  public final String rawChars;

  /** */
  public StringOutput(String s, String rawChars) {
    this.s = s;
    this.rawChars = rawChars;
  }

  @Override
  public Optional<Output> coalesceWithFollower(Output next) {
    if (next instanceof StringOutput) {
      StringOutput nextStringOutput = (StringOutput) next;
      return Optional.<Output>of(
          new StringOutput(
              s + nextStringOutput.s,
              rawChars + nextStringOutput.rawChars));
    }
    return Optional.absent();
  }

  @Override
  public String toString() {
    return "\"" + RawCharsInputCursor.STRING_ESCAPER.escape(s) + "\"";
  }

  @Override
  protected String getVizTypeClassName() {
    return "string-chunk";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    try (Closeable code = out.open(TagName.CODE)) {
      out.text("\"" + RawCharsInputCursor.STRING_ESCAPER.escape(s) + "\"");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof StringOutput)) { return false; }
    StringOutput that = (StringOutput) o;
    return this.s.equals(that.s);
  }

  @Override
  public int hashCode() {
    return s.hashCode();
  }

  @Override
  public boolean isParseRelevant(PartialOutput po) {
    // Relevant if it follows an ephemeral marker.
    return true;
  }

  @Override
  protected int compareToSameClass(UnaryOutput that) {
    return this.rawChars.compareTo(((StringOutput) that).rawChars);
  }
}