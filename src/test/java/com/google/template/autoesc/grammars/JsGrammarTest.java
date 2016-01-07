package com.google.template.autoesc.grammars;

import javax.annotation.CheckReturnValue;

import org.junit.Test;

import com.google.template.autoesc.GrammarTestCase;
import com.google.template.autoesc.var.Value;

import static com.google.template.autoesc.grammars.JsGrammar.SLASH_IS;
import static com.google.template.autoesc.grammars.JsGrammar.SlashIsT.REGEX;
import static com.google.template.autoesc.grammars.JsGrammar.SlashIsT.DONT_CARE;

@SuppressWarnings({ "javadoc" })
public final class JsGrammarTest extends AbstractGrammarTest {

  @CheckReturnValue
  @Override
  protected
  GrammarTestCase.Builder makeTest() {
    return new GrammarTestCase.Builder(JsGrammar.OPT_LANG)
        .alsoRunOn(JsGrammar.LANG)
        ;
  }

  @Test
  public final void testString() throws Exception {
    makeTest()
        .withInput("var s = 'str';")
        .expectOutput(
            lbnd("Program"),
            ldef(SLASH_IS),
            new Value<>(SLASH_IS, DONT_CARE),
            str("var"),
            new Value<>(SLASH_IS, DONT_CARE),
            str(" "),
            lbnd("IdentifierName"),
            str("s"),
            rbnd("IdentifierName"),
            new Value<>(SLASH_IS, DONT_CARE),
            str(" ="),
            new Value<>(SLASH_IS, DONT_CARE),
            str(" "),
            lbnd("StringLiteral"),
            str("'str'"),
            rbnd("StringLiteral"),
            new Value<>(SLASH_IS, DONT_CARE),
            str(";"),
            new Value<>(SLASH_IS, REGEX),
            rdef(SLASH_IS),
            rbnd("Program")
            )
        .run();
  }
}
