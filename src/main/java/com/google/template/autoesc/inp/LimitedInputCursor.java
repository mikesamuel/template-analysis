package com.google.template.autoesc.inp;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.template.autoesc.Completion;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.FList;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.StepLimitingParseWatcher;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TagName;
import com.google.template.autoesc.viz.VizOutput;


/**
 * An input cursor that stops matching input when a pattern is found.
 */
public final class LimitedInputCursor extends InputCursor {

  private static final class Recognizer {
    final InputCursor unlimited;
    final Language lang;
    final Optional<Parse> parseOpt;
    final Combinator matcher;
    final int matcherDelta;
    final boolean isMatched;

    Recognizer(
        InputCursor unlimited, Language lang, Optional<Parse> parseOpt,
        Combinator matcher, int matcherDelta, boolean isMatched) {
      this.unlimited = unlimited;
      this.lang = lang;
      this.parseOpt = parseOpt;
      this.matcher = matcher;
      this.matcherDelta = matcherDelta;
      this.isMatched = isMatched;
    }

    Recognizer lookForLimit() {
      if (isMatched) {
        return this;
      }
      StepLimitingParseWatcher pw = null;
      Optional<Parse> newParseOpt = this.parseOpt;
      int newMatcherDelta = this.matcherDelta;
      CharSequence available = unlimited.getAvailable();
      int nAvailable = available.length();
      boolean newIsMatched = false;
      look_loop:
      while (true) {
        if (!newParseOpt.isPresent()) {
          pw = new StepLimitingParseWatcher();
          pw.setStepLimit(200);
          newParseOpt = Optional.of(
              Parse.builder(lang)
              .withInput(unlimited.advance(newMatcherDelta))
              .withStack(FList.cons(matcher, FList.<Combinator>empty()))
              .build());
        }

        Parse parse = newParseOpt.get();

        // Check for termination.
        switch (Completion.of(parse)) {
          case PASSED:
            newParseOpt = Optional.absent();
            newIsMatched = true;
            break look_loop;
          case FAILED:
            newParseOpt = Optional.absent();
            if (newMatcherDelta == nAvailable) {
              if (unlimited.isComplete()) {
                // Assume the end of input is the limit if not known.
                newIsMatched = true;
              }
              break look_loop;
            }
            ++newMatcherDelta;
            continue look_loop;
          case NOT_STARTED:
            throw new IllegalStateException();
          case IN_PROGRESS:
            break;
        }

        parse = parse.smallStep(pw);
        newParseOpt = Optional.of(parse);

        if (parse.isPaused()) {
          break;
        }
      }
      return new Recognizer(
          unlimited, lang, newParseOpt, matcher, newMatcherDelta,
          newIsMatched);
    }
}

  private final Recognizer recognizer;

  /** */
  public LimitedInputCursor(
      InputCursor unlimited, Combinator limitRecognizer, Language lang) {
    this(new Recognizer(
        unlimited,
        lang,
        Optional.<Parse>absent(),
        limitRecognizer,
        0,
        false).lookForLimit());
  }

  private LimitedInputCursor(Recognizer recognizer) {
    this.recognizer = recognizer;
  }

  /** The underlying input cursor. */
  public InputCursor getUnlimited() { return recognizer.unlimited; }

  @Override
  public boolean isComplete() {
    return recognizer.isMatched;
  }

  @Override
  public CharSequence getAvailable() {
    CharSequence avail = recognizer.unlimited.getAvailable();
    return avail.subSequence(0, recognizer.matcherDelta);
  }

  @Override
  public LimitedInputCursor advance(int nChars) {
    if (nChars > recognizer.matcherDelta) {
      throw new IndexOutOfBoundsException(
          nChars + " > " + recognizer.matcherDelta);
    }
    Recognizer advancedRecognizer = new Recognizer(
        recognizer.unlimited.advance(nChars),
        recognizer.lang,
        recognizer.parseOpt,
        recognizer.matcher,
        recognizer.matcherDelta - nChars,
        recognizer.isMatched
        );
    return new LimitedInputCursor(advancedRecognizer);
  }

  @Override
  public LimitedInputCursor extend(String rawChars, Source source) {
    InputCursor extendedInput =
        recognizer.unlimited.extend(rawChars, source);
    Recognizer extendedRecognizer = new Recognizer(
        extendedInput,
        recognizer.lang,
        Optional.<Parse>absent(),
        recognizer.matcher,
        recognizer.matcherDelta,
        recognizer.isMatched);
    return new LimitedInputCursor(extendedRecognizer.lookForLimit());
  }

  @Override
  public InputCursor insertBefore(String rawChars) {
    if (rawChars.length() == 0) { return this; }
    InputCursor prependedInput = recognizer.unlimited.insertBefore(rawChars);
    int matcherDelta;
    if (recognizer.isMatched) {
      matcherDelta = recognizer.matcherDelta + (
          prependedInput.getAvailable().length()
          - recognizer.unlimited.getAvailable().length());
    } else {
      matcherDelta = 0;
    }
    return new LimitedInputCursor(
        new Recognizer(
            prependedInput,
            recognizer.lang,
            Optional.<Parse>absent(),
            recognizer.matcher,
            matcherDelta,
            recognizer.isMatched).lookForLimit());
  }

  @Override
  public List<Source> sources(int nChars) {
    assert (nChars <= recognizer.matcherDelta);
    return recognizer.unlimited.sources(nChars);
  }

  @Override
  public CharSequence getRawChars(int nChars) {
    assert (nChars <= recognizer.matcherDelta);
    return recognizer.unlimited.getRawChars(nChars);
  }

  @Override
  public LimitedInputCursor finish() {
    return new LimitedInputCursor(
        new Recognizer(
            recognizer.unlimited.finish(),
            recognizer.lang,
            Optional.<Parse>absent(),
            recognizer.matcher,
            recognizer.matcherDelta,
            recognizer.isMatched)
        .lookForLimit());
  }

  @Override
  protected String getVizTypeClassName() {
    return "limited-input";
  }

  @Override
  protected void visualizeBody(DetailLevel lvl, VizOutput out)
  throws IOException {
    recognizer.unlimited.visualize(lvl, out);
    if (recognizer.isMatched) {
      out.text("[:" + recognizer.matcherDelta + "]");
      out.text(" -> ");
      try (Closeable code = out.open(TagName.CODE)) {
        out.text(
            "\""
            + RawCharsInputCursor.STRING_ESCAPER.escape(
                getAvailable().toString())
            + "\"");
      }
    } else {
      out.text(" until ");
      try (Closeable code = out.open(TagName.CODE)) {
        recognizer.matcher.visualize(lvl, out);
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        recognizer.unlimited, recognizer.isMatched, recognizer.matcherDelta,
        recognizer.matcher);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LimitedInputCursor)) { return false; }
    LimitedInputCursor that = (LimitedInputCursor) o;
    return this.recognizer.isMatched == that.recognizer.isMatched
        && this.recognizer.matcherDelta == that.recognizer.matcherDelta
        && this.recognizer.unlimited.equals(that.recognizer.unlimited)
        && this.recognizer.matcher.equals(that.recognizer.matcher);
  }
}
