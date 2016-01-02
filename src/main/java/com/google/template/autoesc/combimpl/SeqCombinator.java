package com.google.template.autoesc.combimpl;

import java.io.IOException;
import java.util.EnumSet;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.Precedence;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.Success;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.inp.CaseSensitivity;
import com.google.template.autoesc.inp.RawCharsInputCursor;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.Visualizables;
import com.google.template.autoesc.viz.VizOutput;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Executes A and then B on the remaining input.
 * <p>
 * This matches the minimal language
 * {&forall;a&isin;A,%forall;b%isin;concatenation(a, b)}.
 */
public final class SeqCombinator extends BinaryCombinator {
  /** */
  public SeqCombinator(
      Supplier<NodeMetadata> mds, Combinator first, Combinator second) {
    super(mds, first, second);
  }

  @Override
  protected SeqCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      Combinator newFirst, Combinator newSecond) {
    if (newMetadata.equals(this.md)
        && this.first == newFirst && this.second == newSecond) {
      return this;
    }
    return new SeqCombinator(
        Suppliers.ofInstance(newMetadata), newFirst, newSecond);
  }

  @Override
  public ParseDelta enter(Parse p) {
    return enter();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    switch (s) {
      case FAIL: return EXIT_FAIL;
      case PASS: return exitPass();
    }
    throw new AssertionError(s);
  }

  @Override
  public final ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:     return ImmutableList.of(enter());
      case EXIT_PASS: return ImmutableList.of(exitPass());
      case EXIT_FAIL: return ImmutableList.of(EXIT_FAIL);
    }
    throw new AssertionError(tt);
  }

  private ParseDelta enter() {
    return ParseDelta
        // Start the left.
        .builder(first)
        // Push this so that we can remember to do b if a passes.
        .push()
        .build();
  }

  private static final ParseDelta EXIT_FAIL = ParseDelta.fail().build();

  private ParseDelta exitPass() {
    // Continue to the next entry.
    return ParseDelta.builder(second).build();
  }

  @Override
  public Precedence precedence() {
    return Precedence.SEQUENCE;
  }

  @Override
  protected String getVizTypeClassName() {
    return "seq";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    writeBinaryOperator(
        first, second, precedence(),
        Predicates.instanceOf(SeqCombinator.class),
        STRING_COALESCING_INFIXER, lvl, out);
  }

  @Override
  public Frequency consumesInput(Language lang) {
    Frequency ff = lang.lali.consumesInput(first);
    switch (ff) {
      case NEVER: return lang.lali.consumesInput(second);
      case ALWAYS: return ff;
      case SOMETIMES:
        Frequency sf = lang.lali.consumesInput(second);
        switch (sf) {
          case ALWAYS: case SOMETIMES: return sf;
          case NEVER: return Frequency.SOMETIMES;
        }
        throw new AssertionError(sf.name());
    }
    throw new AssertionError(ff.name());
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    Frequency ff = lang.lali.consumesInput(first);
    switch (ff) {
      case NEVER: return lang.lali.lookahead(second);
      case ALWAYS: return lang.lali.lookahead(first);
      case SOMETIMES:
        TreeRangeSet<Integer> r = TreeRangeSet.create();
        r.addAll(lang.lali.lookahead(first));
        r.addAll(lang.lali.lookahead(second));
        return ImmutableRangeSet.copyOf(r);
    }
    throw new AssertionError(ff.name());
  }

  @Override
  public boolean reachesWithoutConsuming(Combinator target, Language lang) {
    // Check whether target is reachable from any member.
    return super.reachesWithoutConsuming(target, lang)
        || first.reachesWithoutConsuming(target, lang)
        || (lang.lali.consumesInput(first) != Frequency.ALWAYS
            && second.reachesWithoutConsuming(target, lang));
  }


  /** Finds sequences of characters that */
  private static final Infixer STRING_COALESCING_INFIXER =
      new StringCoalescingInfixer();

}

final class StringCoalescingInfixer implements BinaryCombinator.Infixer {

  @Override
  public void visualizeOperator(
      Optional<Combinator> prev, Combinator next, VizOutput out)
  throws IOException {
    boolean prevIsInString = prev.isPresent()
        && caseSensitivityOf(prev.get()).isPresent();
    boolean nextIsInString = caseSensitivityOf(next).isPresent();
    if (prevIsInString) {
      if (nextIsInString) {
        // Do nothing
      } else {
        out.text("\u201D ");  // Close a string
      }
    } else if (nextIsInString) {
      out.text(prev.isPresent() ? " \u201C" : "\u201C");  // Open a string.
    } else if (prev.isPresent()) {
      out.text(" ");
    }
  }

  @SuppressFBWarnings(
      value="BC_UNCONFIRMED_CAST",
      justification="If case sensitivity is present then operands are chars")
  @Override
  public void visualizeOperand(
      Optional<Combinator> prev, Combinator next,
      DetailLevel lvl, VizOutput out)
  throws IOException {
    Optional<CaseSensitivity> csOpt = caseSensitivityOf(next);
    if (csOpt.isPresent()) {  // Inside a string
      CharSetCombinator csd = (CharSetCombinator) next;
      final CaseSensitivity cs = csOpt.get();
      final int cp = leastCodePointOf(csd.codePoints);
      AbstractVisualizable.visualize(
          csd, csd.getVizTypeClassName() + " " + cs, lvl, out,
          Visualizables.text(
              RawCharsInputCursor.STRING_ESCAPER.escape(
                  new StringBuilder().appendCodePoint(cp).toString())));
    } else {
      next.visualize(lvl, out);
    }
  }

  @Override
  public void finish(Optional<Combinator> prev, VizOutput out)
  throws IOException {
    boolean prevIsInString = prev.isPresent()
        && caseSensitivityOf(prev.get()).isPresent();
     if (prevIsInString) {
       out.text("\u201D");
     }
  }

  static Optional<CaseSensitivity> caseSensitivityOf(Combinator c) {
    if (c instanceof CharSetCombinator) {
      CharSetCombinator cs = (CharSetCombinator) c;
      int leastCp = leastCodePointOf(cs.codePoints);
      for (CaseSensitivity candidate : ALL_CS) {
        if (cs.codePoints.equals(candidate.enumerate(leastCp))) {
          return Optional.of(candidate);
        }
      }
    }
    return Optional.absent();
  }

  static int leastCodePointOf(ImmutableRangeSet<Integer> cps) {
    if (!cps.isEmpty()) {
      Range<Integer> range0 = cps.asRanges().iterator().next();
      if (range0.hasLowerBound()) {
        return range0.lowerEndpoint();
      }
    }
    return -1;
  }

  private static final EnumSet<CaseSensitivity> ALL_CS =
    EnumSet.allOf(CaseSensitivity.class);
}
