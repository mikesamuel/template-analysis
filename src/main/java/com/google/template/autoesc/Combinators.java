package com.google.template.autoesc;

import com.google.template.autoesc.combimpl.AppendOutputCombinator;
import com.google.template.autoesc.combimpl.BoundedReferenceCombinator;
import com.google.template.autoesc.combimpl.CharSetCombinator;
import com.google.template.autoesc.combimpl.EmbedCombinator;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ErrorCombinator;
import com.google.template.autoesc.combimpl.LookaheadCombinator;
import com.google.template.autoesc.combimpl.LoopCombinator;
import com.google.template.autoesc.combimpl.ReferenceCombinator;
import com.google.template.autoesc.combimpl.SeqCombinator;
import com.google.template.autoesc.combimpl.TestMultiVarCombinator;
import com.google.template.autoesc.combimpl.TestVarCombinator;
import com.google.template.autoesc.combimpl.UntilCombinator;
import com.google.template.autoesc.combimpl.OrCombinator;
import com.google.template.autoesc.inp.CaseSensitivity;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.inp.StringTransform;
import com.google.template.autoesc.inp.StringTransforms;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.LimitCheck;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.var.MultiVariable;
import com.google.template.autoesc.var.Scope;
import com.google.template.autoesc.var.Value;
import com.google.template.autoesc.var.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.RangeSet;


/**
 * Factories for building {@linkplain Combinator combinators}.
 * <p>
 * See the {@link com.google.template.autoesc.grammars grammars sub-package}
 * for examples of usage.
 */
public class Combinators {
  private final Supplier<NodeMetadata> mds;

  private Combinators(Supplier<NodeMetadata> mds) {
    this.mds = Preconditions.checkNotNull(mds);
  }

  protected Combinators(Combinators cs) {
    this(cs.mds);
  }

  /**
   * A combinator that tries each option in order and succeeds with the result
   * of the first that succeeds.
   */
  @SafeVarargs  // Els is not written.
  public final Combinator or(Combinator... els) {
    return or(ImmutableList.copyOf(els));
  }

  /**
   * A combinator that tries each option in order and succeeds with the result
   * of the first that succeeds.
   */
  public Combinator or(Iterable<? extends Combinator> els) {
    ImmutableList<Combinator> operands = ImmutableList.copyOf(els);
    int n = operands.size();
    if (n == 0) { return error(); }
    Combinator right = operands.get(n - 1);
    for (int i = n - 1; --i >= 0;) {
      right = new OrCombinator(mds, operands.get(i), right);
    }
    return right;
  }

  /**
   * seq(A,B) executes A and then B on the remaining input.
   * <p>
   * This matches the minimal language
   * {&forall;a&isin;A,%forall;b%isin;concatenation(a, b)}.
   */
  @SafeVarargs  // els is not written.
  public final Combinator seq(Combinator... els) {
    return seq(ImmutableList.copyOf(els));
  }

  /**
   * seq(A,B) executes A and then B on the remaining input.
   * <p>
   * This matches the minimal language
   * {&forall;a&isin;A,%forall;b%isin;concatenation(a, b)}.
   */
  public Combinator seq(Iterable<? extends Combinator> els) {
    ImmutableList<Combinator> operands;
    {
      ImmutableList.Builder<Combinator> b = ImmutableList.builder();
      for (Combinator el : els) {
        if (el.getClass() == SeqCombinator.class) {
          b.addAll(el.children());
        } else if (el != EmptyCombinator.INSTANCE) {
          b.add(el);
        }
      }
      operands = b.build();
    }
    int n = operands.size();
    if (n == 0) { return empty(); }
    Combinator right = operands.get(n - 1);
    for (int i = n - 1; --i >= 0;) {
      right = new SeqCombinator(mds, operands.get(i), right);
    }
    return right;
  }

  /**
   * Matches no strings.
   */
  public static Combinator error() {
    return ErrorCombinator.INSTANCE;
  }

  /**
   * Matches the empty prefix which is a prefix of every input.
   */
  public static Combinator empty() {
    return EmptyCombinator.INSTANCE;
  }

