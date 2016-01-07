package com.google.template.autoesc;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.autoesc.demo.DemoServer;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.Side;
import com.google.template.autoesc.viz.AttribName;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.Visualizable;
import com.google.template.autoesc.viz.VizOutput;
import com.google.template.autoesc.combimpl.AppendOutputCombinator;
import com.google.template.autoesc.combimpl.EmptyCombinator;
import com.google.template.autoesc.combimpl.ReferenceCombinator;
import com.google.template.autoesc.combimpl.SeqCombinator;


/**
 * A self-contained bundle of named combinators that allows resolving
 * references.
 *
 * <p>
 * Languages relate non-terminals to {@link Combinator}s and are complete;
 * the right side of a production only refers to
 * {@link ProdName}s that are also left-hand sides.
 * </p>
 *
 * <p>
 * Languages are not left-recursive; recursive references can only appear on
 * paths along which input has been consumed.
 * </p>
 */
public final class Language implements Visualizable {
  /** The default start production. */
  public final ProdName defaultStartProdName;
  /** Used to compute {@link DemoServer} URLs for failing unit-tests. */
  public final Optional<String> demoServerQuery;
  /** Caches lookahead. */
  public final LookaheadLookinto lali;
  /** Maps names of non-terminals to their definitions. */
  private final ImmutableMap<ProdName, Production> byName;

  private Language(
      ProdName defaultStartProdName,
      ImmutableMap<ProdName, Def> defsByName,
      Optional<String> demoServerQuery) {
    Preconditions.checkArgument(defsByName.containsKey(defaultStartProdName));

    SerialNumberer mapMeta = new SerialNumberer();

    ImmutableMap<ProdName, Production> resolvedProds;
    {
      ImmutableMap.Builder<ProdName, Production> b = ImmutableMap.builder();
      for (Map.Entry<ProdName, Def> e : defsByName.entrySet()) {
        ProdName name = e.getKey();
        Def def = e.getValue();
        Combinator body = def.toCombinator(name, mapMeta);
        final NodeMetadata bodyMd = body.getMetadata();
        Combinators c = Combinators.using(
            Suppliers.compose(mapMeta, Suppliers.ofInstance(bodyMd)));
        b.put(name, new Production(name, body, c, def.getDocComment()));
      }
      resolvedProds = b.build();

      requireComplete(resolvedProds);
    }


    this.defaultStartProdName = defaultStartProdName;
    this.byName = resolvedProds;
    this.demoServerQuery = demoServerQuery;
    this.lali = new LookaheadLookinto(this);

    // Fail fast on LR grammars since we don't try to implement grow-the-seed.
    Set<ProdName> checked = new LinkedHashSet<>();
    Set<ProdName> onStack = new LinkedHashSet<>();
    for (Map.Entry<ProdName, Production> e : byName.entrySet()) {
      onStack.clear();
      onStack.add(e.getKey());
      checked.clear();
      checked.add(e.getKey());
      checkLeftRecursive(onStack, checked, e.getValue().body);
    }
  }

  /**
   * A minimal set of definitions that provide a complete grammar
   * that can start at any of the named productions.
   */
  public Language reachableFrom(ProdName... starts) {
    Set<ProdName> reachable = new LinkedHashSet<>();
    Set<ProdName> checked = new LinkedHashSet<>();
    reachable.addAll(Arrays.asList(starts));
    while (true) {
      Set<ProdName> toCheck = new LinkedHashSet<>(reachable);
      toCheck.removeAll(checked);
      if (toCheck.isEmpty()) { break; }
      for (ProdName nm : toCheck) {
        checked.add(nm);
        checkReferencesIn(nm, byName.get(nm).body, null, reachable);
      }
    }
    ImmutableMap.Builder<ProdName, Def> reachableByName =
        ImmutableMap.builder();
    for (Map.Entry<ProdName, Production> e : byName.entrySet()) {
      ProdName name = e.getKey();
      if (reachable.contains(name)) {
        Production p = e.getValue();
        reachableByName.put(
            name, new Def(p.body, Optional.<ProdName>absent(), p.docComment));
      }
    }
    return new Language(
        defaultStartProdName, reachableByName.build(),
        demoServerQuery);
  }

