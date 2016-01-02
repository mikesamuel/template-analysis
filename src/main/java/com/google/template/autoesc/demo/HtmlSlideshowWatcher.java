package com.google.template.autoesc.demo;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Completion;
import com.google.template.autoesc.Parse;
import com.google.template.autoesc.ParseWatcher;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.VizOutput;

import static com.google.template.autoesc.viz.AttribName.*;
import static com.google.template.autoesc.viz.TagName.*;


/**
 * Generates HTML containing a description of the grammar and a log of a
 * parse run.
 */
public final class HtmlSlideshowWatcher implements ParseWatcher {
  private final Appendable out;
  private final Function<IOException, Void> ioHandler;
  private final VizOutput vizOut;
  private boolean linkToRemoteResources = false;

  /**
   * @param out receives HTML
   * @param ioHandler called when an IOException occurs writing to out.
   */
  public HtmlSlideshowWatcher(
      Appendable out, Function<IOException, Void> ioHandler) {
    this.out = out;
    this.ioHandler = ioHandler;
    this.vizOut = new HtmlVizOutput(out);
  }

  /**
   * @param out receives HTML
   */
  public HtmlSlideshowWatcher(Appendable out) {
    this(
        out,
        new Function<IOException, Void>() {
          @Override
          public Void apply(IOException ioe) {
            Throwables.propagate(ioe);
            return null;
          }
        });
  }

  /**
   * @param linkToRemoteResources true to load supporting CSS and JS instead
   *     of inlining it.
   */
  public void setLinkToRemoteResources(boolean linkToRemoteResources) {
    this.linkToRemoteResources = linkToRemoteResources;
  }

  @Override
  public void started(Parse p) {
    try {
      out.append("<!doctype html>\n");
      out.append("<html><head>\n");
      out.append("<title>Parser Slideshow</title>\n");
      if (this.linkToRemoteResources) {
        out.append("  <link rel=\"stylesheet\" href=\"viz.css\">\n")
           .append("  <script src=\"viz.js\"></script>\n");
      } else {
        out.append("  <style>\n")
           .append(SupportCode.CSS)
           .append("\n</style>\n")
           .append("  <script>\n")
           .append(SupportCode.JS)
           .append("\n</script>\n");
      }
      out.append("<body>\n");

      out.append("  <table><tr valign=top colspan=2><td width=50%>");
      try (Closeable tbl = vizOut.open(
              TABLE, ID, "grammar", CLASS, "grammar")) {
        for (Map.Entry<ProdName, Combinator> production
            : p.lang.byName().entrySet()) {
          ProdName name = production.getKey();
          String anchorId = "def:" + name;  // See ReferenceCombinator
          try (Closeable tr = vizOut.open(TR)) {
            try (Closeable th = vizOut.open(TH, ID, anchorId, CLASS, "def")) {
              vizOut.text(name.text);
            }
            try (Closeable th = vizOut.open(TH)) {
              vizOut.text("::==");
            }
            Combinator body = production.getValue();
            try (Closeable th = vizOut.open(TD)) {
              body.visualize(DetailLevel.LONG, vizOut);
            }
          }
        }
      }
      out.append("</td>\n<td width=50%><ul id=\"parse-log\">\n");
    } catch (IOException ex) {
      ioHandler.apply(ex);
    }
  }

  @Override
  public void paused(Combinator c, Parse p) {
    writeLogEntry(c, p, "paused");
  }

  @Override
  public void inputAdded(Parse p) {
    writeLogEntry(p, "add-input");
  }

  @Override
  public void finished(Parse p, Branch b, Completion state) {
    writeLogEntry(p, "finished " + state.name());
    try {
      out.append("  </ul></td>\n");  // #parse-log
      out.append("</table>\n");
      out.append("</body>\n");
      out.append("<script>init()</script>\n");
      out.append("</html>\n");
    } catch (IOException ex) {
      ioHandler.apply(ex);
    }
  }

  @Override
  public void entered(Combinator c, Parse p) {
    writeLogEntry(c, p, "entered");
  }

  @Override
  public void passed(Combinator c, Parse p) {
    writeLogEntry(c, p, "passed");
  }

  @Override
  public void failed(Combinator c, Parse p) {
    writeLogEntry(c, p, "failed");
  }

  @Override
  public void forked(Parse p, Branch from, Branch to) {
    writeLogEntry(
        Optional.of(p), ImmutableList.of(from), Optional.of(to), "forked");
  }

