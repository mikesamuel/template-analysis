package com.google.template.autoesc.demo;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.template.autoesc.ParseWatcher;
import com.google.template.autoesc.Parser;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.UnjoinableException;
import com.google.template.autoesc.inp.RawCharsInputCursor;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.viz.AttribName;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * Executes branches by forking and joining parsers as necessary.
 */
public final class BranchRunner<BRANCH extends BranchRunner.Branch<BRANCH>> {
  private final Map<BRANCH, Integer> satCounts = new HashMap<>();
  private final Multimap<BRANCH, Parser> parsersPreceding
      = HashMultimap.create();
  private final BRANCH startBranch;
  // TODO: move this startProdName out of branch into some larger context.
  // Since only one branch can be the start branch, it is effectively global to
  // a parse run.
  private final ProdName startProdName;
  private final boolean callFinishParse;
  private BRANCH currentBranch;

  /** The current branch being run if any. */
  public BRANCH getCurrentBranch() {
    return currentBranch;
  }

  /**
   * @param startBranch branch at which parsing starts.
   * @param startProdName the name of the production to start at if this branch
   *     is not preceded by any other branches.
   * @param callFinishParse true to finish the parse if this branch is not
   *     followed by any other branches.
   */
  public BranchRunner(
      BRANCH startBranch, ProdName startProdName, boolean callFinishParse) {
    this.startBranch = startBranch;
    this.startProdName = startProdName;
    this.callFinishParse = callFinishParse;

    // Count the number of times each branch appears as a follower so we can
    // run branches once their preceders have all been run.
    satCounts.put(startBranch, 0);
    computeSatCounts(startBranch);
  }

  /**
   * @param startBranch branch at which parsing starts.
   * @param startProdName the name of the production to start at if this branch
   *     is not preceded by any other branches.
   */
  public BranchRunner(BRANCH startBranch, ProdName startProdName) {
    this(startBranch, startProdName, true);
  }

  private void computeSatCounts(BRANCH b) {
    for (BRANCH f : b.followers) {
      if (!satCounts.containsKey(f)) {
        satCounts.put(f, 1);
        computeSatCounts(f);
      } else {
        satCounts.put(f, 1 + satCounts.get(f));
      }
    }
  }

  /**
   * Runs all branches.
   */
  public void run(Parser p) throws UnjoinableException {
    run(startBranch, p, true);
    this.currentBranch = null;
  }

  private void run(BRANCH b, Parser p, boolean isStartBranch)
  throws UnjoinableException {
    this.currentBranch = b;

    assert satCounts.get(b).intValue() == parsersPreceding.get(b).size();
    if (isStartBranch) {
      p.startParse(startProdName);
    }

    for (StringSourcePair inp : b.inputs) {
      p.addInput(inp.text, inp.src);
      p.continueParse();
    }

    boolean isFinished = false;
    switch (p.getState()) {
      case NOT_STARTED: throw new IllegalStateException();
      case PASSED:
      case FAILED:
        isFinished = true;
        break;
      case IN_PROGRESS:
        isFinished = b.followers.isEmpty();
        break;
    }

    if (isFinished) {
      if (callFinishParse) {
        p.finishParse();
      } else {
        p.getWatcher().finished(p.getParse(), p.getBranch(), p.getState());
      }
    }

    if (isFinished) {
      return;
    }

    // Update satCountsRem for followers.
    List<BRANCH> todo = new ArrayList<>();
    for (BRANCH f : b.followers) {
      int n = satCounts.get(f);
      Collection<Parser> parsersPrecedingF = this.parsersPreceding.get(f);
      parsersPrecedingF.add(p);
      if (n == parsersPrecedingF.size()) {
        todo.add(f);
      }
    }

    for (BRANCH newlySatisfied : todo) {
      Collection<Parser> parsersPrecedingF = this.parsersPreceding.get(
          newlySatisfied);
      boolean isFork = 1 == parsersPrecedingF.size();
      run(
          newlySatisfied,
          isFork
          ? p.fork(newlySatisfied)
          : Parser.join(
              ImmutableList.copyOf(parsersPrecedingF), newlySatisfied),
          false);
    }
  }


  /**
   * Parses can fork and join so a branch is an edge in that DAG.
   */
  public static class Branch<BRANCH extends Branch<BRANCH>>
  implements ParseWatcher.Branch {
    /** Name of the branch. */
    public final String name;
    /** Chunks of input to feed to the parser in order. */
    public final ImmutableList<StringSourcePair> inputs;
    /** Branches that are forked from this branch or into which it joins. */
    public final ImmutableList<BRANCH> followers;

    /** */
    public Branch(
        String name,
        ImmutableList<StringSourcePair> inputs,
        ImmutableList<BRANCH> followers) {
      this.name = name;
      this.inputs = inputs;
      this.followers = followers;
    }

    @Override
    public String toString() {
      return name;
    }

    /** Reference identity. */
    @Override
    public final boolean equals(Object o) { return super.equals(o); }
    /** Reference identity. */
    @Override
    public final int hashCode() { return super.hashCode(); }

    @Override
    public void visualize(DetailLevel lvl, VizOutput out) throws IOException {
      try (Closeable c = out.open(TagName.SPAN, AttribName.CLASS, "branch")) {
        out.text(name);
      }
    }
  }

  /**
   * A chunk of input that can be appended to an input cursor.
   */
  public static final class StringSourcePair {
    /** Raw input chars. */
    public final String text;
    /** The source of {@link #text}. */
    public final Source src;

    /** */
    public StringSourcePair(String text, Source src) {
      this.text = text;
      this.src = src;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append('"');
      sb.append(RawCharsInputCursor.STRING_ESCAPER.escape(text));
      sb.append("\"@");
      sb.append(src);
      return sb.toString();
    }
  }
}