  /**
   * Passes when <i>s</i> is at the beginning of input, advances the input
   * cursor past it, and appends the {@link StringOutput matched characters}
   * to the output.
   * <p>
   * Pauses matching if there is not enough input to determine whether <i>s</i>
   * is on the input.
   */
  public Combinator lit(String s) {
    return lit(s, CaseSensitivity.SENSITIVE);
  }

  /**
   * Passes when <i>s</i> is at the beginning of input
   * {@link CaseSensitivity#IGNORE ignoring case}, advances the input
   * cursor past it, and appends the {@link StringOutput matched characters}
   * to the output.
   * <p>
   * Pauses matching if there is not enough input to determine whether <i>s</i>
   * is on the input.
   */
  public Combinator litIgnCase(String s) {
    return lit(s, CaseSensitivity.IGNORE);
  }

  /**
   * Passes when <i>s</i> is at the beginning of input
   * (modulo case-sensitivity), advances the input
   * cursor past it, and appends the {@link StringOutput matched characters}
   * to the output.
   * <p>
   * Pauses matching if there is not enough input to determine whether <i>s</i>
   * is on the input.
   */
  public Combinator lit(String s, CaseSensitivity cs) {
    ImmutableList.Builder<CharSetCombinator> chars = ImmutableList.builder();
    for (int i = 0, n = s.length(), nc; i < n; i += nc) {
      int cp = s.codePointAt(i);
      nc = Character.charCount(cp);
      chars.add(new CharSetCombinator(mds, cs.enumerate(cp)));
    }
    return seq(chars.build());
  }

  /**
   * Like {@link #lit} but matches the longest string that is
   * a prefix of the input.
   */
  public Combinator lits(String... lits) {
    return lits(CaseSensitivity.SENSITIVE, lits);
  }

  /**
   * Like {@link #lit} but matches the longest string that is
   * a prefix of the input module case-sensitivity.
   */
  public Combinator lits(CaseSensitivity cs, String... lits) {
    List<String> els = new ArrayList<>();
    for (String lit : lits) {
      els.add(cs.normalizeCharSequence(lit));
    }
    // Because or is short-circuiting, we need to sort s before prefixes of s
    // so that we find the maximal match.
    // For example:
    //   ("in" / "instanceof")
    // for the input "instanceof"
    // will match "in" leaving "stanceof" on the input cursor
    // because or tries in-order.
    Collections.sort(
        els,
        new Comparator<String>() {
          @Override
          public int compare(String a, String b) {
            int delta = a.compareTo(b);
            if (delta != 0) {
              // If a is a prefix of b then delta < 0.
              if (delta < 0) {
                if (b.startsWith(a)) { delta = 1; }
              } else {
                if (a.startsWith(b)) { delta = -1; }
              }
            }
            return delta;
          }
        });
    ImmutableList.Builder<Combinator> b = ImmutableList.builder();
    for (String el : els) {
      b.add(lit(el, cs));
    }
    return or(b.build());
  }

  /**
   * A combinator that looks at the first whole code-point on the input and
   * passes if it is one of <i>chars</i>, consumes it, and
   * {@link StringOutput appends it to the output}.
   */
  public Combinator chars(int... chars) {
    return chars(UniRanges.of(chars));
  }

  /**
   * A combinator that looks at the first whole code-point on the input and
   * passes if it is <b>not</b> one of <i>chars</i>, consumes it, and
   * {@link StringOutput appends it to the output}.
   */
  public Combinator invChars(int... chars) {
    return chars(UniRanges.invert(UniRanges.of(chars)));
  }

  /**
   * A combinator that passes if the input is not empty,
   * consumes the first code-point, and
   * {@link StringOutput appends it to the output}.
   * <p>
   * This is equivalent to {@link #invChars invChars}().
   */
  public Combinator anyChar() {
    return chars(UniRanges.ALL_CODEPOINTS);
  }

  /**
   * A combinator that looks at the first whole code-point on the input and
   * passes if ranges contains it, consumes it, and
   * {@link StringOutput appends it to the output}.
   *
   * @see UniRanges
   */
  public Combinator chars(RangeSet<Integer> ranges) {
    return new CharSetCombinator(mds, ranges);
  }

