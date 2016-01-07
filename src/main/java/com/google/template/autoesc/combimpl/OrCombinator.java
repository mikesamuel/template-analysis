package com.google.template.autoesc.combimpl;

import java.io.Closeable;
import java.io.IOException;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
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
import com.google.template.autoesc.out.BranchMarker;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.AbstractVisualizable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.Visualizable;
import com.google.template.autoesc.viz.Visualizables;
import com.google.template.autoesc.viz.VizOutput;

import static com.google.template.autoesc.viz.AttribName.CLASS;
import static com.google.template.autoesc.viz.TagName.SPAN;

/**
 * A combinator that tries each option in order and succeeds with the result
 * of the first that succeeds.
 */
public class OrCombinator extends BinaryCombinator {
  /** */
  public OrCombinator(
      Supplier<NodeMetadata> mds, Combinator first, Combinator second) {
    super(mds, first, second);
  }

  @Override
  protected Combinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer,
      Combinator newFirst, Combinator newSecond) {
    if (newMetadata.equals(md) && newFirst == first && newSecond == second) {
      return this;
    }
    return new OrCombinator(
        Suppliers.ofInstance(newMetadata), newFirst, newSecond);
  }


  @Override
  public ParseDelta enter(Parse p) {
    return enter();
  }

  @Override
  public final ParseDelta exit(Parse p, Success s) {
    switch (s) {
      case FAIL: return exitFail();
      case PASS: return exitPass();
    }
    throw new AssertionError(s.name());
  }

  private ParseDelta enter() {
    return ParseDelta
        // Try the first option.
        .builder(first)
        // Add this so that we can remember to try the next option if the first
        // fails.
        .push()
        // Remember the current output so we can rollback changes if the first
        // option fails.
        .withOutput(BranchMarker.INSTANCE)
        .build();
  }

  private ParseDelta exitFail() {
    // Fail over to later options.
    return ParseDelta.builder(second)
        .withIOTransform(BranchMarker.INSTANCE.rollback())
        .build();
  }

  private static ParseDelta exitPass() {
    return ParseDelta.pass()
        .commit(BranchMarker.INSTANCE)
        .build();
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    ParseDelta result = null;
    switch (tt) {
      case ENTER:     result = enter();    break;
      case EXIT_FAIL: result = exitFail(); break;
      case EXIT_PASS: result = exitPass(); break;
    }
    return ImmutableList.of(Preconditions.checkNotNull(result));
  }

  private Combinator getLastOperand() {
    Combinator last = second;
    while (last instanceof OrCombinator) {
      last = ((OrCombinator) last).second;
    }
    return last;
  }

  private Combinator allButLast() {
    if (second instanceof OrCombinator) {
      return unfold(
          md, Functions.<ProdName>identity(), first,
          ((OrCombinator) second).allButLast());
    }
    return first;
  }

  @Override
  public Precedence precedence() {
    return EmptyCombinator.INSTANCE.equals(getLastOperand())
        ? Precedence.SELF_CONTAINED  // x? and x*
	: Precedence.OR;  // x | y
  }

  @Override
  protected String getVizTypeClassName() {
    return "or";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    Combinator last = getLastOperand();
    if (last instanceof EmptyCombinator) {
      // Treat x? and x* specially.
      Combinator allButLast = allButLast();
      Visualizable bodyVisualizable;
      final String suffixOperator;
      Precedence bodyPrecedence;
      if (allButLast instanceof LoopCombinator) {
        suffixOperator = "*";
	final LoopCombinator loop = (LoopCombinator) allButLast;
        bodyVisualizable = new VizLoop(loop);
        bodyPrecedence = loop.getLoopBody().precedence();
      } else {
        bodyVisualizable = allButLast;
        suffixOperator = "?";
        bodyPrecedence = allButLast.precedence();
      }
      boolean parenthesize = 0 < Precedence.SELF_CONTAINED.compareTo(
          bodyPrecedence);
      if (parenthesize) { out.text("("); }
      bodyVisualizable.visualize(lvl, out);
      if (parenthesize) { out.text(")"); }
      AbstractVisualizable.visualize(
          last, ((EmptyCombinator) last).getVizTypeClassName(), lvl, out,
          Visualizables.text(suffixOperator));  // TODO suffix
    } else {
      writeBinaryOperator(
          first, second, precedence(),
          Predicates.instanceOf(OrCombinator.class),
          OR_OPERATOR_INFIXER,
          lvl, out);
    }
  }

  @Override
  public Frequency consumesInput(Language lang) {
    Frequency ff = lang.lali.consumesInput(first);
    Frequency sf = lang.lali.consumesInput(second);
    if (ff != sf) {
      return Frequency.SOMETIMES;
    }
    return ff;
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    TreeRangeSet<Integer> r = TreeRangeSet.create();
    r.addAll(lang.lali.lookahead(first));
    r.addAll(lang.lali.lookahead(second));
    return ImmutableRangeSet.copyOf(r);
  }

  @Override
  public boolean reachesWithoutConsuming(Combinator target, Language lang) {
    // Check whether stripping elements off the front as they fail over to
    // the rest causes the target to be entered.
    return first.reachesWithoutConsuming(target, lang)
        || second.reachesWithoutConsuming(target, lang)
        || super.reachesWithoutConsuming(target, lang);
  }


  private static final Infixer OR_OPERATOR_INFIXER =
      new Infixer() {

    @Override
    public void visualizeOperator(
        Optional<Combinator> prev, Combinator next, VizOutput out)
    throws IOException {
      if (prev.isPresent()) {
        try (Closeable c = out.open(SPAN, CLASS, "binary-operator")) {
          out.text(" / ");
        }
      }
    }

    @Override
    public void visualizeOperand(
        Optional<Combinator> prev, Combinator next,
        DetailLevel lvl, VizOutput out)
    throws IOException {
      next.visualize(lvl, out);
    }

    @Override
    public void finish(Optional<Combinator> prev, VizOutput out) {
      // Do nothing
    }
  };
}


final class VizLoop implements Visualizable {
  private final LoopCombinator loop;

  VizLoop(LoopCombinator loop) {
    this.loop = loop;
  }

  @Override
  public void visualize(DetailLevel ilvl, VizOutput iout)
  throws IOException {
    AbstractVisualizable.visualize(
        loop, loop.getVizTypeClassName(), ilvl, iout, loop.getLoopBody());
  }
}
