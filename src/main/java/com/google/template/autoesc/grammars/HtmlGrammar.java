package com.google.template.autoesc.grammars;

import com.google.common.base.Optional;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.StringTransforms;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.var.Variable;


/** A grammar for HTML that embeds JS, CSS, and URL grammars. */
public final class HtmlGrammar extends GrammarFactory {
  private HtmlGrammar() {}

  /** The kind of the value of  */
  public static final Variable<AttrT> ATTR =
      Variable.create("attr", AttrT.class);

  /** A classification of attribute value content. */
  public enum AttrT {
    /** @see JsGrammar */
    SCRIPT,
    /** @see CssGrammar */
    STYLE,
    /** @see UrlGrammar */
    URL,
    /**
     * Content that does not obviously contain commands in an embedded
     * language.
     */
    OTHER,
    ;
  }

  /** Name of the start production for an HTML document fragment. */
  public static final ProdName N_HTML = new ProdName("Html");

  /** Name of the production for an HTML attribute. */
  public static final ProdName N_ATTRIB = new ProdName("Attrib");
  /**
   * Name of the production for an HTML attribute name.
   * This is a <i>qname</i> in XML jargon.
   */
  public static final ProdName N_ATTRIB_NAME = new ProdName("AttribName");
  /**
   * Name of the production for an HTML attribute value including any quotes.
   */
  public static final ProdName N_ATTRIB_VALUE = new ProdName("AttribValue");
  /**
   * Name of the production for a run of attributes in an open tag.
   */
  public static final ProdName N_ATTRIBS = new ProdName("Attribs");
  /**
   * Name of the production for an HTML comment or pseudo-comment.
   */
  public static final ProdName N_COMMENT = new ProdName("Comment");
  /**
   * Name of the production for an end tag.
   */
  public static final ProdName N_END_TAG = new ProdName("EndTag");
  /**
   * Name of the production for a run of characters that should match an
   * embedded grammar but which do not.
   */
  public static final ProdName N_MALFORMED = new ProdName("Malformed");
  /**
   * Name of the production for a run of characters that are treated as
   * raw character data.  In RCDATA, text is allowed that looks like HTML tags
   * or comments but they are treated as textual content.
   */
  public static final ProdName N_RC_DATA = new ProdName("RcData");
  /**
   * Name of the production for tags whose bodies require special handling.
   */
  public static final ProdName N_SPECIAL_TAG = new ProdName("SpecialTag");
  /**
   * Name of the production for regular
   * (not {@linkplain #N_SPECIAL_TAG special}) HTML tags.
   */
  public static final ProdName N_TAG = new ProdName("Tag");
  /**
   * Name of the production for a tag name.
   * This is a <i>qname</i> in XML jargon.
   */
  public static final ProdName N_TAG_NAME = new ProdName("TagName");


  private Language make() {
    final Language.Builder lang = new Language.Builder();
    String demoServerQuery;
    try {
      demoServerQuery = DemoServerQuery.builder()
          .grammarField(HtmlGrammar.class.getDeclaredField("LANG"))
          .build();
    } catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }
    lang.demoServerQuery(Optional.of(demoServerQuery));
    lang.defaultStartProdName(N_HTML);