  /**
   * An optimized version that uses LA(1) to skip branches
   * that can't succeed.
   */
  public Language optimized() {
    return LookaheadOptimizer.optimize(this);
  }

  /** True iff {@link #get} with the same name will succeed. */
  public boolean has(ProdName name) {
    return byName.containsKey(name);
  }

  /**
   * The body of the named production if any.
   */
  public Combinator get(ProdName name) throws NoSuchProductionException {
    return requireProduction(name).body;
  }

  /**
   * The body of the named production followed by the end of input.
   */
  public Combinator getAtEndOfInput(ProdName name)
      throws NoSuchProductionException {
    return requireProduction(name).bodyAtEndOfInput;
  }

  /**
   * The body of the named production followed by any character.
   * This can be used to reason about epsilon transitions because the character
   * at the end is a reliable
   */
  public Combinator getRefBeforeAnyChar(ProdName name)
      throws NoSuchProductionException {
    return requireProduction(name).refBeforeAnyCharacter;
  }

  /** Any documentation comment associated with the named production. */
  public Optional<String> getDocComment(ProdName name) {
    return requireProduction(name).docComment;
  }

  private @Nonnull Production requireProduction(ProdName name)
      throws NoSuchProductionException {
    Production p = byName.get(name);
    if (p == null) {
      throw new NoSuchProductionException(name);
    }
    return p;
  }

  /**
   * A mapping between production names and bodies.  Immutable
   */
  public Map<ProdName, Combinator> byName() {
    return Maps.transformValues(byName, Production.BODY_OF);
  }


  /** A builder for a {@link Language}. */
  public static final class Builder {
    private Optional<ProdName> defaultStartProdName = Optional.absent();
    private Optional<String> demoServerQuery = Optional.absent();
    private final ImmutableMap.Builder<ProdName, Def> byName =
        ImmutableMap.builder();
    private Def lastDef;

    /** Defines a production. */
    public Builder define(ProdName name, Combinator c) {
      if (!defaultStartProdName.isPresent()) {
        defaultStartProdName = Optional.of(name);
      }
      lastDef = new Def(
          c, Optional.<ProdName>absent(), Optional.<String>absent());
      lastDef.setBounded(true);  // Until unbounded is called.
      byName.put(name, lastDef);
      return this;
    }

    /** Defines a production. */
    public Builder define(String name, Combinator c) {
      return define(new ProdName(name), c);
    }

    /**
     * Marks the last defined production as unbounded so that the output of its
     * body will not be wrapped with {@link Boundary} markers.
     */
    public Builder unbounded() {
      if (lastDef == null) {
        throw new IllegalStateException();
      }
      lastDef.setBounded(false);
      return this;
    }

    /**
     * Associates a documentation comment with the last production defined.
     */
    public Builder docComment(Optional<String> comment) {
      if (lastDef == null) {
        throw new IllegalStateException();
      }
      this.lastDef.setDocComment(comment);
      return this;
    }

    /**
     * Associates a documentation comment with the last production defined.
     */
    public Builder docComment(String commentText) {
      return docComment(Optional.of(commentText));
    }

    /**
     * Includes all definitions from the given grammar but
     * name-spaced with the given prefix.
     */
    public Builder include(String prefix, Language lang) {
      return include(new ProdName(prefix), lang);
    }

    /**
     * Includes all definitions from the given grammar but
     * name-spaced with the given prefix.
     */
    @SuppressWarnings("synthetic-access")
    public Builder include(ProdName prefix, Language lang) {
      for (Map.Entry<ProdName, Production> e : lang.byName.entrySet()) {
        ProdName prefixedName = e.getKey().withPrefix(prefix);
        Production p = e.getValue();
        byName.put(
            prefixedName,
            new Def(p.body, Optional.of(prefix), p.docComment));
      }
      return this;
    }

