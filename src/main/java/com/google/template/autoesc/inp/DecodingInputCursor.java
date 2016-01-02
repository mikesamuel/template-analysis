package com.google.template.autoesc.inp;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Decodes an input to enable grammar embedding.
 */
public class DecodingInputCursor extends InputCursor {
  private InputCursor encoded;
  private TransformedString decoded;
  private final StringTransform xform;

  private static TransformedString decodeAvailable(
      InputCursor encoded, StringTransform xform) {
    TransformedString.Builder decodedSb =
        new TransformedString.Builder();
    CharSequence available = encoded.getAvailable();
    xform.decode(available, decodedSb, encoded.isComplete());
    return decodedSb.build();
  }

  /** */
  public DecodingInputCursor(InputCursor encoded, StringTransform xform) {
    this(encoded, xform, decodeAvailable(encoded, xform));
  }

  private DecodingInputCursor(
      InputCursor encoded, StringTransform xform,
      TransformedString decoded) {
    this.encoded = encoded;
    this.xform = xform;
    this.decoded = decoded;
  }

  @Override
  public boolean isComplete() {
    return encoded.isComplete()
        && encoded.getAvailable().length()
           == decoded.indexInOriginal(decoded.length());
  }

  @Override
  public CharSequence getAvailable() {
    return decoded;
  }

  @Override
  public InputCursor advance(int nChars) {
    int nCharsInEncoded = decoded.indexInOriginal(nChars);
    return new DecodingInputCursor(
        encoded.advance(nCharsInEncoded),
        xform, decoded.subSequence(nChars));
  }

  @Override
  public List<Source> sources(int nChars) {
    return encoded.sources(decoded.indexInOriginal(nChars));
  }

  @Override
  public InputCursor extend(String rawChars, Source src) {
    // We treat the extra bytes as raw.
    InputCursor extendedEncoded = encoded.extend(rawChars, src);
    CharSequence extendedAvailable = extendedEncoded.getAvailable();

    CharSequence undecodedSuffix = extendedAvailable.subSequence(
        decoded.indexInOriginal(decoded.length()), extendedAvailable.length());
    TransformedString.Builder allDecodedBuilder = new TransformedString.Builder(
        decoded);
    xform.decode(
        undecodedSuffix,
        allDecodedBuilder,
        false);
    TransformedString allDecoded = allDecodedBuilder.build();
    return new DecodingInputCursor(extendedEncoded, xform, allDecoded);
  }

  @Override
  public InputCursor insertBefore(String rawChars) {
    InputCursor prependedEncoded = encoded.insertBefore(rawChars);
    return new DecodingInputCursor(prependedEncoded, xform);
  }

  @Override
  public CharSequence getRawChars(int nChars) {
    return encoded.getRawChars(decoded.indexInOriginal(nChars));
  }

  /** The underlying input. */
  public InputCursor getUndecoded() {
    return encoded;
  }

  @Override
  public DecodingInputCursor finish() {
    // TODO: Tell the decoder that we can assume we've reached end of input.
    return new DecodingInputCursor(encoded.finish(), xform, decoded);
  }

  @Override
  protected String getVizTypeClassName() {
    return "decoding-input";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    encoded.visualize(lvl, out);
    out.text(" | ");
    xform.visualize(lvl, out);
    out.text(" -> ");
    decoded.visualize(lvl, out);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(xform, encoded);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof DecodingInputCursor)) { return false; }
    DecodingInputCursor that = (DecodingInputCursor) o;
    return this.xform.equals(that.xform) && this.encoded.equals(that.encoded);
  }
}
