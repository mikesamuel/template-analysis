package com.google.template.autoesc.grammars;

import com.google.common.base.Optional;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.UniRanges;

/**
 * A grammar that matches any run of Unicode
 * <a href="http://www.unicode.org/glossary/#unicode_scalar_value">scalar values</a>.
 */
public class TextGrammar {

  /** Name of a production that matches a run of Unicode scalar values. */
  public static final ProdName N_TEXT = new ProdName("Text");

  /** Language that matches a run of Unicode scalar values. */
  public static final Language LANG;

  static {
    Combinators c = Combinators.get();
    Language.Builder lang = new Language.Builder();
    String demoServerQuery;
    try {
      demoServerQuery = DemoServerQuery.builder()
          .grammarField(TextGrammar.class.getDeclaredField("LANG"))
          .build();
    } catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }
    lang.demoServerQuery(Optional.of(demoServerQuery));
    lang.defaultStartProdName(N_TEXT);
    lang.define(
        N_TEXT,
        c.star(c.chars(UniRanges.invert(UniRanges.btw(0xD800, 0xDFFF)))));

    LANG = lang.build();
  }
}
