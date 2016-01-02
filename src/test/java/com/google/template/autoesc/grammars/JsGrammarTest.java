package com.google.template.autoesc.grammars;

import javax.annotation.CheckReturnValue;

import org.junit.Test;

import com.google.template.autoesc.GrammarTestCase;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.var.Scope;
import com.google.template.autoesc.var.Value;

import static com.google.template.autoesc.out.Side.LEFT;
import static com.google.template.autoesc.out.Side.RIGHT;
import static com.google.template.autoesc.grammars.JsGrammar.SLASH_IS;
import static com.google.template.autoesc.grammars.JsGrammar.SlashIsT.REGEX;
import static com.google.template.autoesc.grammars.JsGrammar.SlashIsT.DONT_CARE;

@SuppressWarnings({ "static-method", "javadoc" })
public final class JsGrammarTest {

  @CheckReturnValue
  static GrammarTestCase.Builder makeTest() {
    return new GrammarTestCase.Builder(JsGrammar.OPT_LANG)
        //.alsoRunOn(JsGrammar.LANG)
        ;
  }

  @Test
  public final void testString() throws Exception {
    makeTest()
        .withInput("var s = 'str';")
        .expectOutput(
            new Scope(LEFT, SLASH_IS),
            new Value<>(SLASH_IS, DONT_CARE),
            stringOutput("var"),
            new Value<>(SLASH_IS, DONT_CARE),
            stringOutput(" s"),
            new Value<>(SLASH_IS, DONT_CARE),
            stringOutput(" ="),
            new Value<>(SLASH_IS, DONT_CARE),
            stringOutput(" "),
            boundary(LEFT, "StringLiteral"),
            stringOutput("'str'"),
            boundary(RIGHT, "StringLiteral"),
            new Value<>(SLASH_IS, DONT_CARE),
            stringOutput(";"),
            new Value<>(SLASH_IS, REGEX),
            new Scope(RIGHT, SLASH_IS)
            )
        .run();
  }


  private static StringOutput stringOutput(String s) {
    return new StringOutput(s, s);
  }

  private static Boundary boundary(Side side, String name) {
    return new Boundary(side, new ProdName(name));
  }
}
