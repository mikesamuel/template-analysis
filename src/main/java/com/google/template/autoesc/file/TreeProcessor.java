package com.google.template.autoesc.file;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.FList;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.NodeMetadata;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.out.Boundary;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.PartialOutput;
import com.google.template.autoesc.out.PartialOutput.BoundedRegion;
import com.google.template.autoesc.out.StringOutput;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;


/**
 * Recursively applies output processors to parse-tree nodes to derive
 * a parse result.
 *
 * <p>
 * This {@linkplain OutputProcessor#pre pre-processes} in a pre-order walk and
 * {@linkplain OutputProcessor#post post-processes} in a post-order walk and
 * keeps track of tokens to
 */
final class TreeProcessor {

  private static final boolean DEBUG = false;

  /** The index into the input of the character being processed. */
  private int pos;
  /**
   * Null or a documentation comment that is separated from the input
   * character at pos by only whitespace characters.
   */
  private DocComment docComment;
  /** The index into the input of the start of the node being processed. */
  private int nodeStartPos;
  /**
   * Null or a documentation comment that is separated from the node
   * start by only whitespace characters.
   */
  private DocComment nodeDocComment;
  private final LineMap lineMap;
  private final List<Production> productions = new ArrayList<>();
  private final Supplier<NodeMetadata> mds = new Supplier<NodeMetadata>() {
    @SuppressWarnings("synthetic-access")
    @Override
    public NodeMetadata get() {
      return new NodeMetadata(
          lineMap.atCharIndex(nodeStartPos), 0, consumeDocComment());
    }
  };
  private final List<Message> messages = new ArrayList<>();
  /**
   * Can be used by output processors to create combinators that will be
   * associated with the correct source location and doc comment.
   */
  final Combinators combinators = Combinators.using(mds);
  final Types types;
  /**
   * As variables are defined in a parse unit, we keep a relation between
   * names and types so that authors need not
   */
  private final Map<String, Variable<?>> previouslyDeclaredVariables =
    new LinkedHashMap<>();


  TreeProcessor(LineMap lineMap, Types types) {
    this.lineMap = lineMap;
    this.types = types;
  }

  void setDocComment(String docComment) {
    this.docComment = new DocComment(docComment);
  }

  Optional<String> consumeDocComment() {
    if (this.nodeDocComment != null && !this.nodeDocComment.consumed) {
      this.nodeDocComment.consumed = true;
      return Optional.of(this.nodeDocComment.text);
    }
    return Optional.absent();
  }

  /** Appends a message with the error flag set. */
  void error(String s) {
    messages.add(new Message(s, true, lineMap.atCharIndex(nodeStartPos)));
  }

  /** Messages resulting from parse tree processing. */
  ImmutableList<Message> getMessages() {
    return ImmutableList.copyOf(messages);
  }

  Variable<?> defineVariable(Variable<?> var) {
    Variable<?> prior = previouslyDeclaredVariables.get(var.name);
    if (prior != null) {
      if (!var.equals(prior)) {
        error(
            "conflicting variable declaration "
                + TextVizOutput.vizToString(prior, DetailLevel.LONG) + " and "
                + TextVizOutput.vizToString(var, DetailLevel.LONG));
      }
      return prior;
    } else {
      previouslyDeclaredVariables.put(var.name, var);
    }
    return var;
  }

  Optional<Variable<?>> getPreviouslyDeclaredVariable(String name) {
    return Optional.<Variable<?>>fromNullable(
        previouslyDeclaredVariables.get(name));
  }

  void defineProductions(Language.Builder b) {
    for (Production p : this.productions) {
      b.define(p.name, p.c);
    }
  }

  void process(FList<Output> out) {
    PartialOutput.Root po = PartialOutput.of(out);
    ImmutableList.Builder<Object> topLevelElementsParsed =
        ImmutableList.builder();
    for (PartialOutput topLevelElement : po.getBody()) {
      processOutput(topLevelElement, topLevelElementsParsed);
    }
    for (Object element : topLevelElementsParsed.build()) {
      productions.add((Production) element);
    }
  }

  private void processOutput(
      PartialOutput po, ImmutableList.Builder<Object> parts) {
    Output o = po.getOutput().get();
    if (po instanceof PartialOutput.BoundedRegion) {
      PartialOutput.BoundedRegion br = (BoundedRegion) po;
      if (o instanceof Boundary) {
        Boundary b = (Boundary) o;
        ProdName name = b.prodName;
        OutputProcessor<?> op = GrammarGrammar.OUTPUT_PROCESSORS.get(name);
        if (op != null) {
          int brStartPos = pos;
          DocComment docCommentAtStart = this.docComment;

          this.nodeStartPos = brStartPos;
          Optional<?> result = op.pre(br.body, this);

          ImmutableList.Builder<Object> childObjects = ImmutableList.builder();
          for (PartialOutput child : br.body) {
            processOutput(child, childObjects);
          }

          if (!result.isPresent()) {
            // Reset the indices and doc comment visible to the output
            // processor.
            this.nodeStartPos = brStartPos;
            this.nodeDocComment = docCommentAtStart;
            ImmutableList<Object> children = childObjects.build();
            if (DEBUG) {
              System.err.println("for " + name + ", got " + children);
            }
            result = op.post(children, this);
          }

          if (result.isPresent()) {
            parts.add(result.get());
          }
          return;
        } else {
          if (DEBUG) {
            System.err.println("Unbound " + name);
          }
        }
      }
      for (PartialOutput child : br.body) {
        processOutput(child, parts);
      }
    } else if (o instanceof StringOutput) {
      StringOutput so = (StringOutput) o;
      pos += so.rawChars.length();
      if (so.s.trim().length() != 0) {
        this.docComment = null;
      }
    }
  }

}


final class Production {
  final ProdName name;
  final Combinator c;

  Production(ProdName name, Combinator c) {
    this.name = name;
    this.c = c;
  }
}


final class DocComment {
  final String text;
  boolean consumed;
  DocComment(String text) {
    this.text = text;
  }
}
