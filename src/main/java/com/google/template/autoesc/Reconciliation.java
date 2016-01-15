package com.google.template.autoesc;

import static com.google.template.autoesc.viz.TextTables.column;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.autoesc.combimpl.OrCombinator;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.out.BinaryOutput;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.PartialOutput;
import com.google.template.autoesc.out.PartialOutput.StandalonePartialOutput;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.out.UnaryOutput;
import com.google.template.autoesc.var.MultiVariable;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.var.Value;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextTables;
import com.google.template.autoesc.viz.TextVizOutput;
import com.google.template.autoesc.viz.VizOutput;

import static com.google.template.autoesc.Parse.consOutput;

/**
 * Joins two parse states to produce a single unified parse state.
 * <p>
 * When two branches through a template are joined, we need to try to produce
 * a single parse state.
 */
final class Reconciliation {
  static final LogDetailLevel LOG = LogDetailLevel.HIGH_LEVEL;

  static void dumpState(
      Parse a, Parse b,
      @Nullable ImmutableList<Combinator> goalStack, String mod,
      boolean stackOnly) {
    if (LOG.shouldLogEventAtLevel(LogDetailLevel.HIGH_LEVEL)) {
      if (!stackOnly) {
        System.out.println("inp   a" + mod + "=" + a.inp);
        System.out.println("inp   b" + mod + "=" + b.inp);
        System.out.println();
      }
      ImmutableList.Builder<TextTables.Col> stacks = ImmutableList.builder();
      stacks.add(column(("stack a" + mod).trim(), a.stack));
      stacks.add(column(("stack b" + mod).trim(), b.stack));
      if (goalStack != null) {
        stacks.add(column("goal", goalStack.reverse()));
      }
      TextTables.appendTable(System.out, stacks.build());
      if (!stackOnly) {
        System.out.println();
        TextTables.appendTable(
            System.out,
            column(("out   a" + mod).trim(), a.out),
            column(("out   b" + mod).trim(), b.out));
      }
    }
  }

  static InputCursor reconcileInputs(ImmutableList<Parse> joinStates)
  throws UnjoinableException {
    Preconditions.checkArgument(!joinStates.isEmpty());
    Parse joinState0 = joinStates.get(0);
    InputCursor inp0 = joinState0.inp;
    for (Parse joinState : joinStates.subList(1, joinStates.size())) {
      InputCursor inp = joinState.inp;
      if (!inp0.equals(inp)) {
        throw new UnjoinableException(joinState0, inp0, joinState, inp);
      }
    }
    return inp0;
  }

  static FList<Output> reconcileOutputs(ImmutableList<Parse> joinStates)
  throws UnjoinableException {
    List<LinkedList<Output>> unreconciledOutputs = ImmutableList.copyOf(
        Lists.transform(
            joinStates,
            new Function<Parse, LinkedList<Output>>() {
              @Override
              public LinkedList<Output> apply(Parse p) {
                if (p == null) { throw new IllegalArgumentException(); }
                return new LinkedList<>(filterOutput(p.out).toList());
              }
            })
        );

    FList<Output> reconciledOutputsRev = FList.empty();
    while (true) {
      boolean allEmpty = true;
      for (LinkedList<Output> outs : unreconciledOutputs) {
        if (!outs.isEmpty()) {
          allEmpty = false;
          break;
        }
      }
      if (allEmpty) { break; }
      boolean strategyApplied = false;
      for (OutputReconciliationStrategy strategy
          : Reconciliation.OUTPUT_RECONCILIATION_STRATEGIES) {
        Optional<Output> o = applyStrategy(
            strategy, unreconciledOutputs, joinStates);
        if (o.isPresent()) {
          reconciledOutputsRev = consOutput(o.get(), reconciledOutputsRev);
          strategyApplied = true;
          break;
        }
      }
      Preconditions.checkState(strategyApplied);
    }
    return reconciledOutputsRev.rev();
  }


  static FList<Combinator> filterStack(FList<Combinator> stack) {
    return stack.filter(NotOr.INSTANCE);
  }

