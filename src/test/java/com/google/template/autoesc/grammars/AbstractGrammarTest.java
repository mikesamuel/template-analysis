package com.google.template.autoesc.grammars;

import javax.annotation.CheckReturnValue;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.GrammarTestCase;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.inp.StringTransform;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.EmbedOutput;
import com.google.template.autoesc.out.LimitCheck;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.var.MultiVariable;
import com.google.template.autoesc.var.Scope;
import com.google.template.autoesc.var.Value;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.var.VariableOutput;

abstract class AbstractGrammarTest {
  static final Combinators C = Combinators.get();

  @CheckReturnValue
  protected abstract GrammarTestCase.Builder makeTest();

  static final StringOutput str(String s) {
    return new StringOutput(s, s);
  }

  static final Boundary lbnd(String s) {
    return new Boundary(Side.LEFT, new ProdName(s));
  }

  static final Boundary rbnd(String s) {
    return new Boundary(Side.RIGHT, new ProdName(s));
  }

  static final LimitCheck llimit(Combinator c) {
    return new LimitCheck(Side.LEFT, c);
  }

  static final LimitCheck rlimit(Combinator c) {
    return new LimitCheck(Side.RIGHT, c);
  }

  static final EmbedOutput lembed(StringTransform xform) {
    return new EmbedOutput(Side.LEFT, xform);
  }

  static final EmbedOutput rembed(StringTransform xform) {
    return new EmbedOutput(Side.RIGHT, xform);
  }

  static <T> Value<T> val(Variable<T> var, T val) {
    return new Value<>(var, val);
  }

  @SafeVarargs
  static <T extends Enum<T>>
  Value<ImmutableSet<T>> val(MultiVariable<T> var, T... vals) {
    return new Value<>(var, ImmutableSet.copyOf(vals));
  }

  static Value<CssGrammar.PropKindT> val(CssGrammar.PropKindT x) {
    return val(CssGrammar.PROP_KIND, x);
  }

  static Value<HtmlGrammar.AttrT> val(HtmlGrammar.AttrT x) {
    return val(HtmlGrammar.ATTR, x);
  }

  static final Output val(UrlGrammar.ProtocolT x) {
    return val(UrlGrammar.PROTOCOL, x);
  }

  static final Output val(UrlGrammar.PathKindT... xs) {
    return val(UrlGrammar.PATH_RESTRICTION, xs);
  }

  static Scope ldef(Variable<?> var) {
    return new Scope(Side.LEFT, var);
  }

  static Scope rdef(Variable<?> var) {
    return new Scope(Side.RIGHT, var);
  }

  static final Predicate<Output> SKIP_JS_CONTEXT_VAR_OUTPUTS
  = new Predicate<Output>() {
    @Override
    public boolean apply(Output o) {
      if (o instanceof VariableOutput
          && JsGrammar.SLASH_IS.equals(
              ((VariableOutput) o).getVariable())) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "SKIP_JS_CONTEXT_VAR_OUTPUTS";
    }
  };

}