  /**
   * A negative lookahead that passes when d fails and vice-versa.
   * Regardless of whether it passes, it does not modify (or allow d to modify)
   * the output or consume any input.
   */
  public Combinator not(Combinator c) {
    return new LookaheadCombinator(mds, c, false);
  }

  /**
   * A positive lookahead that passes when d passes.
   * <p>
   * Regardless of whether it passes, it does not modify (or allow d to modify)
   * the output or consume any input.
   */
  public Combinator la(Combinator c) {
    return new LookaheadCombinator(mds, c, true);
  }

  /**
   * A combinator that decodes the input using xform and feeds it to body to
   * handle embedded combinators.
   * <p>
   * For example, {@code <a href="?q=a&amp;b">} contains the embedded URL
   * {@code ?q=a&b} and we need to decode the {@code &amp;} for the URL grammar
   * to make sense of the embedded input.
   * </p>
   * @see StringTransforms
   */
  public Combinator embed(Combinator body, StringTransform xform) {
    // TODO: automatically wrap in body in (... / bref("Malformed")) so that
    // failure in the embedded grammar does not translate to failure in the
    // embedding grammar.
    return new EmbedCombinator(mds, body, xform);
  }

  /**
   * A combinator that identifies a prefix of the input until limitRecognizer
   * exposes body to only that prefix, and passes when body matches all of that
   * input.
   * <p>
   * The region matched by limitRecognizer is not consumed, and if there is
   * no match before end of input, body will be exposed to the entire input.
   * Use {@link #seq} to match the limit if it is required.
   * <p>
   * When until combinators pass, they also append {@link LimitCheck}s so that
   * the caller can double-check that the eventual output does not match the
   * limitRecognizer earlier than it was during parse.
   * <br>
   * For example, if the output is
   * <pre>[{limit /foo/}, "x", {pause}, "o", {/limit /foo/}]</pre>
   * and the pause were filled with {@code "fo"} then the limit pattern
   * {@code /foo/} would match earlier so would violate assumptions made by
   * the parser.
   */
  public Combinator until(Combinator body, Combinator limit) {
    return new UntilCombinator(mds, body, limit);
  }

  /**
   * A reference to a non-terminal resolved using {@link Language}.
   */
  public Combinator ref(String name) {
    return ref(new ProdName(name));
  }

  /**
   * A reference to a non-terminal resolved using {@link Language}.
   */
  public Combinator ref(ProdName name) {
    return new ReferenceCombinator(mds, name);
  }

  /**
   * A <i>bounded</i> reference that matches just like a {@link #ref} but which
   * surrounds any output appended by the named non-terminal's body with
   * {@link Boundary} markers.
   */
  public Combinator bref(String name) {
    return bref(new ProdName(name));
  }

  /**
   * A <i>bounded</i> reference that matches just like a {@link #ref} but which
   * surrounds any output appended by the named non-terminal's body with
   * {@link Boundary} markers.
   */
  public Combinator bref(ProdName name) {
    // We could phrase this in terms of a concatenation of
    // AppendOutputs with left and right boundary around ref(name),
    // but that makes debugging output spammy.
    // Instead of seeing X, the debug output looks like {X} X {/X}.
    return new BoundedReferenceCombinator(mds, name);
  }

  /**
   * Kleene plus which matches body one or more times.
   * <p>
   * This passes if body passes at least once.
   * It terminates the first time body fails or the first time body passes but
   * without consuming any input, whichever happens first.
   */
  public Combinator plus(Combinator body) {
    return new LoopCombinator(mds, body);
  }

  /**
   * Kleene star which matches body zero or more times.
   * <p>
   * This passes if body passes at least once.
   * It terminates the first time body fails or the first time body passes but
   * without consuming any input, whichever happens first.
   */
  public Combinator star(Combinator body) {
    return opt(plus(body));
  }

  /**
   * Optionally matches body.
   * This always passes.
   */
  public Combinator opt(Combinator body) {
    return or(body, empty());
  }

