package com.google.template.autoesc.grammars;

import com.google.common.base.Optional;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.var.Variable;


/**
 * An approximate lexical JS grammar.
 */
public final class JsGrammar extends GrammarFactory {

  private JsGrammar() {}

  /**
   * A good heuristic as to whether a '{@code /}' that does not start a
   * comment starts a regular expression or a division operator.
   */
  public static final Variable<SlashIsT> SLASH_IS =
      Variable.create("SlashIs", SlashIsT.class);

  /**
   * A good heuristic as to whether a '{@code /}' that does not start a
   * comment starts a regular expression or a division operator.
   */
  public enum SlashIsT {
    /** '{@code /}' starts a regex literal like {@code /foo/i}. */
    REGEX,
    /** '{@code /}' starts a division like {@code /} or {@code /=}. */
    DIV_OP,
    /**
     * Assume that the next non-comment, non-whitespace character is not
     * '{@code /}'.
     */
    DONT_CARE,
  }

  /** Name of a production that matches a run of JS token. */
  public static final ProdName N_PROGRAM = new ProdName("Program");

  /** Name of a production that matches a quoted string literal. */
  public static final ProdName N_STRING_LITERAL = new ProdName("StringLiteral");

  /** Name of a production that matches a {@code RegExp} literal. */
  public static final ProdName N_REGEX_LITERAL = new ProdName("RegexLiteral");

  private Language make() {
    final Language.Builder lang = new Language.Builder();

    String demoServerQuery;
    try {
      demoServerQuery = DemoServerQuery.builder()
          .grammarField(JsGrammar.class.getDeclaredField("LANG"))
          .build();
    } catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }
    lang.demoServerQuery(Optional.of(demoServerQuery));
    lang.defaultStartProdName(N_PROGRAM);

    lang.define(N_PROGRAM, decl(SLASH_IS, seq(
        set(SLASH_IS, SlashIsT.REGEX),
        star(ref("Token")),
        ref("Ignorable")
        )));

    lang.define("Ignorable", star(or(
        chars('\t', '\n', '\r', ' ', '\u2028', '\u2029'),
        seq(lit("//"), star(invChars('\n', '\r', '\u2028', '\u2029'))),
        seq(lit("/*"),
            // TODO: maybe match .* until "*/"
            seq(
                // The body is optional as in /**/
                star(
                    or(
                        chars('/'),
                        seq(star(chars('*')), invChars('*', '/')))),
                // Comments might not be closed but /* is still not a
                // division operator or regex start.
                or(
                    endOfInput(),
                    seq(plus(chars('*')), or(chars('/'), endOfInput())))))
        )))
        .unbounded();

    lang.define("Token", seq(
        // Maximize the chance of joining two parser branches by
        // treating any token not followed by a '/' as equivalent.
        opt(seq(
            set(SLASH_IS, SlashIsT.DONT_CARE),
            not(seq(ref("Ignorable"), chars('/'))))),
        ref("Ignorable"),
        or(
            seq(
                ref("RegexPreceder"),
                set(SLASH_IS, SlashIsT.REGEX)),
            seq(
                ref("DivOpPreceder"),
                set(SLASH_IS, SlashIsT.DIV_OP)),
            seq(
                in(SLASH_IS, SlashIsT.REGEX),
                ref(N_REGEX_LITERAL),
                set(SLASH_IS, SlashIsT.DIV_OP)),
            seq(
                in(SLASH_IS, SlashIsT.DIV_OP),
                ref("DivOp"),
                set(SLASH_IS, SlashIsT.REGEX)))
        ))
        .unbounded();

    lang.define(
        "RegexPreceder",
        or(
            lits(
                "!", "!=", "!==", "#", "%", "%=", "&", "&&", "&&=", "&=",
                "(", "*", "*=", "+", "+=", ",", "-", "-=", ".", "...", "/",
                "/=", ":", "::", ";", "<", "<<", "<<=", "<=", "=", "==",
                "===", ">", ">=", ">>", ">>=", ">>>", ">>>=", "?", "[", "^",
                "^=", "{", "|", "|=", "||", "||=", "~"),
            seq(
                lits(
                    "abstract", "break", "case", "catch", "class", "const",
                    "continue", "debugger", "default", "delete", "do",
                    "else", "enum", "export", "extends", "final", "finally",
                    "for", "function", "goto", "if", "implements", "import",
                    "in", "instanceof", "native", "new", "package",
                    "return", "static", "switch", "synchronized", "throw",
                    "throws", "transient", "try", "typeof", "var", "void",
                    "volatile", "while", "with"),
                not(ref("IdentifierPart")))
        ))
        .unbounded();

