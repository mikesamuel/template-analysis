package com.google.template.autoesc.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Completion;
import com.google.template.autoesc.FList;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseWatcher;
import com.google.template.autoesc.Parser;
import com.google.template.autoesc.TeeParseWatcher;
import com.google.template.autoesc.combimpl.ReferenceCombinator;
import com.google.template.autoesc.inp.InputCursor;
import com.google.template.autoesc.inp.Source;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;

/**
 * Parses a language using a syntax similar to the
 * {@linkplain Combinator#visualize visualize} syntax.
 */
public class LanguageParser {
  private ParseWatcher watcher = TeeParseWatcher.DO_NOTHING_WATCHER;
  private final Language.Builder builder = new Language.Builder();
  private List<Message> messages = new ArrayList<>();
  private Parser parser = null;
  private LineMap lineMap = null;
  private Types types = Types.EMPTY;
  private final LongestParseWatcher longestParseWatcher
      = new LongestParseWatcher();

  /**
   * Sets the parse watcher used during parsing.
   */
  public void setParseWatcher(ParseWatcher pw) {
    this.watcher = pw;
  }

  /**
   * Sets the types instance used to resolve names to types.
   */
  public void setTypes(Types types) {
    this.types = types;
  }

  /**
   * Parses productions from the given input.
   */
  public void parse(String inp, Source src) {
    if (parser == null) {
      parser = new Parser(
          LanguageParserBranch.INSTANCE,
          GrammarGrammar.LANG,
          TeeParseWatcher.create(watcher, longestParseWatcher));
      parser.startParse();
      lineMap = new LineMap();
    }
    parser.addInput(inp, src);
    lineMap.addInput(inp, src);
    parser.continueParse();
  }

  /**
   * Notifies the parser that end of input has been seen.
   */
  public void finishParse() {
    if (parser != null) {
      parser.finishParse();
      if (parser.getState() == Completion.PASSED) {
        TreeProcessor t = new TreeProcessor(lineMap, types);
        t.process(parser.getOutput());
        messages.addAll(t.getMessages());
        t.defineProductions(this.builder);
      } else {
        List<Source> sources = parser.getParse().inp.sources(0);
        Source source = sources.isEmpty() ? Source.UNKNOWN : sources.get(0);
        messages.add(new Message("Parsing failed", true, source));
        int longestParseOffset = longestParseWatcher.getMaxOffset();
        FList<Combinator> stackAtMaxOffset =
            longestParseWatcher.getStackAtMaxOffset();
        Source farthestSource = lineMap.atCharIndex(longestParseOffset);
        if (longestParseOffset != 0 || !stackAtMaxOffset.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          sb.append("Longest parse at ").append(longestParseOffset);
          for (Combinator c : stackAtMaxOffset.rev()) {
            if (c instanceof ReferenceCombinator) {
              sb.append("\n\t").append(((ReferenceCombinator) c).name);
            }
          }
          messages.add(new Message(sb.toString(), false, farthestSource));
        }
      }
      parser = null;
      lineMap = null;
    }
  }

  /**
   * A builder for the productions parsed.
   */
  public Language.Builder getBuilder() {
    finishParse();
    return builder;
  }

  /**
   * True if the language parsed without significant issues.
   */
  public boolean isOk() {
    for (Message m : this.messages) {
      if (m.isError) { return false; }
    }
    return true;
  }

  /**
   * Any informational or error messages.
   */
  public ImmutableList<Message> getMessages() {
    return ImmutableList.copyOf(messages);
  }

}


/**
 * Keeps track of the longest parse which is a good hint as to where a syntax
 * error occurred.
 */
final class LongestParseWatcher implements ParseWatcher {
  private int endIndex = 0;
  private int offset = 0;
  private int maxOffset = 0;
  private FList<Combinator> stackAtMaxOffset = FList.empty();

  int getMaxOffset() {
    return maxOffset;
  }

  FList<Combinator> getStackAtMaxOffset() {
    return stackAtMaxOffset;
  }

  int getRelOffsetOfLongestPrefix() {
    return endIndex - maxOffset;
  }

  private static int distanceFromEnd(InputCursor inp) {
    return inp.getRawChars(inp.getAvailable().length()).length();
  }

  private void checkInputPos(Parse p) {
    int distanceFromEnd = distanceFromEnd(p.inp);
    int newOffset = endIndex - distanceFromEnd;
    if (newOffset > maxOffset) {
      maxOffset = newOffset;
      stackAtMaxOffset = p.stack;
    }
    offset = newOffset;
  }

  @Override
  public void inputAdded(Parse p) {
    int distanceFromEnd = distanceFromEnd(p.inp);
    endIndex = distanceFromEnd + offset;
  }

  @Override
  public void started(Parse p) {
    checkInputPos(p);
  }

  @Override
  public void entered(Combinator c, Parse p) {
    checkInputPos(p);
  }

  @Override
  public void passed(Combinator c, Parse p) {
    checkInputPos(p);
  }

  @Override
  public void failed(Combinator c, Parse p) {
    // Do nothing
  }

  @Override
  public void paused(Combinator c, Parse p) {
    // Do nothing
  }

  @Override
  public void forked(Parse p, Branch start, Branch end) {
    // Do nothing
  }

  @Override
  public void joinStarted(Branch from, Branch to) {
    // Do nothing
  }

  @Override
  public void joinFinished(Parse p, Branch from, Parse q, Branch to) {
    // Do nothing
  }

  @Override
  public void finished(Parse p, Branch b, Completion endState) {
    // Do nothing
  }

}


final class LanguageParserBranch implements ParseWatcher.Branch {
  static final LanguageParserBranch INSTANCE = new LanguageParserBranch();

  private LanguageParserBranch() {}

  @Override
  public void visualize(DetailLevel lvl, VizOutput out)
      throws IOException {
    out.text("Language");
  }
}