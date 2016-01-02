package com.google.template.autoesc.combimpl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseDelta;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.Success;
import com.google.template.autoesc.TransitionType;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.OutputContext;
import com.google.template.autoesc.out.Side;

/**
 * A reference combinator that demarcates the output produced by the referent
 * with {@link Boundary} markers of the same name.
 */
public class BoundedReferenceCombinator extends ReferenceCombinator {

  /** */
  public BoundedReferenceCombinator(Supplier<NodeMetadata> mds, ProdName name) {
    super(mds, name);
  }

  @Override
  protected BoundedReferenceCombinator makeInstanceOfSameClass(
      NodeMetadata newMetadata, ProdName newName) {
    return new BoundedReferenceCombinator(
        Suppliers.ofInstance(newMetadata), newName);
  }

  @Override
  public ParseDelta enter(Parse p) {
    return ParseDelta.builder(super.enter(p))
        .withOutput(new Boundary(Side.LEFT, name))
        .push()
        .build();
  }

  @Override
  public ParseDelta exit(Parse p, Success s) {
    switch (s) {
      case FAIL:
        return super.exit(p, s);
      case PASS:
        return ParseDelta.builder(super.exit(p, s))
            .withOutput(new Boundary(Side.RIGHT, name))
            .build();
    }
    throw new AssertionError(s.toString());
  }

  @Override
  public ImmutableList<ParseDelta> epsilonTransition(
      TransitionType tt, Language lang, OutputContext ctx) {
    switch (tt) {
      case ENTER:
        return ImmutableList.of(ParseDelta
            .builder(lang.get(name))
            .push()
            .withOutput(new Boundary(Side.LEFT, name))
            .build());
      case EXIT_FAIL:
        return ImmutableList.of(ParseDelta.fail().build());
      case EXIT_PASS:
        return ImmutableList.of(ParseDelta
            .pass()
            .withOutput(new Boundary(Side.RIGHT, name))
            .build());
    }
    throw new AssertionError(tt);
  }

}