    /** Sets the default start production. */
    public Builder defaultStartProdName(ProdName name) {
      return defaultStartProdName(Optional.of(name));
    }

    /** Sets the default start production. */
    public Builder defaultStartProdName(Optional<ProdName> name) {
      this.defaultStartProdName = name;
      return this;
    }

    /** Sets the default start production. */
    public Builder defaultStartProdName(String name) {
      return defaultStartProdName(new ProdName(name));
    }

    /**
     * Sets the query string that failing test cases use to make it easy to
     * visualize the test run via the demo server.
     */
    public Builder demoServerQuery(Optional<String> newDemoServerQuery) {
      this.demoServerQuery = newDemoServerQuery;
      return this;
    }

    /**
     * Builds the grammar which involves checking that all references can be
     * resolved and checking for LR cycles.
     */
    @SuppressWarnings("synthetic-access")
    public Language build() {
      return new Language(
          defaultStartProdName.get(), byName.build(), demoServerQuery);
    }
  }


  private static void checkReferencesIn(
      ProdName source, Combinator c,
      @Nullable Set<ProdName> defined, @Nullable Set<ProdName> seen) {
    if (c instanceof ReferenceCombinator) {
      ProdName name = ((ReferenceCombinator) c).name;
      if (defined != null && !defined.contains(name)) {
        throw new AssertionError(
            "In " + source + ", unresolved reference " + name);
      }
      if (seen != null) {
        seen.add(name);
      }
    } else {
      for (Combinator child : c.children()) {
        checkReferencesIn(source, child, defined, seen);
      }
    }
  }

  private static void requireComplete(
      ImmutableMap<ProdName, Production> byName) {
    for (Map.Entry<ProdName, Production> e : byName.entrySet()) {
      checkReferencesIn(
          e.getKey(), e.getValue().body, byName.keySet(), null);
    }
  }

  private void checkLeftRecursive(
      Set<ProdName> onStack, Set<ProdName> checked, Combinator c) {
    ImmutableList<Combinator> children = c.children();
    if (c instanceof ReferenceCombinator) {
      ReferenceCombinator rd = (ReferenceCombinator) c;
      if (onStack.contains(rd.name)) {
        throw new LeftRecursiveReferenceException(
            ImmutableList.copyOf(onStack));
      }
      if (!checked.contains(rd.name)) {
        Combinator referent = byName.get(rd.name).body;
        checked.add(rd.name);
        onStack.add(rd.name);
        checkLeftRecursive(onStack, checked, referent);
        onStack.remove(rd.name);
      }
    } else if (c instanceof SeqCombinator) {
      for (Combinator child : children) {
        checkLeftRecursive(onStack, checked, child);
        if (lali.consumesInput(child) == Frequency.ALWAYS) { break; }
      }
    } else {
      for (Combinator child : children) {
        checkLeftRecursive(onStack, checked, child);
      }
    }
  }


  /** Raised when building a grammar that would be left-recursive. */
  public static class LeftRecursiveReferenceException extends RuntimeException {
    private static final long serialVersionUID = -2765810103919168664L;

    /**
     * @param onStack productions involved in the left-recursive loop.
     */
    public LeftRecursiveReferenceException(Iterable<ProdName> onStack) {
      super(Joiner.on(", ").join(onStack));
    }
  }


