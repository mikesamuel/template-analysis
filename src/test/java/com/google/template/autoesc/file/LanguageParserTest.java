package com.google.template.autoesc.file;

import java.util.List;

import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.SilentLoggingWatcher;
import com.google.template.autoesc.grammars.CssGrammar;
import com.google.template.autoesc.grammars.HtmlGrammar;
import com.google.template.autoesc.grammars.HtmlGrammar.AttrT;
import com.google.template.autoesc.grammars.JsGrammar;
import com.google.template.autoesc.grammars.JsGrammar.SlashIsT;
import com.google.template.autoesc.grammars.UrlGrammar;
import com.google.template.autoesc.grammars.UrlGrammar.PathKindT;
import com.google.template.autoesc.grammars.UrlGrammar.ProtocolT;
import com.google.template.autoesc.grammars.CssGrammar.PropKindT;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class LanguageParserTest extends TestCase {

  private static final Source TEST_SOURCE = new Source(
      LanguageParserTest.class.getSimpleName(), 1);

  static Language.Builder parse(String s) {
    return parse(s, Types.EMPTY);
  }

  static Language.Builder parse(String s, Types types) {
    SilentLoggingWatcher pw = new SilentLoggingWatcher();
    LanguageParser p = new LanguageParser();
    p.setParseWatcher(pw);
    p.setTypes(types);
    p.parse(s, TEST_SOURCE);
    p.finishParse();
    assertTrue(formatMessages(p.getMessages()), p.isOk());
    return p.getBuilder();
  }

  static ImmutableList<Message> messagesFor(String s) {
    SilentLoggingWatcher pw = new SilentLoggingWatcher();
    LanguageParser p = new LanguageParser();
    p.setParseWatcher(pw);
    p.parse(s, TEST_SOURCE);
    p.finishParse();
    return p.getMessages();
  }

  static void assertEqualCombinators(Combinator c, String s) {
    Language lang = parse("P := " + s + ";").build();
    assertEquals(s, c, lang.get(new ProdName("P")));
  }

  @Test
  public static void testChar() {
    assertEqualCombinators(
        Combinators.get().chars('p'),
        "[p]");
  }

  @Test
  public static void testInvChar() {
    assertEqualCombinators(
        Combinators.get().invChars('p'),
        "[^p]");
  }

  @Test
  public static void testInvCharEscaped() {
    assertEqualCombinators(
        Combinators.get().invChars('\n'),
        "[^\\n]");
  }

  @Test
  public static void testCharRange() {
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.btw(0, ' ')),
        "[\\u0000- ]");
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.btw(0, ' ')),
        "[\\x00- ]");
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.btw('\n', '\r')),
        "[\\x0A-\\x0d]");
  }

  @Test
  public static void testCharRangeWithBracketedHex() {
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.btw(0, ' ')),
        "[\\U{0}-\\U{000020}]");
  }

  @Test
  public static void testMultipleChars() {
    assertEqualCombinators(
        Combinators.get().chars('^', '-', '\\', 'x'),
        "[\\^\\-\\\\x]");
    assertEqualCombinators(
        Combinators.get().chars(
            UniRanges.union(
                UniRanges.of('-'),
                UniRanges.btw('0', '9'),
                UniRanges.of(':'),
                UniRanges.btw('A', 'Z'),
                UniRanges.of('_'),
                UniRanges.btw('a', 'z'))),
        "[\\-0-9:A-Z_a-z]");
  }

  @Test
  public static void testEmptyCharSet() {
    assertEqualCombinators(
        Combinators.get().chars(),
        "[]");
    assertEqualCombinators(
        Combinators.get().invChars(),
        "[^]");
  }

  @Test
  public static void testCharRangeOutOfOrder() {
    List<Message> msgs = messagesFor("P := [z-a]");
    assertTrue(!msgs.isEmpty());
    assertEquals(
        "ERROR: LanguageParserTest:1: Range end-points out of order: 122 > 97",
        "" + msgs.get(0));
  }

  @Test
  public static void testChars() {
    assertEqualCombinators(
        Combinators.get().chars('^', '-', '\\', 'x'),
        "[\\^\\-\\\\x]");
  }

  @Test
  public static void testNamedCharSets() {
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.categories("L")),
        "[[:L:]]");
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.categories("Lu")),
        "[[:Lu:]]");
  }

  @Test
  public static void testLiteral() {
    assertEqualCombinators(
        Combinators.get().lit(""),
        "\"\"");
    assertEqualCombinators(
        Combinators.get().lit("foo"),
        "\"foo\"");
    assertEqualCombinators(
        Combinators.get().lit("\""),
        "\"\\\"\"");
    assertEqualCombinators(
        Combinators.get().lit("\\"),
        "\"\\\\\"");
    assertEqualCombinators(
        Combinators.get().lit("foo\nbar\rbaz"),
        "\"foo\\nbar\\rbaz\"");
  }

  @Test
  public static void testEscs() {
    assertEqualCombinators(
        Combinators.get().chars('\t'),
        "[\\t]");
    assertEqualCombinators(
        Combinators.get().chars('\b'),
        "[\\b]");
    assertEqualCombinators(
        Combinators.get().chars('\r'),
        "[\\r]");
    assertEqualCombinators(
        Combinators.get().chars('\n'),
        "[\\n]");
    assertEqualCombinators(
        Combinators.get().chars('\n'),
        "[\\n]");
    assertEqualCombinators(
        Combinators.get().chars('\f'),
        "[\\f]");
    assertEqualCombinators(
        Combinators.get().chars('\u000b'),
        "[\\v]");
  }

  @Test
  public static void testSupplementalCodepoints() {
    assertEqualCombinators(
        Combinators.get().chars(Character.toCodePoint('\ud801', '\udc02')),
        "[\ud801\udc02]");
  }

  @Test
  public static void testComments() {
    assertEqualCombinators(
        Combinators.get().chars('[', ']'),
        "// foo\n[\\[\\]]");
    assertEqualCombinators(
        Combinators.get().chars('[', ']'),
        "/* bar */[\\[\\]]");
    assertEqualCombinators(
        Combinators.get().chars('[', ']'),
        "/* bar*baz/ */[\\[\\]]");
    assertEqualCombinators(
        Combinators.get().chars('[', ']'),
        "/*** bar*baz/ **/[\\[\\]]");
  }

  @Test
  public static void testLineNumbering() {
    Language lang = parse("A := [a];\nB := [b];\nC := \"c\";\n").build();
    NodeMetadata aMetadata = lang.get(new ProdName("A")).getMetadata();
    NodeMetadata bMetadata = lang.get(new ProdName("B")).getMetadata();
    NodeMetadata cMetadata = lang.get(new ProdName("C")).getMetadata();
    assertEquals(
        new Source(TEST_SOURCE.source, 1),
        aMetadata.source);
    assertEquals(
        new Source(TEST_SOURCE.source, 2),
        bMetadata.source);
    assertEquals(
        new Source(TEST_SOURCE.source, 3),
        cMetadata.source);
    // TODO: test that line numbering for messages is correct.
  }

  @Test
  public static void testDocComments() {
    // TODO: test doc comments that precede parenthetical groups, and those
    // that precede productions.
  }

  @Test
  public static void testReference() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.seq(
            combinators.chars('x'),
            combinators.opt(
                combinators.ref(new ProdName("P")))),
        "[x] P?");
  }

  @Test
  public static void testBoundedDefinition() {
    Language lang = parse("<P> := [x]").build();
    assertEqualCombinators(
        lang.get(new ProdName("P")),
        "{<P>} [x] {</P>}");
  }

  @Test
  public static void testProdName() {
    Language lang = parse("Foo := [x];\nfoo_bar := [y];\na1 := [z];\n").build();
    assertTrue(lang.has(new ProdName("Foo")));
    assertTrue(lang.has(new ProdName("foo_bar")));
    assertTrue(lang.has(new ProdName("a1")));

    List<Message> messages = messagesFor("a-b := bad-prod-name");
    assertTrue(
        messages.toString(),
        Iterables.any(
            messages,
            new Predicate<Message>() {
              @Override
              public boolean apply(Message msg) {
                return msg.isError;
              }
            }));
  }

  @Test
  public static void testOr() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.or(
            combinators.anyChar(),
            combinators.endOfInput()),
        ". / $");
    assertEqualCombinators(
        combinators.or(
            combinators.anyChar(),
            combinators.endOfInput()),
        "./$");
  }

  @Test
  public static void testSeq() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.seq(
            combinators.anyChar(),
            combinators.endOfInput()),
        ". $");
  }

  @Test
  public static void testEmptySeq() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.seq(),
        "");
    assertEqualCombinators(
        combinators.seq(),
        " ");
    assertEqualCombinators(
        combinators.seq(),
        " /* Nothing here but comments. */ ");
    assertEqualCombinators(
        combinators.seq(),
        "()");
  }

  @Test
  public static void testPrecedence() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.or(
            combinators.seq(
                combinators.anyChar(),
                combinators.anyChar()),
            combinators.endOfInput()),
        ". . / $");
    assertEqualCombinators(
        combinators.seq(
            combinators.chars('a'),
            combinators.star(combinators.chars('b'))),
        "[a] [b]*");
  }

  @Test
  public static void testGrouping() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.seq(
            combinators.anyChar(),
            combinators.or(
                combinators.anyChar(),
                combinators.endOfInput())),
        ". (. / $)");
    assertEqualCombinators(
        combinators.star(
            combinators.seq(
                combinators.chars('a'),
                combinators.chars('b'))),
        "([a] [b])*");
  }

  @Test
  public static void testSuffixOps() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.star(combinators.chars('x')),
        "[x]*");
    assertEqualCombinators(
        combinators.plus(combinators.chars('x')),
        "[x]+");
    assertEqualCombinators(
        combinators.opt(combinators.chars('x')),
        "[x]?");
  }

  @Test
  public static void testLookaheads() {
    Combinators combinators = Combinators.get();
    assertEqualCombinators(
        combinators.la(combinators.chars('x')),
        "(?= [x])");
    assertEqualCombinators(
        combinators.not(combinators.chars('x')),
        "(?! [x])");
  }

  @Test
  public static void testComplexCharSet() {
    assertEqualCombinators(
        Combinators.get().chars(UniRanges.invert(UniRanges.union(
            UniRanges.btw(0, 0x20),
            UniRanges.btw(0x80, Character.MAX_CODE_POINT),
            UniRanges.btw('0', '9'),
            UniRanges.btw('A', 'Z'),
            UniRanges.btw('a', 'z'),
            UniRanges.of(
                '-', '_', ';', '(', ')', '{', '}',
                '"', '\'', '\\', ':', '/')))),
        "[!#-&*-,.<-@\\[\\]\\^`|~\u007f]");
  }

  private static String languageToString(Language lang) {
    return TextVizOutput.vizToString(lang, DetailLevel.SHORT);
  }

  @Test
  public static void testGrammarGrammarIsParseable() {
    parse(languageToString(GrammarGrammar.LANG)).build();
  }

  @Test
  public static void testHtmlGrammarIsParseable() {
    Types types = new Types.Builder()
        .define(AttrT.class)
        .build();
    parse(languageToString(HtmlGrammar.LANG), types)
        .include("Js", JsGrammar.LANG)
        .include("Css", CssGrammar.LANG)
        .include("Url", UrlGrammar.LANG)
        .build();
  }

  @Test
  public static void testUrlGrammarIsParseable() {
    Types types = new Types.Builder()
        .define(PathKindT.class)
        .define(ProtocolT.class)
        .build();
    parse(languageToString(UrlGrammar.LANG), types)
        .include("Js", JsGrammar.LANG)
        .build();
  }

  @Test
  public static void testCssGrammarIsParseable() {
    Types types = new Types.Builder()
        .define(PropKindT.class)
        .build();
    parse(languageToString(CssGrammar.LANG), types)
        .include("Js", JsGrammar.LANG)
        .include("Url", UrlGrammar.LANG)
        .build();
  }

  @Test
  public static void testJsGrammarIsParseable() {
    Types types = new Types.Builder()
        .define(SlashIsT.class)
        .build();
    parse(languageToString(JsGrammar.LANG), types)
        .build();
  }

  static String formatMessages(Iterable<? extends Message> msgs) {
    StringBuilder sb = new StringBuilder();
    for (Message msg : msgs) {
      if (sb.length() != 0) { sb.append('\n'); }
      sb.append(msg);
    }
    return sb.toString();
  }
}