    lang.define("DivOpPreceder", or(
        ref("NumberLiteral"),
        ref(N_STRING_LITERAL),
        lits("}", ")", "]", "++", "--"),
        ref("IdentifierName")  // Includes keywords that can precede '/'
        ))
        .unbounded();

    lang.define("DivOp", seq(
        lits("/", "/=")
        ))
        .unbounded();

    lang.define(N_STRING_LITERAL, or(
        seq(
            chars('"'),
            star(or(ref("StringChar"), chars('\''))),
            or(chars('"'), endOfInput())),
        seq(
            chars('\''),
            star(or(ref("StringChar"), chars('"'))),
            or(chars('\''), endOfInput()))
        ));

    lang.define("StringChar", or(
        invChars('\\', '"', '\'', '\n', '\r', '\u2028', '\u2029'),
        seq(chars('\\'), or(anyChar(), endOfInput()))
        ))
        .unbounded();

    lang.define(N_REGEX_LITERAL, seq(
        chars('/'),
        not(chars('/', '*')),
        plus(or(ref("RegexChar"), ref("RegexCharSet"))),
        or(
            seq(chars('/'), ref("RegexFlags")),
            endOfInput())
        ));

    lang.define("RegexChar", or(
        invChars('\\', '/', '[', '\n', '\r', '\u2028', '\u2029'),
        seq(chars('\\'), or(anyChar(), endOfInput()))
        ))
        .unbounded();
    lang.define("RegexCharSet", seq(
        lit("["),
        star(
            or(
                invChars('\\', ']', '\n', '\r', '\u2028', '\u2029'),
                seq(chars('\\'), or(anyChar(), endOfInput())))),
        or(lit("]"), endOfInput())
        ))
        .unbounded();
    lang.define("RegexFlags", opt(ref("IdentifierName"))).unbounded();

    lang.define("IdentifierName", seq(
        ref("IdentifierStart"),
        star(ref("IdentifierPart"))
        ));

    lang.define("IdentifierStart", or(
        chars('_', '$'),
        chars(UniRanges.categories("L")),  // Unicode letters
        seq(lit("\\u"), ref("Hex"), ref("Hex"), ref("Hex"), ref("Hex"))
        ))
        .unbounded();

    lang.define("IdentifierPart", or(
        ref("IdentifierStart"),
        chars(UniRanges.union(
            // Digits, Combining Mark, Connector Punctuation
            UniRanges.categories("Nd", "Mc", "Pc"),
            UniRanges.of('\u200c', '\u200d'))) // ZWNJ ZWJ
        ))
        .unbounded();

    lang.define("Hex", chars(
        UniRanges.union(
            UniRanges.btw('A', 'F'),
            UniRanges.btw('a', 'f'),
            UniRanges.btw('0', '9'))
        ))
        .unbounded();

    lang.define("NumberLiteral", seq(
        or(
            seq(litIgnCase("0x"), plus(ref("Hex"))),
            seq(lit("0"), ref("FractionAndExp")),
            seq(chars(UniRanges.btw('1', '9')), star(ref("Dec")),
                opt(ref("FractionAndExp"))),
            seq(lit("."), plus(ref("Dec")), opt(ref("Exp")))),
        not(ref("IdentifierPart"))
        ));
    lang.define("Dec", chars(UniRanges.btw('0', '9')))
        .unbounded();
    lang.define("FractionAndExp", seq(
        lit("."),
        star(ref("Dec")),
        opt(ref("Exp"))
        ))
        .unbounded();
    lang.define("Exp", seq(
        chars('e', 'E'),
        opt(chars('+', '-')),
        plus(ref("Dec"))
        ))
        .unbounded();

    return lang.build().reachableFrom(N_PROGRAM);
  }

  /**
   * An approximate lexical JS grammar.
   */
  public static final Language LANG = new JsGrammar().make();

  /** An optimized version of {@link #LANG}. */
  public static final Language OPT_LANG = LANG.optimized();
}