  static FList<Output> filterOutput(FList<Output> out) {
    // TODO: Drop any StringOutputs that appear before the first
    // ephemeral marker.
    if (LOG.shouldLogEventAtLevel(LogDetailLevel.DETAILED)) {
      System.out.println("\nFiltering output\n\t" + out);
    }
    PartialOutput po = PartialOutput.of(out);
    if (LOG.shouldLogEventAtLevel(LogDetailLevel.DETAILED)) {
      System.out.print("Using\n" + po.toString(1));
    }
    FList<Output> filtered = rebuildRelevant(po, FList.<Output>empty());
    if (LOG.shouldLogEventAtLevel(LogDetailLevel.DETAILED)) {
      System.out.println("Filtered to\n\t" + filtered);
    }
    return filtered;
  }

  private static FList<Output> rebuildRelevant(
      PartialOutput po, FList<Output> out) {
    Optional<Output> oOpt = po.getOutput();
    boolean relevant = false;
    FList<Output> result = out;
    if (oOpt.isPresent()) {
      Output o = oOpt.get();
      relevant = o.isParseRelevant(po);
      if (relevant) {
        result = consOutput(o, result);
      }
    }
    for (PartialOutput bodyPart : tryToReorder(po.getBody())) {
      result = rebuildRelevant(bodyPart, result);
    }
    if (relevant) {
      Optional<BinaryOutput> rightOpt = po.getRightSideOutput();
      if (rightOpt.isPresent()) {
        result = consOutput(rightOpt.get(), result);
      }
    }
    return result;
  }

  /**
   * Try to canonicalize outputs as much as possible so that different branches
   * which set variables, for example, don't need to set them in the same order.
   * <p>
   * This gives the output reconciliation strategies that run later more
   * latitude.
   */
  private static List<PartialOutput> tryToReorder(
      ImmutableList<PartialOutput> outputs) {
    int n = outputs.size();
    for (int i = 0; i < n; ++i) {
      PartialOutput po = outputs.get(i);
      if (canReorder(po)) {
        List<StandalonePartialOutput> reorderable = new ArrayList<>();
        List<PartialOutput> unreorderable =
            new ArrayList<>(outputs.subList(0, i));
        for (; i < n; ++i) {
          po = outputs.get(i);
          if (canReorder(po)) {
            reorderable.add((StandalonePartialOutput) po);
          } else {
            unreorderable.add(po);
          }
        }

        Collections.sort(
            reorderable,
            new Comparator<StandalonePartialOutput>() {
              @Override
              public int compare(
                  StandalonePartialOutput a, StandalonePartialOutput b) {
                return ((UnaryOutput) a.output).compareTo(
                    (UnaryOutput) b.output);
              }
            });

        return ImmutableList.<PartialOutput>builder()
            .addAll(reorderable)
            .addAll(unreorderable)
            .build();
      }
    }
    return outputs;
  }

  private static boolean canReorder(PartialOutput po) {
    if (po instanceof StandalonePartialOutput) {
      PartialOutput.StandalonePartialOutput spo = (StandalonePartialOutput) po;
      if (spo.output instanceof UnaryOutput) {
        return (((UnaryOutput) spo.output).canReorder());
      }
    }
    return false;
  }

  private interface OutputReconciliationStrategy {
    boolean reconcilesWith(Output prior, Output current);
    Optional<Output> reconcile(
        Parse p, Optional<Output> prior, Parse q, Output current)
    throws UnjoinableException;
  }