  /**
   * Writes out productions in {@code Name ":=" Body ";"} form.
   * @param lvl if {@link DetailLevel#LONG} then will include
   *     imported productions.
   */
  @Override
  public void visualize(DetailLevel lvl, VizOutput out)
      throws IOException {
    try (Closeable tbl = out.open(
        TagName.TABLE, AttribName.ID, "grammar", AttribName.CLASS, "grammar")) {
      Iterator<Map.Entry<ProdName, Production>> it
          = byName.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<ProdName, Production> e = it.next();
        ProdName name = e.getKey();
        if (lvl.compareTo(DetailLevel.LONG) < 0
            && name.text.indexOf('.') >= 0) {
          continue;
        }
        Production p = e.getValue();
        Combinator body = p.body;
        String anchorId = "def:" + name;  // See ReferenceCombinator
        Optional<Combinator> unmarkedBody = stripMarkersFromBody(name, body);
        boolean isMarked = unmarkedBody.isPresent();
        String decoratedName = isMarked ? "<" + name + ">" : name.text;
        if (p.docComment.isPresent()) {
          String commentText = p.docComment.get();
          StringBuilder fullComment = new StringBuilder();
          fullComment.append("/**\n * ");
          for (String commentLine : commentText.split("\r\n?|\n")) {
            fullComment.append(" * ")
                .append(commentLine.replace("*/", "* /"))
                .append('\n');
          }
          fullComment.append(" */");
          try (Closeable tr = out.open(TagName.TR)) {
            try (Closeable th = out.open(
                TagName.TH,
                AttribName.CLASS, "comment",
                AttribName.COLSPAN, "2")) {
              out.text(fullComment.toString());
            }
          }
        }
        try (Closeable tr = out.open(TagName.TR)) {
          try (Closeable th = out.open(
              TagName.TH, AttribName.ID, anchorId, AttribName.CLASS, "def")) {
            out.text(decoratedName);
          }
          try (Closeable th = out.open(TagName.TH)) {
            out.text(":=");
          }
          try (Closeable th = out.open(TagName.TD)) {
            unmarkedBody.or(body).visualize(DetailLevel.LONG, out);
          }
          if (it.hasNext()) {
            out.text(";");
          }
        }
        if (it.hasNext()) {
          out.text("\n");
        }
      }
    }
  }


  private static Optional<Combinator> stripMarkersFromBody(
      ProdName name, Combinator c) {
    if (c instanceof SeqCombinator) {
      SeqCombinator seq = (SeqCombinator) c;
      if (seq.first instanceof AppendOutputCombinator) {
        AppendOutputCombinator startMarker = (AppendOutputCombinator) seq.first;
        if (startMarker.output instanceof Boundary) {
          Boundary boundary = (Boundary) startMarker.output;
          if (boundary.side == Side.LEFT
              && boundary.prodName.equals(name)) {
            return stripEndMarkerFromBody(name, seq.second);
          }
        }
      }
    }
    return Optional.absent();
  }

  private static Optional<Combinator> stripEndMarkerFromBody(
      ProdName name, Combinator c) {
    if (c instanceof AppendOutputCombinator) {
      AppendOutputCombinator startMarker = (AppendOutputCombinator) c;
      if (startMarker.output instanceof Boundary) {
        Boundary boundary = (Boundary) startMarker.output;
        if (boundary.side == Side.RIGHT
            && boundary.prodName.equals(name)) {
          return Optional.<Combinator>of(EmptyCombinator.INSTANCE);
        }
      }
    } else if (c instanceof SeqCombinator) {
      SeqCombinator seq = (SeqCombinator) c;
      Optional<Combinator> stripped = stripEndMarkerFromBody(name, seq.second);
      if (stripped.isPresent()) {
        Combinator newSecond = stripped.get();
        if (newSecond == EmptyCombinator.INSTANCE) {
          return Optional.of(seq.first);
        } else {
          return Optional.of(seq.unfold(
              seq.getMetadata(), Functions.<ProdName>identity(),
              ImmutableList.of(seq.first, newSecond)));
        }
      }
    }
    return Optional.absent();
  }
}


final class Production {
  final Combinator body;
  final Combinator bodyAtEndOfInput;
  final Combinator refBeforeAnyCharacter;
  final Optional<String> docComment;

