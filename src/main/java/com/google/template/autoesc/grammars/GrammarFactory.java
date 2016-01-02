package com.google.template.autoesc.grammars;

import com.google.template.autoesc.Combinators;

abstract class GrammarFactory extends Combinators {
  GrammarFactory() {
    super(Combinators.get());
  }
}
