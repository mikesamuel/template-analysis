package com.google.template.autoesc.grammars;

import org.junit.Test;

import com.google.template.autoesc.Completion;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.GrammarTestCase;
import com.google.template.autoesc.grammars.CssGrammar.PropKindT;
import com.google.template.autoesc.inp.StringTransforms;


@SuppressWarnings("javadoc")
public final class HtmlGrammarTest extends AbstractGrammarTest {

  @Override
  protected GrammarTestCase.Builder makeTest() {
    return new GrammarTestCase.Builder(HtmlGrammar.OPT_LANG)
        .alsoRunOn(HtmlGrammar.LANG)
        // Make the tests less brittle to changes in the JS grammar by skipping
        // variable context changes.
        .filterOutputs(SKIP_JS_CONTEXT_VAR_OUTPUTS);
  }

  @Test
  public final void testHtmlCommentAgainstEmptyString() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public final void testHtmlCommentFailsOnInvalidInput() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("Hello")
        .expectEndState(Completion.FAILED)
        .run();
  }

  @Test
  public final void testHtmlCommentWithContent() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo -->")
        .expectOutput("<!-- Foo -->")
        .run();
  }

  @Test
  public final void testHtmlCommentWithEmbeddedDashes() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo--Bar -->")
        .expectOutput("<!-- Foo--Bar -->")
        .run();
  }

  @Test
  public final void testHtmlCommentWithEmbeddedDashGt() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo->Bar -->")
        .expectOutput("<!-- Foo->Bar -->")
        .run();
  }

  @Test
  public final void testHtmlCommentWithLongTail() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo --->")
        .expectOutput("<!-- Foo --->")
        .run();
  }

  @Test
  public final void testHtmlCommentTruncated1() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo --")
        .expectOutput("<!-- Foo --")
        .run();
  }

  @Test
  public final void testHtmlCommentTruncated2() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo -")
        .expectOutput("<!-- Foo -")
        .run();
  }

  @Test
  public final void testHtmlCommentTruncated3() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-- Foo ")
        .expectOutput("<!-- Foo ")
        .run();
  }

  @Test
  public final void testHtmlCommentMergedEnd1() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!-->")
        .expectOutput("<!-->")
        .run();
  }

  @Test
  public final void testHtmlCommentMergedEnd2() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!--->")
        .expectOutput("<!--->")
        .run();
  }

  @Test
  public final void testHtmlCommentMergedEnd3() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!---->")
        .expectOutput("<!---->")
        .run();
  }

  @Test
  public final void testHtmlCommentMergedEndTruncated() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!----")
        .expectOutput("<!----")
        .run();
  }

  @Test
  public final void testHtmlCommentPseudo1() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!doctype foo>")
        .expectOutput("<!doctype foo>")
        .run();
  }

  @Test
  public final void testHtmlCommentPseudo1Truncated() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<!doctype foo")
        .expectOutput("<!doctype foo")
        .run();
  }

  @Test
  public final void testHtmlCommentPseudo2() throws Exception {
    makeTest()
        .withStartProd("Comment")
        .withInput("<?doctype foo>")
        .expectOutput("<?doctype foo>")
        .run();
  }


  @Test
  public final void testHtmlTextAndTags() throws Exception {
    makeTest()
        .withStartProd("Html")
        .withInput("Hello, <b>World!</b>")
        .expectOutput(
              str("Hello, ")
            , lbnd("Tag")
            ,   str("<b>")
            , rbnd("Tag")
            , str("World!")
            , lbnd("EndTag")
            ,   str("</b>")
            , rbnd("EndTag")
            )
        .run();
  }

  @Test
  public final void testHtmlTextArea() throws Exception {
    Combinator limitCheckPattern = HtmlGrammar.closeTagPattern(C, "textarea");
    makeTest()
        .withStartProd("Html")
        .withInput("<textarea>foo</textarea>")
        .expectOutput(
              lbnd("SpecialTag")
            ,   str("<textarea>")
            ,   llimit(limitCheckPattern)
            ,     str("foo")
            ,   rlimit(limitCheckPattern)
            , rbnd("SpecialTag")
            , lbnd("EndTag")
            ,   str("</textarea>")
            , rbnd("EndTag")
        )
        .run();
  }

  @Test
  public final void testScriptElement() throws Exception {
    Combinator limitCheckPattern = HtmlGrammar.closeTagPattern(C, "script");
    makeTest()
        .withInput("<script>var s = 'string';</script>")
        .expectOutput(
              lbnd("SpecialTag")
            ,   str("<script>")
            ,    llimit(limitCheckPattern)
            ,      lbnd("Js.Program")
            ,        str("var s = ")
            ,        lbnd("Js.StringLiteral")
            ,          str("'string'")
            ,        rbnd("Js.StringLiteral")
            ,        str(";")
            ,      rbnd("Js.Program")
            ,    rlimit(limitCheckPattern)
            , rbnd("SpecialTag")
            , lbnd("EndTag")
            ,   str("</script>")
            , rbnd("EndTag")
            )
        .run();
  }

  @Test
  public final void testStyleAttribDq() throws Exception {
    Combinator dq = Combinators.get().chars('"');
    makeTest()
        .withInput("<span style=\"color: red\">")
        .expectOutput(
              lbnd("Tag")
            ,   str("<span ")
            ,   ldef(HtmlGrammar.ATTR)
            ,     str("style")
            ,     val(HtmlGrammar.AttrT.STYLE)
            ,     str("=\"")
            ,     llimit(dq)
            ,       lembed(StringTransforms.HTML)
            ,         lbnd("Css.Props")
            ,           ldef(CssGrammar.PROP_KIND)
            ,             lbnd("Css.Name")
            ,               str("color")
            ,               val(PropKindT.OTHER)
            ,             rbnd("Css.Name")
            ,             str(": ")
            ,             lbnd("Css.Value")
            ,               str("red")
            ,             rbnd("Css.Value")
            ,           rdef(CssGrammar.PROP_KIND)
            ,         rbnd("Css.Props")
            ,       rembed(StringTransforms.HTML)
            ,     rlimit(dq)
            ,     str("\"")
            ,   rdef(HtmlGrammar.ATTR)
            ,   str(">")
            , rbnd("Tag")
            )
        .run();
  }

  @Test
  public final void testForkAndJoinAroundTag() throws Exception {
    // HACK DEBUG
    //      --> "<b>W</b>" --
    //    /                   \
    // "H"                      --> "!"
    //    \                   /
    //      --> "C" ---------
    GrammarTestCase.Builder test = makeTest()
        .withInput("H")
        .expectOutput(str("H"));
    GrammarTestCase.BranchBuilder topBranch = test.fork()
        .withInput("<b>W</b>")
        .expectOutput(
              lbnd("Tag")
            ,   str("<b>")
            , rbnd("Tag")
            , str("W")
            , lbnd("EndTag")
            ,   str("</b>")
            , rbnd("EndTag")
            );
    GrammarTestCase.BranchBuilder bottomBranch = test.fork()
        .withInput("C")
        .expectOutput("C");
    topBranch.join(bottomBranch)
        .withInput("!")
        .expectOutput("!");
    test.run();
  }

}
