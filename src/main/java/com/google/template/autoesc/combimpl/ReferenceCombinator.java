package com.google.template.autoesc.combimpl;

import java.io.Closeable;
import java.io.IOException;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Frequency;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.Success;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.viz.AttribName;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A reference to a non-terminal defined in
 * {@link com.google.template.autoesc.Language}.
 */
public final class ReferenceCombinator extends AtomicCombinator {
  /** The name of the referenced non-terminal. */
  public final ProdName name;

  /** */
  public ReferenceCombinator(Supplier<NodeMetadata> mds, ProdName name) {
    super(mds);
    this.name = name;
  }

  @Override
  protected ReferenceCombinator unfold(
      NodeMetadata newMetadata, Function<ProdName, ProdName> renamer) {
    ProdName newName = renamer.apply(name);
    if (newName.equals(name) && newMetadata.equals(md)) {
      return this;
    } else {
      return new ReferenceCombinator(
          Suppliers.ofInstance(newMetadata), newName);
    }
  }

  @Override
  public ParseDelta enter(Parse p) {
    Combinator referent = p.lang.get(name);
    return ParseDelta.builder(referent).push().build();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    switch (s) {
      case FAIL: return ParseDelta.fail().build();
      case PASS: return ParseDelta.pass().build();
    }
    throw new AssertionError(s);
  }


  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(
            ParseDelta.builder(lang.get(name)).push().build());
      case EXIT_FAIL:
        return ImmutableList.of(ParseDelta.fail().build());
      case EXIT_PASS:
        return ImmutableList.of(ParseDelta.pass().build());
    }
    throw new AssertionError(tt);
  }

  @Override
  protected String getVizTypeClassName() {
    return "ref";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    try (Closeable link = out.open(
        TagName.A, AttribName.HREF, "#def:" + name)) {
      out.text(name.text);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ReferenceCombinator)) {
      return false;
    }
    return name.equals(((ReferenceCombinator) o).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public Frequency consumesInput(Language lang) {
    return lang.lali.consumesInput(name);
  }

  @Override
  public ImmutableRangeSet<Integer> lookahead(Language lang) {
    return lang.lali.lookahead(name);
  }
}
