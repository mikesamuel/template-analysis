package com.google.template.autoesc;

import java.util.Arrays;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.autoesc.demo.DemoServer;
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
public final class Language {
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

    final Function<NodeMetadata, NodeMetadata> mapMeta =
        new Function<NodeMetadata, NodeMetadata>() {
          private int indexCounter = 1;

          @Override
          public NodeMetadata apply(NodeMetadata md) {
            int index = indexCounter;
            Preconditions.checkState(index >= 0, "underflow");
            ++indexCounter;
            return new NodeMetadata(md.source, index, md.docComment);
          }
    };

    ImmutableMap<ProdName, Production> resolvedProds;
    {
      ImmutableMap.Builder<ProdName, Production> b = ImmutableMap.builder();
      for (Map.Entry<ProdName, Def> e : defsByName.entrySet()) {
        ProdName name = e.getKey();
        Combinator body = e.getValue().toCombinator(mapMeta);
        final NodeMetadata bodyMd = body.getMetadata();
        Combinators c = Combinators.using(new Supplier<NodeMetadata>() {
          @Override
          public NodeMetadata get() {
            return mapMeta.apply(bodyMd);
          }
        });
        b.put(name, new Production(name, body, c));
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
      if (reachable.contains(e.getKey())) {
        reachableByName.put(e.getKey(), new Def(e.getValue().body));
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

    /** Defines a production. */
    public Builder define(ProdName name, Combinator c) {
      if (!defaultStartProdName.isPresent()) {
        defaultStartProdName = Optional.of(name);
      }
      byName.put(name, new Def(c));
      return this;
    }

    /** Defines a production. */
    public Builder define(String name, Combinator c) {
      return define(new ProdName(name), c);
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
        byName.put(
            e.getKey().withPrefix(prefix),
            new Def(e.getValue().body, prefix));
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
}


final class Production {
  final Combinator body;
  final Combinator bodyAtEndOfInput;
  final Combinator refBeforeAnyCharacter;

  Production(ProdName name, Combinator body, Combinators c) {
    this.body = body;
    this.bodyAtEndOfInput = c.seq(body, c.endOfInput());
    this.refBeforeAnyCharacter = c.seq(c.ref(name), c.la(c.anyChar()));
  }

  static final Function<Production, Combinator> BODY_OF =
      new Function<Production, Combinator>() {

    @Override
    public Combinator apply(Production p) {
      return p.body;
    }

  };
}


final class Def {
  final Combinator c;
  final Optional<ProdName> prefixOpt;

  Def(Combinator c, ProdName prefix) {
    this.c = c;
    this.prefixOpt = Optional.of(prefix);
  }

  Def(Combinator c) {
    this.c = c;
    this.prefixOpt = Optional.absent();
  }

  int indexCounter = 0;

  Combinator toCombinator(Function<NodeMetadata, NodeMetadata> mapMeta) {
    Function<ProdName, ProdName> mapName = Functions.identity();
    if (prefixOpt.isPresent()) {
      final ProdName prefix = prefixOpt.get();
      mapName = new Function<ProdName, ProdName>() {
        @Override
        public ProdName apply(ProdName name) {
          if (name.text.indexOf('.') < 0) {
            return name.withPrefix(prefix);
          }
          return name;
        }
      };
    }
    return mapCombinatorDeep(mapMeta, mapName, c);
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