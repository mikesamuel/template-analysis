package com.google.template.autoesc.grammars;

import com.google.common.base.Optional;

import com.google.template.autoesc.Language;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.var.Variable;


/**
 * A lexical grammar for the Cascading Style Sheets language that attempts to
 * identify string, keyword, URL, and function boundaries..
 */
public final class CssGrammar extends GrammarFactory {
  private CssGrammar() {}

  /**
   * A property classification used to decide when a string literal is a
   * URL or not.
   */
  public static final Variable<PropKindT> PROP_KIND
      = Variable.create("PropKind", PropKindT.class);

  /** A kind of property. */
  public enum PropKindT {
    /** For properties <i>font-*</i>. */
    FONT,
    /** <tt>content</tt> */
    CONTENT,

    // TODO: Audit CSS3 for all uses of <string>

    /** For all other properties. */
    OTHER,
    ;
  }

  private Language make() {
    final Language.Builder lang = new Language.Builder();
    final ProdName props = new ProdName("Props");
    final ProdName styleSheet = new ProdName("StyleSheet");
    String demoServerQuery;
    try {
      demoServerQuery = DemoServerQuery.builder()
          .grammarField(CssGrammar.class.getDeclaredField("LANG"))
          .build();
    } catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }
    lang.demoServerQuery(Optional.of(demoServerQuery));
    lang.defaultStartProdName(props);

    lang.define(props, seq(
        ref("Ignorable"),
        star(seq(lit("l"), ref("Ignorable"))),
        opt(
            seq(
                ref("Prop"), ref("Ignorable"),
                star(
                    seq(
                        plus(seq(lit(";"), ref("Ignorable"))),
                        ref("Prop"), ref("Ignorable"))),
                star(seq(lit("l"), ref("Ignorable")))))
        ));
    lang.define(styleSheet, seq(
        ref("Ignorable"),
        star(
            seq(
                or(
                    ref("Block"),
                    ref("ValueToken")
                    ),
                ref("Ignorable")))
        ));
    lang.define("Block", seq(
        lit("{"),
        ref("Ignorable"),
        star(
            seq(
                or(
                    ref("Props"),
                    ref("Block"),
                    ref("ValueToken")
                    ),
                ref("Ignorable"))),
        lit("}")
        ))
        .unbounded();

    lang.define("Ignorable", star(
        or(
            ref("Spaces"),
            ref("Comment"))
        ))
        .unbounded();

    lang.define("Prop", decl(PROP_KIND, seq(
        ref("Name"),
        ref("Ignorable"),
        lit(":"),
        ref("Ignorable"),
        ref("Value")
        )))
        .unbounded();

    lang.define("Name", or(
        seq(litIgnCase("font"), set(PROP_KIND, PropKindT.FONT),
            opt(ref("IdentTail"))),
        seq(litIgnCase("content"), set(PROP_KIND, PropKindT.CONTENT),
            not(ref("IdentChar"))),
        seq(ref("Ident"),
            set(PROP_KIND, PropKindT.OTHER))
        ));

    lang.define("Ident", seq(
        or(
            seq(
                opt(chars('-')),
                or(
                    chars(UniRanges.union(
                        UniRanges.btw('A', 'Z'),
                        UniRanges.btw('a', 'z'),
                        UniRanges.of('_'),
                        UniRanges.btw(0x80, Character.MAX_CODE_POINT))),
                    ref("Escape"))),
            lit("--")),
        ref("IdentTail")
        ))
        .unbounded();
    lang.define("IdentTail", star(ref("IdentChar")))
        .unbounded();
    lang.define("IdentChar", or(
        chars(UniRanges.union(
            UniRanges.btw('A', 'Z'),
            UniRanges.btw('a', 'z'),
            UniRanges.btw('0', '9'),
            UniRanges.of('_'),
            UniRanges.btw(0x80, Character.MAX_CODE_POINT))),
        ref("Escape")
        ))
        .unbounded();

    lang.define("Value", opt(seq(
        ref("ValueToken"),
        star(seq(
            ref("Ignorable"),
            ref("ValueToken")))
        )));

    lang.define("ValueToken", or(
        seq(in(PROP_KIND, PropKindT.FONT, PropKindT.CONTENT),
            ref("StringToken")),
        ref("UrlToken"),
        ref("StringUrlToken"),
        ref("Quantity"),
        ref("IdentOrFn"),
        ref("HashToken"),
        ref("AtToken"),
        ref("Punctuation")
        ))
        .unbounded();

