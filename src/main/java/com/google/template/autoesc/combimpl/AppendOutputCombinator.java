package com.google.template.autoesc.combimpl;

import java.io.IOException;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A combinator that always passes and which appends a particular token to the
 * output.
 */
public final class AppendOutputCombinator extends AtomicCombinator {
  /** Appended to the output on entry. */
  public final Output output;

  /**
   * @param output the output to append.
   */
  public AppendOutputCombinator(Supplier<NodeMetadata> mds, Output output) {
    super(mds);
    this.output = output;
  }

  @Override
  public ParseDelta enter(Parse p) {
    return ParseDelta.pass().withOutput(output).build();
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(ParseDelta.pass().withOutput(output).build());
      case EXIT_FAIL:
        case EXIT_PASS:
        break;
    }
    throw new AssertionError(tt);
  }

  @Override
  protected AtomicCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer) {
    Output newOutput = output;
    // HACK: there's only one output that really depends on production names.
    // If this changes, then generalize this by making Outputs aware
    // of renaming.
    if (newOutput instanceof Boundary) {
      Boundary b = (Boundary) newOutput;
      ProdName newName = renamer.apply(b.prodName);
      if (!newName.equals(b.prodName)) {
        newOutput = new Boundary(b.side, newName);
      }
    }
    if (newMetadata.equals(this.md) && output == newOutput) {
      return this;
    }
    return new AppendOutputCombinator(
        Suppliers.ofInstance(newMetadata), newOutput);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AppendOutputCombinator)) { return false; }
    AppendOutputCombinator that = (AppendOutputCombinator) o;
    return this.output.equals(that.output);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(output);
  }

  @Override
  protected String getVizTypeClassName() {
    return "append";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    out.text("{");
    output.visualize(lvl, out);
    out.text("}");
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return Frequency.NEVER;
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return ImmutableRangeSet.of();
  }
}
