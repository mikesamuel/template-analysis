package com.google.template.autoesc.grammars;

import com.google.template.autoesc.ProdName;

/**
 * Well-known prefixes used when
 * {@linkplain com.google.template.autoesc.Language.Builder#include including}
 * grammars.
 */
public final class Prefixes {
  /** Prefix used for imported CSS grammar. */
  public static final ProdName CSS_PREFIX = new ProdName("Css");
  /** Prefix used for imported JS grammar. */
  public static final ProdName JS_PREFIX = new ProdName("Js");
  /** Prefix used for imported URL grammar. */
  public static final ProdName URL_PREFIX = new ProdName("Url");

  private Prefixes() {
    // Not instantiable
  }
}