  @Override
  public void joinStarted(Branch from, Branch to) {
    writeLogEntry(
        Optional.<Combinator>absent(), Optional.<Parse>absent(),
        ImmutableList.of(from), Optional.of(to),
        "join");
  }

  @Override
  public void joinFinished(Parse p, Branch from, Parse q, Branch to) {
    writeLogEntry(
        Optional.<Combinator>absent(), Optional.of(q),
        ImmutableList.of(from), Optional.of(to), "joined");
  }

  private void writeLogEntry(Combinator c, Parse p, String kind) {
    writeLogEntry(
        Optional.of(c), Optional.of(p),
        ImmutableList.<Branch>of(), Optional.<Branch>absent(),
        kind);
  }

  private void writeLogEntry(Parse p, String kind) {
    writeLogEntry(
        Optional.<Combinator>absent(), Optional.of(p),
        ImmutableList.<Branch>of(), Optional.<Branch>absent(),
        kind);
  }

  private void writeLogEntry(
      Optional<Parse> p, ImmutableList<Branch> from, Optional<Branch> to,
      String kind) {
    writeLogEntry(Optional.<Combinator>absent(), p, from, to, kind);
  }

  private void writeLogEntry(
      Optional<Combinator> cOpt, Optional<Parse> pOpt,
      ImmutableList<Branch> from, Optional<Branch> toOpt,
      String kind) {
    try {
      try (Closeable entry = vizOut.open(LI, CLASS, "entry")) {
        if (pOpt.isPresent()) {
          Parse p = pOpt.get();
          try (Closeable c = vizOut.open(DIV, CLASS, "input")) {
            p.inp.visualize(DetailLevel.LONG, vizOut);
          }
        }
        try (Closeable c = vizOut.open(DIV, CLASS, "event " + kind)) {
          vizOut.text(kind + " ");
          if (cOpt.isPresent()) {
            cOpt.get().visualize(DetailLevel.SHORT, vizOut);
          }
          if (!from.isEmpty()) {
            try (Closeable ul = vizOut.open(UL, CLASS, "from-branches")) {
              for (Branch b : from) {
                try (Closeable li = vizOut.open(LI, CLASS, "from-branch")) {
                  b.visualize(DetailLevel.SHORT, vizOut);
                }
              }
            }
          }
          if (toOpt.isPresent()) {
            try (Closeable f = vizOut.open(DIV, CLASS, "to-branch")) {
              toOpt.get().visualize(DetailLevel.SHORT, vizOut);
            }
          }
        }
        if (pOpt.isPresent()) {
          Parse p = pOpt.get();
          try (Closeable c = vizOut.open(OL, CLASS, "stack")) {
            for (Combinator stackEl : p.stack) {
              try (Closeable li = vizOut.open(LI)) {
                stackEl.visualize(DetailLevel.SHORT, vizOut);
              }
            }
          }
          try (Closeable c = vizOut.open(OL, CLASS, "output")) {
            for (Output o : p.out.toReverseList()) {
              try (Closeable li = vizOut.open(LI)) {
                o.visualize(DetailLevel.LONG, vizOut);
              }
            }
          }
        }
      }
    } catch (IOException ex) {
      this.ioHandler.apply(ex);
    }
  }


  /** Supporting styles and scripts. */
  public static final class SupportCode {
    /** Resource URL of supporting styles. */
    public static final URL CSS_URL =
        Resources.getResource(SupportCode.class, "viz.css");
    /** Resource URL of supporting scripts. */
    public static final URL JS_URL =
        Resources.getResource(SupportCode.class, "viz.js");
    /** Supporting resource URLs */
    public static final ImmutableList<URL> URLS =
        ImmutableList.of(CSS_URL, JS_URL);

    /** Supporting styles. */
    public static final String CSS;
    /** Supporting scripts. */
    public static final String JS;

    static {
      String css;
      String js;
      try {
        css = Resources.toString(CSS_URL, Charsets.UTF_8);
      } catch (IOException ex) {
        css = "/* Failed to load resource: " + ex + "*/";
        ex.printStackTrace();
      }
      try {
        js = Resources.toString(JS_URL, Charsets.UTF_8);
      } catch (IOException ex) {
        js = "/* Failed to load resource: " + ex + "*/";
        ex.printStackTrace();
      }
      CSS = css;
      JS = js;
    }
  }
}
