package com.google.template.autoesc;

import java.io.IOException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.combimpl.CharSetCombinator;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;
import com.google.template.autoesc.viz.Visualizable;
import com.google.template.autoesc.viz.VizOutput;


/**
 * A parse watcher that queues events so that they can be emitted should the
 * test eventually fail.
 */
public class SilentLoggingWatcher implements ParseWatcher {
  private final ImmutableList.Builder<LogEvent> log =
      ImmutableList.builder();

  @Override
  public void started(Parse p) {
    log.add(new LogEvent(EventType.STARTED, p));
  }

  @Override
  public void entered(Combinator c, Parse p) {
    log.add(new LogEvent(EventType.ENTERED, p, c));
  }

  @Override
  public void passed(Combinator c, Parse p) {
    log.add(new LogEvent(EventType.PASSED, p, c));
  }

  @Override
  public void failed(Combinator c, Parse p) {
    log.add(new LogEvent(EventType.FAILED, p, c));
  }

  @Override
  public void paused(Combinator c, Parse p) {
    log.add(new LogEvent(EventType.PAUSED, p, c));
  }

  @Override
  public void inputAdded(Parse p) {
    log.add(new LogEvent(EventType.INPUT_ADDED, p));
  }

  @Override
  public void forked(Parse p, Branch start, Branch end) {
    log.add(new LogEvent(
        EventType.FORKED, Optional.of(p), Optional.<Combinator>absent(),
        ImmutableList.of(start), Optional.of(end)));
  }

  @Override
  public void joinStarted(Branch from, Branch to) {
    log.add(new LogEvent(
        EventType.JOIN_STARTED, Optional.<Parse>absent(),
        Optional.<Combinator>absent(),
        ImmutableList.of(from), Optional.of(to)));
  }

  @Override
  public void joinFinished(Parse p, Branch from, Parse q, Branch to) {
    log.add(new LogEvent(
        EventType.JOIN_FINISHED, Optional.of(q),
        Optional.<Combinator>absent(), ImmutableList.of(from),
        Optional.of(to)));
  }

  @Override
  public void finished(Parse p, Branch b, Completion endState) {
    log.add(new LogEvent(
        EventType.FINISHED, Optional.of(p), Optional.<Combinator>absent(),
        ImmutableList.<Branch>of(), Optional.of(b)));
  }

  /** The events enqueued thus far. */
  public ImmutableList<LogEvent> getEvents() { return log.build(); }

  /** Dumps the event log thus far to out. */
  public void printLog(Appendable out) throws IOException {
    for (final LogEvent e : getEvents()) {
      String typeName = e.t.name();
      out.append(typeName);
      Visualizable partToLog = null;
      switch (e.t) {
        case INPUT_ADDED:
          partToLog = e.p.get().inp;
          break;
        case PAUSED: case FINISHED:
          partToLog = new VizStack(e);
          break;
        default:
          if (e.c.isPresent()) {
            final Combinator c = e.c.get();
            if (c instanceof CharSetCombinator) {
              partToLog = new VizCharactersConsumed(c, e);
            } else {
              partToLog = c;
            }
          } else if (!e.from.isEmpty() || e.to.isPresent()) {
            partToLog = new VizRolledBackInput(e);
          }
          break;
      }
      if (partToLog != null) {
        out.append("            : ".substring(typeName.length()));
        TextVizOutput logOut = new TextVizOutput(out);
        partToLog.visualize(DetailLevel.SHORT, logOut);
      }
      out.append("\n");
    }
  }

  /** Correspond to the event methods in ParseWatcher. */
  public enum EventType {
    /** @see ParseWatcher#started */
    STARTED,
    /** @see ParseWatcher#entered */
    ENTERED,
    /** @see ParseWatcher#paused */
    PAUSED,
    /** @see ParseWatcher#inputAdded */
    INPUT_ADDED,
    /** @see ParseWatcher#passed */
    PASSED,
    /** @see ParseWatcher#failed */
    FAILED,
    /** @see ParseWatcher#forked */
    FORKED,
    /** @see ParseWatcher#joinStarted */
    JOIN_STARTED,
    /** @see ParseWatcher#joinFinished */
    JOIN_FINISHED,
    /** @see ParseWatcher#finished */
    FINISHED,
    ;
  }

  /** A reified ParseWatcher event. */
  @SuppressWarnings("javadoc")
  public static final class LogEvent {
    public final EventType t;
    public final Optional<Parse> p;
    public final Optional<Combinator> c;
    public final ImmutableList<Branch> from;
    public final Optional<Branch> to;

    LogEvent(
        EventType t, Optional<Parse> p, Optional<Combinator> c,
        ImmutableList<Branch> from, Optional<Branch> to) {
      this.t = t;
      this.p = p;
      this.c = c;
      this.from = from;
      this.to = to;
    }

    LogEvent(EventType t, Parse p, Combinator c) {
      this(t, Optional.of(p), Optional.of(c),
          ImmutableList.<Branch>of(), Optional.<Branch>absent());
    }

    LogEvent(EventType t, Parse p) {
      this(t, Optional.of(p), Optional.<Combinator>absent(),
          ImmutableList.<Branch>of(), Optional.<Branch>absent());
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(t);
      if (c.isPresent()) {
        sb.append(' ').append(c.get());
      }
      return sb.toString();
    }
  }
}


final class VizRolledBackInput implements Visualizable {
  private final SilentLoggingWatcher.LogEvent e;

  VizRolledBackInput(SilentLoggingWatcher.LogEvent e) {
    this.e = e;
  }

  @Override
  public void visualize(DetailLevel ilvl, VizOutput iout)
  throws IOException {
    boolean first = true;
    for (ParseWatcher.Branch f : e.from) {
      if (first) {
        first = false;
      } else {
        iout.text(", ");
      }
      f.visualize(ilvl, iout);
    }
    iout.text("  \u2192  ");
    if (e.to.isPresent()) {
      e.to.get().visualize(ilvl, iout);
    }
  }
}

final class VizCharactersConsumed implements Visualizable {
  private final Combinator c;
  private final SilentLoggingWatcher.LogEvent e;

  VizCharactersConsumed(Combinator c, SilentLoggingWatcher.LogEvent e) {
    this.c = c;
    this.e = e;
  }

  @Override
  public void visualize(DetailLevel ilvl, VizOutput iout)
      throws IOException {
    c.visualize(ilvl, iout);
    if (e.p.isPresent()) {
      iout.text("  \u27f8  ");
      e.p.get().inp.visualize(ilvl, iout);
    }
  }
}

final class VizStack implements Visualizable {
  private final SilentLoggingWatcher.LogEvent e;

  VizStack(SilentLoggingWatcher.LogEvent e) {
    this.e = e;
  }

  @Override
  public void visualize(DetailLevel ilvl, VizOutput iout)
  throws IOException{
    boolean needsComma = false;
    for (Combinator c : e.p.get().stack.rev()) {
      if (needsComma) { iout.text(", "); }
      needsComma = true;
      c.visualize(ilvl, iout);
    }
  }
}