  private static final ImmutableList<OutputReconciliationStrategy>
      OUTPUT_RECONCILIATION_STRATEGIES
      = ImmutableList.<OutputReconciliationStrategy>of(
          new OutputReconciliationStrategy() {
            @Override
            public boolean reconcilesWith(Output prior, Output current) {
              return current instanceof StringOutput;
            }
            @Override
            public Optional<Output> reconcile(
                Parse p, Optional<Output> prior, Parse q, Output current) {
              if (prior.isPresent()) {
                Preconditions.checkArgument(
                    prior.get() instanceof StringOutput);
                return prior;
              }
              if (current instanceof StringOutput) {
                return Optional.of(current);
              }
              return Optional.absent();
            }
          },
          new OutputReconciliationStrategy() {
            @Override
            public boolean reconcilesWith(Output prior, Output current) {
              return current instanceof Value && prior instanceof Value
                  && ((Value<?>) current).getVariable().equals(
                      ((Value<?>) prior).getVariable());
            }
            @Override
            public Optional<Output> reconcile(
                Parse p, Optional<Output> prior, Parse q, Output current) {
              if (prior.isPresent()) {
                Value<?> x = (Value<?>) prior.get();
                Variable<?> var = x.getVariable();
                if (var instanceof MultiVariable) {
                  MultiVariable<?> multivar = (MultiVariable<?>) var;
                  if (current instanceof Value) {
                    Value<?> y = (Value<?>) current;
                    if (var.equals(y.getVariable())) {
                      // Intersect.
                      return Optional.<Output>of(multivar.intersection(x, y));
                    }
                  } else {
                    return Optional.<Output>of(multivar.emptyValue());
                  }
                }
                return prior;
              }
              if (current instanceof Value) {
                Value<?> y = (Value<?>) current;
                if (y.getVariable() instanceof MultiVariable) {
                  return Optional.<Output>of(y);
                }
              }
              return Optional.absent();
            }
          },
          new OutputReconciliationStrategy() {
            @Override
            public boolean reconcilesWith(Output prior, Output current) {
              return prior.equals(current);
            }

            @SuppressWarnings("synthetic-access")
            @Override
            public Optional<Output> reconcile(
                Parse p, Optional<Output> prior, Parse q, Output current)
            throws UnjoinableException {
              if (!prior.isPresent()) {
                if (!NO_MORE_OUTPUT.equals(current)) {
                  return Optional.of(current);
                }
              } else if (prior.get().equals(current)) {
                return prior;
              }
              Output pOut = prior.or(NO_MORE_OUTPUT);
              Output qOut = current;
              if (!(NO_MORE_OUTPUT.equals(pOut)
                    && NO_MORE_OUTPUT.equals(qOut))) {
                throw new UnjoinableException(
                    "Outputs cannot be reconciled",
                    p, pOut, q, qOut);
              }
              return Optional.absent();
            }
          });

  private static Optional<Output> applyStrategy(
      OutputReconciliationStrategy strategy,
      List<LinkedList<Output>> outputLists,
      ImmutableList<Parse> joinStates)
  throws UnjoinableException {
    Optional<Output> prior = Optional.<Output>absent();
    Parse p = joinStates.get(0);
    for (int i = 0, n = outputLists.size(); i < n; ++i) {
      LinkedList<Output> outputList = outputLists.get(i);
      Output current = outputList.isEmpty()
          ? NO_MORE_OUTPUT : outputList.getFirst();
      Parse q = joinStates.get(i);
      Optional<Output> newPrior = strategy.reconcile(p, prior, q, current);
      if (!prior.isPresent() && newPrior.isPresent()) {
        p = q;
      }
      prior = newPrior;
    }
    if (prior.isPresent()) {
      for (LinkedList<Output> outputList : outputLists) {
        if (!outputList.isEmpty()
            && strategy.reconcilesWith(prior.get(), outputList.get(0))) {
          outputList.removeFirst();
        }
      }
    }
    return prior;
  }

  private static final Output NO_MORE_OUTPUT = new UnaryOutput() {

    @Override
    public Optional<Output> coalesceWithFollower(Output next) {
      return Optional.absent();
    }

    @Override
    public boolean isParseRelevant(PartialOutput po) {
      return true;
    }

    @Override
    public boolean equals(Object o) {
      return o == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    protected String getVizTypeClassName() {
      return "no-more-output";
    }

    @Override
    protected void visualizeBody(DetailLevel lvl, VizOutput out)
    throws IOException {
      out.text("None");
    }

    @Override
    protected int compareToSameClass(UnaryOutput that) {
      Preconditions.checkState(that == this);
      return 0;
    }
  };
}


final class NotOr implements Predicate<Combinator> {
  static final NotOr INSTANCE = new NotOr();

  private NotOr() {
    // Singleton
  }

  @Override
  public boolean apply(@Nullable Combinator c) {
    return !(c instanceof OrCombinator);
  }
}