    // http://dev.w3.org/csswg/css-syntax/#token-diagrams
    lang.define("Comment", seq(
        lit("/*"), until(star(anyChar()), lit("*/")),
            or(
                seq(chars('*'), or(chars('/'), endOfInput())),
                endOfInput())
        ))
        .unbounded();

    lang.define("Spaces", plus(chars('\n', '\r', '\f', '\t', ' ')))
        .unbounded();

    lang.define("WS", or(ref("NL"), chars('\t', ' ')))
        .unbounded();
    lang.define("NL", or(lit("\r\n"), chars('\n', '\r', '\f')))
        .unbounded();

    lang.define("Hex", chars(UniRanges.union(
        UniRanges.btw('0', '9'),
        UniRanges.btw('A', 'F'),
        UniRanges.btw('a', 'f')
        )))
        .unbounded();

    lang.define("Escape", seq(
        chars('\\'),
        or(
            chars(UniRanges.invert(
                UniRanges.union(
                    UniRanges.btw('0', '9'),
                    UniRanges.btw('A', 'F'),
                    UniRanges.btw('a', 'f'),
                    UniRanges.of('\n', '\r', '\f')))),
            seq(plus(ref("Hex")), ref("WS")))
        ))
        .unbounded();

    lang.define("IdentOrFn", seq(
        ref("Ident"),
        opt(ref("CallParams"))
        ))
        .unbounded();

    lang.define("CallParams", seq(
        lit("("),
        ref("Ignorable"),
        ref("Value"),
        ref("Ignorable"),
        or(lit(")"), endOfInput())
        ));

    lang.define("AtToken", seq(
        lit("@"),
        ref("Ident")
        ))
        .unbounded();

    lang.define("HashToken", seq(
        lit("#"),
        ref("Ident")
        ))
        .unbounded();

    lang.define("Quantity", seq(
        ref("Number"),
        opt(
            or(
                seq(opt(ref("Spaces")), chars('%')),
                ref("Ident")))
        ))
        .unbounded();

    lang.define("StringToken", or(
        seq(chars('"'), star(or(chars('\''), ref("StringChar"))),
            or(chars('"'), endOfInput())),
        seq(chars('\''), star(or(chars('"'), ref("StringChar"))),
            or(chars('\''), endOfInput()))
        ));
    lang.define("StringChar", or(
        invChars('"', '\'', '\\', '\r', '\n', '\f'),
        ref("Escape"),
        seq(chars('\\'), ref("NL"))
        ))
        .unbounded();

    lang.define("UrlToken", seq(
        litIgnCase("url("),
        star(ref("WS")),
        or(
            ref("StringToken"),
            star(or(
                chars(UniRanges.invert(UniRanges.union(
                    UniRanges.of('"', '\'', '(', ')', '\\'),
                    UniRanges.btw(0, 0x20)
                    ))),
                ref("Escape")))),
        star(ref("WS")),
        or(lit(")"), endOfInput())
        ));

    lang.define("StringUrlToken", ref("StringToken"));

    lang.define("Number", seq(
        opt(chars('+', '-')),
        or(
            seq(plus(chars(UniRanges.btw('0', '9'))),
                opt(seq(chars('.'), chars(UniRanges.btw('0', '9'))))),
            seq(chars('.'), plus(chars(UniRanges.btw('0', '9'))))),
        opt(
            seq(
                chars('e', 'E'),
                opt(chars('+', '-')),
                plus(chars(UniRanges.btw('0', '9')))))
        ))
        .unbounded();

    lang.define("Punctuation", or(
        lits("~-", "|=", "^=", "$=", "*=", "||", "<!--", "-->"),
        seq(chars('/'), not(chars('*'))),
        chars(UniRanges.invert(UniRanges.union(
            UniRanges.btw(0, 0x20),
            UniRanges.btw(0x80, Character.MAX_CODE_POINT),
            UniRanges.btw('0', '9'),
            UniRanges.btw('A', 'Z'),
            UniRanges.btw('a', 'z'),
            UniRanges.of(
                '-', '_', ';', '(', ')', '{', '}',
                '"', '\'', '\\', ':', '/'))))
        ))
        .unbounded();

    return lang.build().reachableFrom(props, styleSheet);
  }

  /**
   * A lexical grammar for the Cascading Style Sheets language that attempts to
   * identify string, keyword, URL, and function boundaries..
   */
  public static final Language LANG = new CssGrammar().make();

  /** An optimized form of {@link #LANG} */
  public static final Language OPT_LANG = LANG.optimized();
}