  /**
   * Matches when the input is empty and
   * {@link Parser#finishParse no more input can follow}.
   */
  public Combinator endOfInput() {
    return not(anyChar());
  }

  /**
   * Declares a scope for the given variable by appending markers to the output.
   */
  public Combinator decl(Variable<?> var, Combinator body) {
    return seq(
        emit(new Scope(Side.LEFT, var)),
        body,
        emit(new Scope(Side.RIGHT, var)));
  }

  /**
   * On success, appends the given output to the output stream.
   * <p>
   * Be careful with this.  Left and right {@link Side sides} must match up
   * properly.
   */
  public Combinator emit(Output o) {
    return new AppendOutputCombinator(mds, o);
  }

  /**
   * Sets the value of the given variable by appending to the output.
   */
  public <T> Combinator set(Variable<T> var, T value) {
    return new AppendOutputCombinator(mds, new Value<>(var, value));
  }

  /**
   * Passes when var has one of the given values.
   * <p>
   * The value of var is determined by looking
   * backwards over the input at {@link #decl scope} and {@link #set value}
   * markers.
   *
   * @see Value#valueOf
   */
  @SafeVarargs  // Not assigned
  public final <T> Combinator in(Variable<T> var, T... values) {
    return in(var, ImmutableSet.<T>copyOf(values));
  }

  /**
   * Passes when var has one of the given values.
   * <p>
   * The value of var is determined by looking
   * backwards over the input at {@link #decl scope} and {@link #set value}
   * markers.
   *
   * @see Value#valueOf
   */
  public <T> Combinator in(Variable<T> var, Iterable<T> values) {
    return new TestVarCombinator<>(mds, var, ImmutableSet.copyOf(values));
  }

  /**
   * Passes when var does <b>not</b> have one of the given values.
   * @see #in
   */
  @SafeVarargs
  public final <T extends Enum<T>>
  Combinator notIn(Variable<T> var, T... values) {
    return notIn(var, ImmutableSet.copyOf(values));
  }

    /**
   * Passes when var does <b>not</b> have one of the given values.
   * @see #in
   */
  public <T extends Enum<T>>
  Combinator notIn(Variable<T> var, Iterable<T> values) {
    ImmutableSet<T> valueSet = ImmutableSet.copyOf(values);
    ImmutableSet<T> invValueSet = ImmutableSet.copyOf(
        EnumSet.complementOf(EnumSet.copyOf(valueSet)));
    return in(var, invValueSet);
  }

  /** Passes when var's value contains value. */
  public <T extends Enum<T>> Combinator has(MultiVariable<T> var, T value) {
    return new TestMultiVarCombinator<>(mds, var, value);
  }


  // Let AbstractCombinator infer the debug source string.
  private static final Combinators INFER_SOURCE_INSTANCE = new Combinators(
      new StackIntrospectingMetadataSupplier());

  /**
   * A grammar that tries to derive a source by stack introspection.
   */
  public static Combinators get() {
    return INFER_SOURCE_INSTANCE;
  }

  /**
   * A grammar that uses the given source for created nodes.
   */
  public static Combinators using(Supplier<NodeMetadata> mds) {
    return new Combinators(mds);
  }
}


final class StackIntrospectingMetadataSupplier
implements Supplier<NodeMetadata> {

  private static Source getSourceFromStack() {
    for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
      String fn = el.getFileName();
      String baseName = fn.substring(
          1 +
          Math.max(
              fn.lastIndexOf('/'),
              fn.lastIndexOf('\\')));
      // Match HtmlGrammar, CssGrammar, etc. but not Grammar.
      if ((baseName.endsWith("Grammar.java")
          && !baseName.equals("Grammar.java"))
          || baseName.endsWith("Test.java")) {
        return new Source(fn, el.getLineNumber());
      }
    }
    throw new AssertionError("Cannot determine source");
  }

  @Override
  public NodeMetadata get() {
    return new NodeMetadata(
        getSourceFromStack(),
        0, // Reassigned by Language
        Optional.<String>absent());
  }

}
