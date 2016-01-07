package com.google.template.autoesc.grammars;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.template.autoesc.GrammarTestCase;
import com.google.template.autoesc.grammars.UrlGrammar.PathKindT;
import com.google.template.autoesc.grammars.UrlGrammar.ProtocolT;
import com.google.template.autoesc.out.LimitCheck;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.var.Scope;

@SuppressWarnings("javadoc")
public final class UrlGrammarTest extends AbstractGrammarTest {

  public UrlGrammarTest() {
  }


  @Override
  protected GrammarTestCase.Builder makeTest() {
    return new GrammarTestCase.Builder(UrlGrammar.LANG)
        .alsoRunOn(UrlGrammar.OPT_LANG)
        .filterOutputs(NO_SCOPE_OUTPUTS);
  }

  @Test
  public void testAbsolute1() throws Exception {
    makeTest().withInput("http://www.google.com/")
        .expectOutput(
              lbnd("Url")
            ,   LFRAG
            ,     lbnd("Protocol")
            ,       str("http:")
            ,       val(ProtocolT.HTTP)
            ,     rbnd("Protocol")
            ,     str("//")
            ,     lbnd("Authority")
            ,       str("www.google.com")
            ,     rbnd("Authority")
            ,     val(PathKindT.ABSOLUTE)
            ,     lbnd("Path")
            ,       str("/")
            ,     rbnd("Path")
            ,   RFRAG
            , rbnd("Url")
            )
        .run();
  }

  @Test
  public void testProtocolRelative() throws Exception {
    makeTest().withInput("//www.google.com?q#a?b/c")
        .expectOutput(
              lbnd("Url")
            ,   LFRAG
            ,     val(ProtocolT._NONE)
            ,     str("//")
            ,     lbnd("Authority")
            ,       str("www.google.com")
            ,     rbnd("Authority")
            ,     val(PathKindT.ABSOLUTE)
            ,     lbnd("Query")
            ,       str("?q")
            ,     rbnd("Query")
            ,   RFRAG
            ,   lbnd("Fragment")
            ,     str("#a?b/c")
            ,   rbnd("Fragment")
            , rbnd("Url")
            )
        .run();
  }

  @Test
  public void testJavaScript1() throws Exception {
    makeTest().withInput("javascript://\u2028alert(42)//#foo")
        .filterOutputs(SKIP_JS_CONTEXT_VAR_OUTPUTS)
        .expectOutput(
              lbnd("Url"),
                LFRAG
            ,     lbnd("Protocol")
            ,       str("javascript:")
            ,       val(ProtocolT.JAVASCRIPT)
            ,     rbnd("Protocol")
            ,     val(PathKindT.OPAQUE)
            ,     lbnd("OpaqueBody")
            ,       lbnd("Js.Program")
            ,         str("//\u2028")
            ,         lbnd("Js.IdentifierName")
            ,           str("alert")
            ,         rbnd("Js.IdentifierName")
            ,         str("(")
            ,         lbnd("Js.NumberLiteral")
            ,           str("42")
            ,         rbnd("Js.NumberLiteral")
            ,         str(")//")
            ,       rbnd("Js.Program")
            ,     rbnd("OpaqueBody")
            ,   RFRAG
            ,   lbnd("Fragment")
            ,     str("#foo")
            ,   rbnd("Fragment")
            , rbnd("Url")
            )
        .run();
  }

  @Test
  public void testJavaScript2() throws Exception {
    makeTest().withInput("JavaScript://\u2029alert('Hey')//#")
        .filterOutputs(SKIP_JS_CONTEXT_VAR_OUTPUTS)
        .expectOutput(
              lbnd("Url")
            ,   LFRAG
            ,     lbnd("Protocol")
            ,       str("JavaScript:")
            ,       val(ProtocolT.JAVASCRIPT)
            ,     rbnd("Protocol")
            ,     val(PathKindT.OPAQUE)
            ,     lbnd("OpaqueBody")
            ,       lbnd("Js.Program")
            ,         str("//\u2029")
            ,         lbnd("Js.IdentifierName")
            ,           str("alert")
            ,         rbnd("Js.IdentifierName")
            ,         str("(")
            ,         lbnd("Js.StringLiteral")
            ,           str("'Hey'")
            ,         rbnd("Js.StringLiteral")
            ,         str(")//")
            ,       rbnd("Js.Program")
            ,     rbnd("OpaqueBody")
            ,   RFRAG
            ,   lbnd("Fragment")
            ,     str("#")
            ,   rbnd("Fragment")
            , rbnd("Url")
            )
        .run();
  }