  Production(
      ProdName name, Combinator body, Combinators c,
      Optional<String> docComment) {
    this.body = body;
    this.bodyAtEndOfInput = c.seq(body, c.endOfInput());
    this.refBeforeAnyCharacter = c.seq(c.ref(name), c.la(c.anyChar()));
    this.docComment = docComment;
  }

  static final Function<Production, Combinator> BODY_OF =
      new Function<Production, Combinator>() {

    @Override
    public Combinator apply(Production p) {
      if (p == null) { throw new IllegalArgumentException(); }
      return p.body;
    }

  };
}


final class Def {
  final Combinator c;
  final Optional<ProdName> prefixOpt;
  private Optional<String> docComment;
  private boolean bounded;

  Def(Combinator c, Optional<ProdName> prefix, Optional<String> docComment) {
    this.c = c;
    this.prefixOpt = prefix;
    this.docComment = docComment;
  }

  void setBounded(boolean newBounded) {
    this.bounded = newBounded;
  }

  void setDocComment(Optional<String> newDocComment) {
    if (newDocComment.isPresent() && newDocComment.get().contains("*/")) {
      throw new IllegalArgumentException(newDocComment.toString());
    }
    this.docComment = newDocComment;
  }

  Optional<String> getDocComment() {
    return docComment;
  }

  Combinator toCombinator(
      ProdName name, Function<NodeMetadata, NodeMetadata> mapMeta) {
    Function<ProdName, ProdName> mapName = Functions.identity();
    if (prefixOpt.isPresent()) {
      mapName = new PrefixIfNoDot(prefixOpt.get());
    }

    Supplier<NodeMetadata> mds = Suppliers.ofInstance(c.getMetadata());
    Combinators combinators = Combinators.using(mds);

    Combinator body = c;
    if (bounded) {
      body = combinators.seq(
          new AppendOutputCombinator(mds, new Boundary(Side.LEFT, name)),
          body,
          new AppendOutputCombinator(mds, new Boundary(Side.RIGHT, name)));
    }
    return mapCombinatorDeep(mapMeta, mapName, body);
  }

  static Combinator mapCombinatorDeep(
      Function<NodeMetadata, NodeMetadata> mapMeta,
      Function<ProdName, ProdName> mapName,
      Combinator c) {
    NodeMetadata metadata = c.getMetadata();
    NodeMetadata newMetadata = mapMeta.apply(metadata);
    if (newMetadata.equals(metadata)) {
      newMetadata = metadata;
    }

    ImmutableList<Combinator> children = c.children();
    ImmutableList<Combinator> newChildren = children;
    {
      ImmutableList.Builder<Combinator> b = null;
      for (int i = 0, n = children.size(); i < n; ++i) {
        Combinator child = children.get(i);
        Combinator newChild = mapCombinatorDeep(mapMeta, mapName, child);
        if (b == null && newChild != child) {
          b = ImmutableList.builder();
          b.addAll(children.subList(0,  i));
        }
        if (b != null) {
          b.add(newChild);
        }
      }
      if (b != null) {
        newChildren = b.build();
      }
    }

    return c.unfold(newMetadata, mapName, newChildren);
  }
}


final class PrefixIfNoDot implements Function<ProdName, ProdName> {
  final ProdName prefix;

  PrefixIfNoDot(ProdName prefix) {
    this.prefix = prefix;
  }

  @Override
  public ProdName apply(ProdName name) {
    if (name != null && name.text.indexOf('.') < 0) {
      return name.withPrefix(prefix);
    }
    return name;
  }
}


final class SerialNumberer implements Function<NodeMetadata, NodeMetadata> {
  private int indexCounter = 1;

  @Override
  public NodeMetadata apply(NodeMetadata md) {
    if (md == null) { throw new IllegalArgumentException(); }
    int index = indexCounter;
    Preconditions.checkState(index >= 0, "underflow");
    ++indexCounter;
    return new NodeMetadata(md.source, index, md.docComment);
  }
}