    lang.define(N_HTML, star(ref("Element")));
    lang.define("Element", or(
        ref("TextChar"),
        ref(N_END_TAG),
        ref(N_SPECIAL_TAG),
        ref(N_TAG),
        ref(N_COMMENT),
        // This allows joined grammars to successfully commit to
        // re-entering the loop but without requiring more output.
        endOfInput()  // TODO: is this really necessary anymore?
        ))
        .unbounded();
    lang.define(N_SPECIAL_TAG, or(
        seq(
            litIgnCase("<script"),
            ref("TagTail"),
            until(
                or(
                    ref(JsGrammar.N_PROGRAM.withPrefix(Prefixes.JS_PREFIX)),
                    ref(N_MALFORMED)),
                closeTagPattern("script")
                )
            ),
        seq(
            litIgnCase("<style"),
            ref("TagTail"),
            until(
                or(
                    ref(CssGrammar.N_STYLE_SHEET
                        .withPrefix(Prefixes.CSS_PREFIX)),
                    ref(N_MALFORMED)),
                closeTagPattern("style"))
            ),
        seq(
            litIgnCase("<textarea"),
            ref("TagTail"),
            until(
                ref(N_RC_DATA),
                closeTagPattern("textarea"))
            ),
        seq(
            litIgnCase("<title"),
            ref("TagTail"),
            until(
                ref(N_RC_DATA),
                closeTagPattern("title"))
            )
        ));
    lang.define("TextChar", or(
        invChars('<'),
        seq(
            lit("<"),
            not(
                or(
                    chars('!', '?'),
                    seq(
                        opt(lit("/")),
                        chars(
                            UniRanges.union(
                                UniRanges.btw('A', 'Z'),
                                UniRanges.btw('a', 'z')))))
                )
            )
        ))
        .unbounded();
    lang.define(N_COMMENT, or(
        seq(lit("<!-"),
            plus(chars('-')),
            or(
                seq(
                    invChars('-', '>'),
                    star(
                        or(
                            seq(opt(chars('-')), chars('>')),
                            seq(star(chars('-')),
                                invChars('-', '>')))),
                    or(
                        seq(lit("-"), plus(lit("-")), lit(">")),
                        endOfInput())),
                chars('>'),
                endOfInput())),
        seq(lit("<"),
            chars('?', '!'),
            star(invChars('>')),
            or(lit(">"), endOfInput()))
        ));
    lang.define(N_END_TAG,
        seq(lit("</"),
            ref(N_TAG_NAME), opt(ref("Spaces")),
            or(lit(">"), endOfInput())));
    lang.define(N_TAG, seq(lit("<"), ref(N_TAG_NAME), ref("TagTail")));
    lang.define("TagTail", seq(
        ref("AttribsUnbounded"),
        opt(lit("/")),
        or(lit(">"), endOfInput())))
        .unbounded();
    lang.define(N_ATTRIBS,
        ref("AttribsUnbounded"));
    lang.define("AttribsUnbounded",
        opt(seq(
            opt(ref("Spaces")),
            star(seq(ref(N_ATTRIB), opt(ref("Spaces")))))))
        .unbounded();
    lang.define(N_RC_DATA, star(anyChar()));
    lang.define(N_TAG_NAME, seq(
        chars(UniRanges.union(
            UniRanges.btw('A', 'Z'),
            UniRanges.btw('a', 'z'))),
        star(
            chars(UniRanges.union(
                UniRanges.btw('A', 'Z'),
                UniRanges.btw('a', 'z'),
                UniRanges.btw('0', '9'),
                UniRanges.of(':', '-', '_'))))
        ));
    lang.define(N_ATTRIB,
        decl(
            ATTR,
            seq(
                ref(N_ATTRIB_NAME),
                opt(ref("Spaces")),
                opt(
                    seq(
                        lit("="),
                        opt(ref("Spaces")),
                        or(
                            ref(N_ATTRIB_VALUE),
                            endOfInput()
                            )
                ))
        )));
    lang.define(N_ATTRIB_NAME,
        or(
            seq(
                or(
                    litIgnCase("action"),
                    litIgnCase("cite"),
                    litIgnCase("codebase"),
                    litIgnCase("href"),
                    litIgnCase("longdesc"),
                    litIgnCase("src"),
                    litIgnCase("usemap")
                    // TODO is this complete?
                ),
                not(ref("AttribNameChar")),
                set(ATTR, AttrT.URL)),
            seq(
                litIgnCase("on"), star(ref("AttribNameChar")),
                set(ATTR, AttrT.SCRIPT)),
            seq(
                litIgnCase("style"), not(ref("AttribNameChar")),
                set(ATTR, AttrT.STYLE)),
            seq(
                plus(ref("AttribNameChar")),
                set(ATTR, AttrT.OTHER))));
    lang.define("AttribNameChar",
        invChars('\t', '\n', '\f', '\r', ' ', '>', '/', '"', '\'', '='))
        .unbounded();

    lang.define(N_ATTRIB_VALUE, or(
        seq(
            chars('"'),
            until(ref("AttribContent"), chars('"')),
            or(chars('"'), endOfInput())),
        seq(
            chars('\''),
            until(ref("AttribContent"), chars('\'')),
            or(chars('\''), endOfInput())),
        until(ref("AttribContent"),
              or(
                  chars('\t', '\n', '\f', '\r', ' '),
                  seq(opt(lit("/")), lit(">"))))
        ));

    lang.define("AttribContent", or (
            seq(in(ATTR, AttrT.SCRIPT),
                embed(
                    ref(JsGrammar.N_PROGRAM.withPrefix(Prefixes.JS_PREFIX)),
                    StringTransforms.HTML)),
            seq(in(ATTR, AttrT.STYLE),
                embed(
                    ref(CssGrammar.N_PROPS.withPrefix(Prefixes.CSS_PREFIX)),
                    StringTransforms.HTML)),
            seq(in(ATTR, AttrT.URL),
                embed(
                    ref(UrlGrammar.N_URL.withPrefix(Prefixes.URL_PREFIX)),
                    StringTransforms.HTML)),
            seq(in(ATTR, AttrT.OTHER), star(anyChar()))))
        .unbounded();

    lang.define("Spaces", plus(chars('\t', '\n', '\f', '\r', ' ')))
        .unbounded();

    lang.define("Break", not(chars(UniRanges.union(
        UniRanges.btw('A', 'Z'),
        UniRanges.btw('a', 'z'),
        UniRanges.btw('0', '9'),
        UniRanges.of(':', '-', '_')))))
        .unbounded();

    lang.define(N_MALFORMED, star(anyChar()));

    lang.include(Prefixes.JS_PREFIX, JsGrammar.LANG);
    lang.include(Prefixes.CSS_PREFIX, CssGrammar.LANG);
    lang.include(Prefixes.URL_PREFIX, UrlGrammar.LANG);

    return lang.build().reachableFrom(N_HTML);
  }

  /** A grammar for HTML that embeds JS, CSS, and URL grammars. */
  public static final Language LANG = new HtmlGrammar().make();

  /** An optimized version of {@link #LANG}. */
  public static final Language OPT_LANG = LANG.optimized();

  private final Combinator closeTagPattern(String tagName) {
    return closeTagPattern(this, tagName);
  }

  static final Combinator closeTagPattern(Combinators c, String tagName) {
    return c.seq(
        c.litIgnCase("</" + tagName),
        c.ref("Break"));
  }

}
