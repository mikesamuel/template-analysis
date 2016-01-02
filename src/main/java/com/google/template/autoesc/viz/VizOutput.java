package com.google.template.autoesc.viz;

import java.io.Closeable;
import java.io.IOException;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Allow dumping objects to HTML at a variety of {@link DetailLevel}s.
 */
public abstract class VizOutput {
  /**
   * Opens a tag.
   * <p>
   * Tag structure is apparent from code structure when you use
   * <pre>
   * try (Closeable t = out.open(myTag)) {
   *   // visualize body of tag
   * }
   * </pre>
   */
  public abstract Closeable open(final Tag t) throws IOException;

  /** Opens a tag. */
  public final Closeable open(TagName nm) throws IOException {
    return open(tag(nm).build());
  }

  /** Opens a tag with an attribute. */
  public final Closeable open(TagName nm, AttribName a0, String v0)
  throws IOException {
    return open(tag(nm).attr(a0, v0).build());
  }

  /** Opens a tag with two attributes.  For more, use the builder. */
  public final Closeable open(TagName nm,
      AttribName a0, String v0, AttribName a1, String v1) throws IOException {
    return open(tag(nm).attr(a0, v0).attr(a1, v1).build());
  }

  /** Appends text to out. */
  public abstract void text(String s) throws IOException;

  /** A tag builder. */
  public static Tag.Builder tag(TagName name) {
    return new Tag.Builder(name);
  }

  private long idCounter;
  private final WeakHashMap<Linkable, String> idMap = new WeakHashMap<>();

  /**
   * An identifier that allows a short representation to link to the long
   * form elsewhere in the same document.
   */
  public String getIdFor(Linkable linkable) {
    Preconditions.checkArgument(linkable.shouldLinkTo());
    String id = idMap.get(linkable);
    if (id == null) {
      if (idCounter == Long.MAX_VALUE) {
        throw new ArithmeticException("Underflow");
      }
      id = "n" + idCounter;
      ++idCounter;
      idMap.put(linkable,  id);
    }
    return id;
  }


  /** An HTML-like tag. */
  public static final class Tag {
    /** The name of the tag. */
    public final TagName name;
    /** Maps attribute names to attribute values. */
    public final ImmutableMap<String, String> attribs;

    Tag(TagName name, ImmutableMap<String, String> attribs) {
      this.name = name;
      this.attribs = attribs;
    }


    /** An incremental builder for a tag. */
    public static final class Builder {
      private final TagName name;
      private final ImmutableMap.Builder<String, String> attribs;

      Builder(TagName name) {
        this.name = name;
        this.attribs = ImmutableMap.builder();
      }

      /** Add the attribute to the tag. */
      public Builder attr(AttribName attrName, String value) {
        attribs.put(attrName.name().toLowerCase(Locale.ROOT), value);
        return this;
      }

      /** Add a data-attr to the tag. */
      public Builder dataAttr(String nameSuffix, String value) {
        Preconditions.checkArgument(
            Pattern.compile("^[\\w:.\\-]+$").matcher(nameSuffix).matches());
        attribs.put("data-" + nameSuffix, value);
        return this;
      }

      /** Produce a tag with the attributes previously specified. */
      public Tag build() {
        return new Tag(name, attribs.build());
      }
    }
  }
}
