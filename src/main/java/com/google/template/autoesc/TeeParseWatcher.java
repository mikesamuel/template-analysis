package com.google.template.autoesc;

import com.google.common.collect.ImmutableList;

/** Dispatches events to multiple other watchers. */
public final class TeeParseWatcher implements ParseWatcher {
  final ImmutableList<ParseWatcher> subwatchers;

  /** Does nothing with any event.  Tees to nothing. */
  public static final ParseWatcher DO_NOTHING_WATCHER =
      new TeeParseWatcher(ImmutableList.<ParseWatcher>of());

  private TeeParseWatcher(Iterable<? extends ParseWatcher> subwatchers) {
    this.subwatchers = ImmutableList.copyOf(subwatchers);
  }

  /** Factory */
  public static ParseWatcher create(
      Iterable<? extends ParseWatcher> subwatchers) {
    return new TeeParseWatcher(subwatchers);
  }

  /** Factory */
  public static ParseWatcher create(ParseWatcher... subwatchers) {
    return create(ImmutableList.copyOf(subwatchers));
  }

  @Override
  public void started(Parse p) {
    for (ParseWatcher w : subwatchers) {
      w.started(p);
    }
  }

  @Override
  public void entered(Combinator c, Parse p) {
    for (ParseWatcher w : subwatchers) {
      w.entered(c, p);
    }
  }

  @Override
  public void passed(Combinator c, Parse p) {
    for (ParseWatcher w : subwatchers) {
      w.passed(c, p);
    }
  }

  @Override
  public void failed(Combinator c, Parse p) {
    for (ParseWatcher w : subwatchers) {
      w.failed(c, p);
    }
  }

  @Override
  public void paused(Combinator c, Parse p) {
    for (ParseWatcher w : subwatchers) {
      w.paused(c, p);
    }
  }

  @Override
  public void inputAdded(Parse p) {
    for (ParseWatcher w : subwatchers) {
      w.inputAdded(p);
    }
  }

  @Override
  public void forked(Parse p, Branch start, Branch end) {
    for (ParseWatcher w : subwatchers) {
      w.forked(p, start, end);
    }
  }

  @Override
  public void joinStarted(Branch from, Branch to) {
    for (ParseWatcher w : subwatchers) {
      w.joinStarted(from, to);
    }
  }

  @Override
  public void joinFinished(Parse p, Branch from, Parse q, Branch to) {
    for (ParseWatcher w : subwatchers) {
      w.joinFinished(p, from, q, to);
    }
  }

  @Override
  public void finished(Parse p, Branch b, Completion endState) {
    for (ParseWatcher w : subwatchers) {
      w.finished(p, b, endState);
    }
  }
}
