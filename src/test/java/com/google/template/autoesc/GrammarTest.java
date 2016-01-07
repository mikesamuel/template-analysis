package com.google.template.autoesc;

import javax.annotation.CheckReturnValue;

import org.junit.Test;

import com.google.template.autoesc.inp.StringTransforms;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.out.EmbedOutput;
import com.google.template.autoesc.out.LimitCheck;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.out.StringOutput;

import org.junit.Assert;

@SuppressWarnings({ "javadoc", "static-method" })
public final class GrammarTest {
  static final ProdName START_NAME = new ProdName("start");
  static final Combinators C = Combinators.get();

  static Language lang(Combinator c) {
    return new Language.Builder().define(START_NAME, c).unbounded().build();
  }

  @CheckReturnValue
  static GrammarTestCase.Builder makeTest(Combinator c) {
    Language lang = lang(c);
    return new GrammarTestCase.Builder(lang)
        .alsoRunOn(LookaheadOptimizer.optimize(lang));
  }

  @Test
  public void testLiteralMatch() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("foo")
        .expectOutput("foo")
        .run();
  }

  @Test
  public void testLiteralMatchFails() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("bar")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testLiteralCaseSensitive() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("FOO")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testLiteralUndermatch() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("food")
        .expectOutput("foo")
        .expectUnparsedInput("d")
        .run();
  }

  @Test
  public void testLiteralTruncatedOutput() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("fo")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testLiteralSplitMatch() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("fo")
        .withInput("oo")
        .expectOutput("foo")
        .expectUnparsedInput("o")
        .run();
  }

  @Test
  public void testLiteralSplitMatchFailed() throws Exception {
    makeTest(C.lit("foo"))
        .withInput("fo")
        .withInput("dd")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testLiteralCaseInsensitive() throws Exception {
    makeTest(C.litIgnCase("foo"))
        .withInput("foo")
        .expectOutput("foo")
        .run();
    makeTest(C.litIgnCase("foo"))
        .withInput("FOO")
        .expectOutput("FOO")
        .run();
    makeTest(C.litIgnCase("foo_bar"))
        .withInput("FOO_BAR")
        .expectOutput("FOO_BAR")
        .run();
    makeTest(C.litIgnCase("foo_bar"))
        .withInput("FOO" + ((char) ('_' | 32)) + "BAR")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testCharSetRequiresInput() throws Exception {
    makeTest(C.chars('a', 'b', 'c'))
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testCharSetRequiresMatch() throws Exception {
    for (String s : new String[] { "a", "e", "B", "C", "D" }) {
      makeTest(C.chars('b', 'c', 'd'))
          .withInput(s)
          .expectEndState(Completion.FAILED)
          .run();
    }
  }

  @Test
  public void testCharSetPasses() throws Exception {
    for (String suffix : new String[] { "", "x" }) {
      for (String s : new String[] { "a", "b", "c" }) {
        makeTest(C.chars('a', 'b', 'c'))
            .withInput(s + suffix)
            .expectOutput(s)
            .expectUnparsedInput(suffix)
            .run();
      }
    }
  }

  @Test
  public void testInvCharSetRequiresMatch() throws Exception {
    for (String suffix : new String[] { "", "x" }) {
      for (String s : new String[] { "a", "e", "B", "C", "D" }) {
        makeTest(C.invChars('b', 'c', 'd'))
            .withInput(s + suffix)
            .expectOutput(s)
            .expectUnparsedInput(suffix)
            .run();
      }
    }
  }

  @Test
  public void testInvCharSetPasses() throws Exception {
    for (String s : new String[] { "a", "b", "c" }) {
      makeTest(C.invChars('a', 'b', 'c'))
          .withInput(s)
          .expectEndState(Completion.FAILED)
          .run();
    }
  }

  @Test
  public void testCharSetResumes() throws Exception {
    makeTest(C.chars('a', 'b', 'c'))
        .withInput("")
        .withInput("b")
        .expectOutput("b")
        .run();
  }

  @Test
  public void testCharSetSupplementalCodepoints() throws Exception {
    String surrogatePair = "\uD801\uDC37";
    int cp = Character.codePointAt(surrogatePair, 0);
    makeTest(C.chars(cp))
        .withInput(surrogatePair)
        .expectOutput(surrogatePair)
        .run();
    makeTest(C.invChars(cp))
        .withInput(surrogatePair)
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.chars(cp))
        .withInput("a")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.chars(cp))
        .withInput("\uD801")
        .withInput("\uDC37")
        .expectOutput(surrogatePair)
        .run();
    makeTest(C.chars(cp))
        .withInput("\uD801")
        .withInput("\uD801\uDC37")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.chars(cp))
        .withInput("\uD800")
        .withInput("\uDC37")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.chars(cp))
        .withInput("\uD800\uDC37")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.chars(cp))
        .withInput("\uD801\uDC38")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testEmpty() throws Exception {
    makeTest(Combinators.empty())
        .run();
    makeTest(Combinators.empty())
        .withInput("foo")
        .expectUnparsedInput("foo")
        .run();
  }

  @Test
  public void testError() throws Exception {
    makeTest(Combinators.error()).expectEndState(Completion.FAILED).run();
    makeTest(Combinators.error())
        .withInput("foo")
        .expectUnparsedInput("foo")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testOpt() throws Exception {
    makeTest(C.opt(C.lit("foo")))
        .run();
    makeTest(C.opt(C.lit("foo")))
        .withInput("foo")
        .expectOutput("foo")
        .run();
    makeTest(C.opt(C.lit("foo")))
        .withInput("bar")
        .expectUnparsedInput("bar")
        .run();
  }

  @Test
  public void testLoop() throws Exception {
    makeTest(C.plus(C.chars('a')))
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.plus(C.chars('a')))
        .withInput("b")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.plus(C.chars('a')))
        .withInput("aaaaa")
        .expectOutput("aaaaa")
        .run();
    makeTest(C.plus(C.chars('a', 'A')))
        .withInput("aAa")
        .withInput("AAa")
        .expectOutput("aAaAAa")
        .run();
    makeTest(C.plus(C.chars('a')))
        .withInput("aaaaab")
        .expectOutput("aaaaa")
        .expectUnparsedInput("b")
        .run();
  }

  @Test
  public void testLoopTerminates() throws Exception {
    makeTest(C.plus(C.opt(C.chars('a'))))
        .run();
    makeTest(C.plus(C.opt(C.chars('a'))))
        .withInput("aaa")
        .expectOutput("aaa")
        .run();
  }

  @Test
  public void testOr() throws Exception {
    Combinator alt = C.or(C.lit("foo"), C.lit("bar"), C.lit("baz"));
    for (String s : new String[] { "foo", "bar", "baz" }) {
      makeTest(alt).withInput(s).expectOutput(s).run();
    }
  }

  @Test
  public void testOrNoBacktracking() throws Exception {
    Combinator alt = C.or(C.lit("foo"), C.seq(C.chars('f', 'm'), C.lit("ood")));
    makeTest(alt)
        .withInput("food")
        .expectOutput("foo")
        .expectUnparsedInput("d")
        .run();
    makeTest(alt)
        .withInput("mood")
        .expectOutput("mood")
        .run();
    makeTest(alt)
        .withInput("wood")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testEmbed() throws Exception {
    for (String[] encodedFoo :
         new String[][] {
           { "foo" },
           { "f&#111;o" },
           { "fo&#x6F;" },
           { "&#x66;oo" },
           { "f&#x", "6f", ";o" },
         }) {
      StringBuilder rawChars = new StringBuilder();
      for (String rawPart : encodedFoo) {
        rawChars.append(rawPart);
      }
      makeTest(C.embed(C.lit("foo"), StringTransforms.HTML))
          .withInput(encodedFoo)
          .expectOutput(
              new EmbedOutput(Side.LEFT, StringTransforms.HTML),
              new StringOutput("foo", rawChars.toString()),
              new EmbedOutput(Side.RIGHT, StringTransforms.HTML))
          .run();
    }
    makeTest(C.embed(C.lit("foo"), StringTransforms.HTML))
        .withInput("bar")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(C.embed(C.lit("foo"), StringTransforms.HTML))
        .withInput("fo&#111;d")
        .expectUnparsedInput("d")
        .expectOutput(
            new EmbedOutput(Side.LEFT, StringTransforms.HTML),
            new StringOutput("foo", "fo&#111;"),
            new EmbedOutput(Side.RIGHT, StringTransforms.HTML))
        .run();
    makeTest(C.embed(C.lit("foo"), StringTransforms.HTML))
        .withInput("fo&#111;&#100;")
        .expectUnparsedInput("&#100;")
        .expectOutput(
            new EmbedOutput(Side.LEFT, StringTransforms.HTML),
            new StringOutput("foo", "fo&#111;"),
            new EmbedOutput(Side.RIGHT, StringTransforms.HTML))
        .run();
  }

  @Test
  public void testUntil() throws Exception {
    Combinator endPattern = C.seq(
        C.litIgnCase("</script"),
        C.not(C.chars(UniRanges.union(
            UniRanges.btw('a', 'z')))));
    Combinator limit = C.until(C.star(C.anyChar()), endPattern);
    makeTest(limit)
        .expectOutput(
            new LimitCheck(Side.LEFT, endPattern),
            new LimitCheck(Side.RIGHT, endPattern))
        .run();
    makeTest(limit)
        .withInput("aaa")
        .expectOutput(new LimitCheck(Side.LEFT, endPattern))
        .expectOutput("aaa")
        .expectOutput(new LimitCheck(Side.RIGHT, endPattern))
        .run();
    makeTest(limit)
        .withInput("aaa</script>foo")
        .expectOutput(new LimitCheck(Side.LEFT, endPattern))
        .expectOutput("aaa")
        .expectOutput(new LimitCheck(Side.RIGHT, endPattern))
        .expectUnparsedInput("</script>foo")
        .run();
    makeTest(limit)
        .withInput("aaa</script")
        .withInput("foo></script>bar")
        .expectOutput(new LimitCheck(Side.LEFT, endPattern))
        .expectOutput("aaa</scriptfoo>")
        .expectOutput(new LimitCheck(Side.RIGHT, endPattern))
        .expectUnparsedInput("</script>bar")
        .run();

    makeTest(C.seq(C.until(C.lit("foo"), endPattern), C.lit("</script>")))
        .withInput("foo</script>bar")
        .expectOutput(new LimitCheck(Side.LEFT, endPattern))
        .expectOutput("foo")
        .expectOutput(new LimitCheck(Side.RIGHT, endPattern))
        .expectOutput("</script>")
        .expectUnparsedInput("bar")
        .run();
    makeTest(C.seq(C.until(C.lit("foo"), endPattern), C.lit("</script>")))
        // The whole limited body must be matched.
        .withInput("food</script>bar")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testUntilConsumesEntireInput() throws Exception {
    makeTest(C.until(C.lit("foo"), C.endOfInput()))
        .withInput("foo")
        .expectOutput(new LimitCheck(Side.LEFT, C.endOfInput()))
        .expectOutput("foo")
        .expectOutput(new LimitCheck(Side.RIGHT, C.endOfInput()))
        .run();

    makeTest(C.until(C.lit("foo"), C.endOfInput()))
        .withInput("food")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public void testStarLoop() throws Exception {
    Combinator loop = C.star(C.invChars());
    makeTest(loop)
        .withInput("")
        .run();
    for (String s : new String[] { "a", "aa", "aaa", "ABC" }) {
      makeTest(loop)
          .withInput(s)
          .expectOutput(s)
          .run();
      makeTest(loop)
          .withInput(s)
          .withInput(s)
          .expectOutput(s + s)
          .run();
    }
  }

  @Test
  public void testLookahead() throws Exception {
    Combinator la = C.la(C.lit("foo"));
    makeTest(la)
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(la)
        .withInput("bar")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(la)
        .withInput("foo")
        .expectUnparsedInput("foo")
        .run();
  }

  @Test
  public void testNot() throws Exception {
    Combinator la = C.not(C.lit("foo"));
    makeTest(la)
        .run();
    makeTest(la)
        .withInput("foo")
        .expectEndState(Completion.FAILED)
        .run();
    makeTest(la)
        .withInput("bar")
        .expectUnparsedInput("bar")
        .run();
  }

  @Test
  public void testLitIgnCase() throws Exception {
    Combinator ignCaseOptions = C.or(
        C.litIgnCase("foo"),
        C.litIgnCase("bar"),
        C.litIgnCase("zebra"),
        C.litIgnCase("UGLY-fish"),
        C.litIgnCase("-1")
        );
    for (String s : new String[] {
        "foo", "FOO", "Foo", "foO",
        "bar", "BaR", "BAR",
        "Zebra", "zebra",
        "UGLY-fish", "ugly-fish",
        "-1",
        }) {
      makeTest(ignCaseOptions).withInput(s).expectOutput(s).run();
    }
    for (String s : new String[] {
        "fo", "FO",
        "RAB", "XYZ",
        "ebra", "ebrazay",
        "ugly\rfish",
        "\r1", "1", "",
        }) {
      makeTest(ignCaseOptions)
          .withInput(s).expectEndState(Completion.FAILED).run();
    }
  }

  @Test
  public void testLits() throws Exception {
    Combinator c = C.lits("foo", "in", "instanceof");
    for (String s : new String[] { "foo", "in", "instanceof" }) {
      makeTest(c).withInput(s).expectOutput(s).run();
    }
  }

  @Test
  public void testLitToString() {
    Assert.assertEquals("\"\"", C.lit("").toString());
    Assert.assertEquals("\u201Cfoo\u201D", C.lit("foo").toString());
    Assert.assertEquals("\u201Cfoo+bar\u201D", C.lit("foo+bar").toString());
    Assert.assertEquals(
        "\u201C\\u201Cfoo\\u201D\u201D",
        C.lit("\u201Cfoo\u201D").toString());
    Assert.assertEquals(
        "\u201Caa\u201D [ab] \u201Cbb\u201D",
        C.seq(
            C.chars('a'),
            C.chars('a'),
            C.chars('a', 'b'),
            C.chars('b'),
            C.chars('b')
            ).toString());
  }

}