  @Test
  public void testJoiningPathAndQueryEnds() throws Exception {
    GrammarTestCase.Builder testB = makeTest();
    GrammarTestCase.BranchBuilder b1 = testB.fork()
        .withInput("/path")
        .expectOutput(
              lbnd("Url")
            ,   llimit(C.chars('#'))
            ,     val(ProtocolT._NONE)
            ,     val(PathKindT.ABSOLUTE, PathKindT.RELATIVE)
            ,     lbnd("Path")
            ,       str("/path")
            ,     rbnd("Path")
            );
    GrammarTestCase.BranchBuilder b2 = testB.fork()
        .withInput("?query")
        .expectOutput(
              lbnd("Url")
            ,   llimit(C.chars('#'))
            ,     val(ProtocolT._NONE)
            ,     val(PathKindT.ABSOLUTE, PathKindT.RELATIVE)
            ,     lbnd("Path")
            ,     rbnd("Path")
            ,     lbnd("Query")
            ,       str("?query")
            ,     rbnd("Query")
         );
    b1.join(b2)
        .withInput("#frag")
        .expectOutput(
                rlimit(C.chars('#'))
            ,   lbnd("Fragment")
            ,     str("#frag")
            ,   rbnd("Fragment")
            , rbnd("Url"));
    testB.run();
  }

  @Ignore  // TODO: Known failure.  Fix me.
  @Test
  public void testJoiningRelativeAndAbsolute() throws Exception {
    GrammarTestCase.Builder testB = makeTest();
    GrammarTestCase.BranchBuilder b1, b2, b3, b4;

    b1 = testB.fork()
        .withInput("//www.google.com:8000")
        .expectOutput(
              lbnd("Url")
            ,   llimit(C.chars('#'))
            ,     val(ProtocolT._NONE)
            ,     str("//")
            ,     lbnd("Authority")
            ,       str("www.google.com:8000")
            ,     rbnd("Authority")
            ,     val(PathKindT.ABSOLUTE)
            );

    b2 = testB.fork()
        .withInput("https://foo")
        .expectOutput(
              llimit(C.chars('#'))
            ,   lbnd("Protocol")
            ,     str("https:")
            ,     val(ProtocolT.HTTPS)
            ,   rbnd("Protocol")
            ,   str("//")
            ,   lbnd("Authority")
            ,     str("foo")
            ,   rbnd("Authority")
            ,   val(PathKindT.ABSOLUTE)
            );

    b3 = testB.fork()
        .withInput("file://")
        .expectOutput(
              llimit(C.chars('#'))
            ,   lbnd("Protocol")
            ,     str("file:")
            ,     val(ProtocolT._UNKNOWN)
            ,   rbnd("Protocol")
            ,   str("//")
            ,   lbnd("Authority")
            ,   rbnd("Authority")
            ,   val(PathKindT.ABSOLUTE)
         );

    b4 = testB.fork()
        // Don't add any input to b4.  It's going to be path relative.
        .expectOutput(
              llimit(C.chars('#'))
            ,   val(ProtocolT._NONE)
            ,   val(PathKindT.ABSOLUTE, PathKindT.RELATIVE)
         );

    b1.join(b2, b3, b4)
        .withInput("/path#x")
        .expectOutput(
                  lbnd("Path")
            ,       str("/path")
            ,     rbnd("Path")
            ,   rlimit(C.chars('#'))
            ,   lbnd("Fragment")
            ,     str("#x")
            ,   rbnd("Fragment")
            , rbnd("Url")
            );
    testB.run();
  }

  private static final Predicate<Output> NO_SCOPE_OUTPUTS
  = new Predicate<Output>() {
    @Override
    public boolean apply(Output o) {
      if (o instanceof Scope) {
        return false;
      }
      return true;
    }
  };

  private static final LimitCheck LFRAG = new LimitCheck(
      Side.LEFT,
      C.chars('#'));

  private static final LimitCheck RFRAG = new LimitCheck(
      Side.RIGHT, LFRAG.limit);
}
