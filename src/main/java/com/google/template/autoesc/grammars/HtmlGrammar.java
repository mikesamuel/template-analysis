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


  private Language make() {
    final ProdName html = new ProdName("Html");
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
    lang.defaultStartProdName(html);

    lang.define(html, star(ref("Element")));
    lang.define("Element", or(
        ref("TextChar"),
        ref("EndTag"),
        ref("SpecialTag"),
        ref("Tag"),
        ref("Comment"),
        // This allows joined grammars to successfully commit to
        // re-entering the loop but without requiring more output.
        endOfInput()  // TODO: is this really necessary anymore?
        ))
        .unbounded();
    lang.define("SpecialTag", or(
        seq(
            litIgnCase("<script"),
            ref("TagTail"),
            until(
                or(
                    ref("Js.Program"),
                    ref("Malformed")),
                closeTagPattern("script")
                )
            ),
        seq(
            litIgnCase("<style"),
            ref("TagTail"),
            until(
                or(
                    ref("Css.StyleSheet"),
                    ref("Malformed")),
                closeTagPattern("style"))
            ),
        seq(
            litIgnCase("<textarea"),
            ref("TagTail"),
            until(
                ref("RcData"),
                closeTagPattern("textarea"))
            ),
        seq(
            litIgnCase("<title"),
            ref("TagTail"),
            until(
                ref("RcData"),
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
    lang.define("Comment", or(
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
    lang.define("EndTag",
        seq(lit("</"),
            ref("TagName"), opt(ref("Spaces")),
            or(lit(">"), endOfInput())));
    lang.define("Tag", seq(lit("<"), ref("TagName"), ref("TagTail")));
    lang.define("TagTail", seq(
        opt(seq(
            opt(ref("Spaces")),
            star(seq(ref("Attrib"), opt(ref("Spaces")))))),
        opt(lit("/")),
        or(lit(">"), endOfInput())))
        .unbounded();
    lang.define("RcData", star(anyChar()));
    lang.define("TagName", seq(
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
    lang.define("Attrib",
        decl(
            ATTR,
            seq(
                ref ("AttribName"),
                opt(ref("Spaces")),
                opt(
                    seq(
                        lit("="),
                        opt(ref("Spaces")),
                        or(
                            ref("AttribValue"),
                            endOfInput()
                            )
                ))
        )));
    lang.define("AttribName",
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

    lang.define("AttribValue", or(
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
                embed(ref("Js.Program"), StringTransforms.HTML)),
            seq(in(ATTR, AttrT.STYLE),
                embed(ref("Css.Props"), StringTransforms.HTML)),
            seq(in(ATTR, AttrT.URL),
                embed(ref("Url.Url"), StringTransforms.HTML)),
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

    lang.define("Malformed", star(anyChar()));

    lang.include("Js", JsGrammar.LANG);
    lang.include("Css", CssGrammar.LANG);
    lang.include("Url", UrlGrammar.LANG);

    return lang.build().reachableFrom(html);
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
