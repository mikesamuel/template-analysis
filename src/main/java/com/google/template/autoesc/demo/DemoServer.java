package com.google.template.autoesc.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Parser;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.StepLimitingParseWatcher;
import com.google.template.autoesc.TeeParseWatcher;
import com.google.template.autoesc.UnjoinableException;
import com.google.template.autoesc.grammars.HtmlGrammar;
import com.google.template.autoesc.inp.Source;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


/**
 * An HTTP server that serves an HTML page that allows stepping through a
 * parse run.
 */
public final class DemoServer {
  static final int PORT = 8000;
  static final String DEMO_PATH = "/demo";

  /**
   * A URL that will reach the demo server with the given query string.
   * @see DemoServerQuery
   */
  public static URI getTestUrl(String testQueryString) {
    try {
      return new URI(
          "http://127.0.0.1:" + PORT + DEMO_PATH + "?" + testQueryString);
    } catch (URISyntaxException ex) {
      throw (IllegalArgumentException)
          new IllegalArgumentException(testQueryString).initCause(ex);
    }
  }

  /** @param argv ignored */
  public static void main(String... argv) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
    server.createContext(DEMO_PATH, new DemoHandler());
    for (URL url : HtmlSlideshowWatcher.SupportCode.URLS) {
      String path = url.getPath();
      int lastSlash = path.lastIndexOf('/');
      server.createContext(
          path.substring(lastSlash), new StaticContentHandler(url));
    }
    // So I don't have to do killall when I'm running this in an IDE and
    // accidentally hit run without stopping the old one.
    ExitHandler exitHandler = new ExitHandler(server);
    server.createContext("/quitquitquit", exitHandler);
    server.setExecutor(null);
    server.start();
    System.out.println("Serving from port " + PORT);
  }

  static final class StaticContentHandler implements HttpHandler {
    final URL url;

    StaticContentHandler(URL url) {
      this.url = url;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
      if (!"GET".equals(t.getRequestMethod())) {
        t.sendResponseHeaders(500, 0);
        return;
      }
      byte[] content = Resources.toByteArray(url);

      t.getResponseHeaders().set("Content-type", guessContentType(url));
      t.sendResponseHeaders(200, content.length);
      try (OutputStream out = t.getResponseBody()) {
        out.write(content);
      }
    }
  }


  static final class ExitHandler implements HttpHandler {
    private final HttpServer server;
    ExitHandler(HttpServer server) {
      this.server = server;
    }
    @Override
    public void handle(HttpExchange e) throws IOException {
      server.stop(0 /* seconds delay */);
    }
  }


  static final class DemoHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange t) throws IOException {
      if (!"GET".equalsIgnoreCase(t.getRequestMethod())) {
        t.sendResponseHeaders(500, 0);
        return;
      }
      URI requestURI = t.getRequestURI();
      int responseCode;
      String outputString;
      String contentType;
      try {
        ImmutableList<Param> queryParams = parseQuery(
            requestURI.getRawQuery());
        Run run = runFromQuery(queryParams);
        outputString = parseLog(run);
        contentType = "text/html; charset=UTF-8";
        responseCode = 200;
      } catch (RuntimeException | Error e) {
        responseCode = 500;
        contentType = "text/plain; charset=UTF-8";
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
          e.printStackTrace(pw);
        }
        outputString = sw.toString();

        System.err.println("For " + requestURI);
        System.err.println(outputString);
      }

      byte[] outputBytes = outputString.getBytes(Charsets.UTF_8);
      t.getResponseHeaders().set("Content-type", contentType);
      t.sendResponseHeaders(responseCode, outputBytes.length);
      try (OutputStream os = t.getResponseBody()) {
        os.write(outputBytes);
      }
      System.out.println(requestURI + " " + responseCode);
    }
  }

  static String parseLog(Run run) {
    StringBuilder htmlOut = new StringBuilder();

    Language lang = run.lang.reachableFrom(run.startProd);
    HtmlSlideshowWatcher watcher = new HtmlSlideshowWatcher(htmlOut);
    watcher.setLinkToRemoteResources(true);
    StepLimitingParseWatcher stepLimiter = new StepLimitingParseWatcher();
    stepLimiter.setStepLimit(run.stepLimit);

    Parser parser = new Parser(
        run.startBranch, lang, TeeParseWatcher.create(watcher, stepLimiter));
    BranchRunner<DemoBranch> runner = new BranchRunner<>(
        run.startBranch, run.startProd, run.finishParse);
    try {
      runner.run(parser);
    } catch (StepLimitingParseWatcher.StepLimitExceededException
             | UnjoinableException ex) {
      ex.printStackTrace();
      watcher.finished(
          parser.getParse(), runner.getCurrentBranch(), parser.getState());
    }

    return htmlOut.toString();
  }

  static ImmutableList<Param> parseQuery(@Nullable String s) {
    if (s == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Param> b = ImmutableList.builder();
    int i = 0, n = s.length();
    if (n != 0 && s.charAt(i) == '?') { ++i; }
    for (int end; i < n; i = end + 1) {
      int qmark = s.indexOf('&', i);
      end = qmark >= 0 ? qmark : n;
      String keyAndValue = s.substring(i, end);
      int eq = keyAndValue.indexOf('=');
      String encodedKey;
      Optional<String> encodedValue;
      if (eq >= 0) {
        encodedKey = keyAndValue.substring(0, eq);
        encodedValue = Optional.of(keyAndValue.substring(eq + 1));
      } else {
        encodedKey = keyAndValue;
        encodedValue = Optional.absent();
      }
      String key;
      Optional<String> value;
      try {
        key = URLDecoder.decode(encodedKey, "UTF-8");
        if (encodedValue.isPresent()) {
          value = Optional.of(URLDecoder.decode(encodedValue.get(), "UTF-8"));
        } else {
          value = Optional.absent();
        }
      } catch (UnsupportedEncodingException ex) {
        throw new AssertionError(ex);
      }
      b.add(new Param(DemoServerQuery.fromQueryParam(key), value));
    }
    return b.build();
  }

  static <T> T firstOrDefault(Iterable<? extends T> it, T defaultValue) {
    for (T x : it) {
      return x;
    }
    return defaultValue;
  }

  static @Nullable <T> T firstOrNull(Iterable<? extends T> it) {
    for (T x : it) {
      return x;
    }
    return null;
  }

  static String guessContentType(URL url) {
    String path = url.getPath();
    if (path != null) {
      String basename = path.substring(path.lastIndexOf('/') + 1);
      int dot = basename.lastIndexOf('.');
      String ext = dot >= 0 ? basename.substring(dot + 1) : null;
      if ("js".equals(ext)) {
        return "text/javascript; charset=UTF-8";
      } else if ("css".equals(ext)) {
        return "text/css; charset=UTF-8";
      }
    }
    return "test/plain; charset=UTF-8";
  }

  static final class Param {
    final DemoServerQuery key;
    final Optional<String> value;

    Param(DemoServerQuery key, Optional<String> value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String toString() {
      return key + (value.isPresent() ? " = `" + value.get() + "`" : "");
    }
  }



  static final class DemoBranch extends BranchRunner.Branch<DemoBranch> {
    DemoBranch(
        String name,
        ImmutableList<BranchRunner.StringSourcePair> inputs,
        ImmutableList<DemoBranch> followers) {
      super(name, inputs, followers);
    }
  }


  static final class Run {
    final DemoBranch startBranch;
    final Language lang;
    final ProdName startProd;
    final int stepLimit;
    // TODO: should finishParse be per-branch?
    final boolean finishParse;

    Run(DemoBranch startBranch, Language lang, ProdName startProd,
        int stepLimit, boolean finishParse) {
      this.startBranch = startBranch;
      this.lang = lang;
      this.startProd = startProd;
      this.stepLimit = stepLimit;
      this.finishParse = finishParse;
    }
  }

  static Run runFromQuery(Iterable<? extends Param> params) {
    int stepLimit = StepLimitingParseWatcher.DEFAULT_TEST_STEP_LIMIT;
    boolean optimized = false;
    Language lang = HtmlGrammar.LANG;
    final Multimap<String, BranchRunner.StringSourcePair> inputsPerBranch =
        HashMultimap.create();
    final Multimap<String, String> followersPerBranch = HashMultimap.create();
    ProdName startProdName = null;
    String currentBranch = "0";
    boolean finishParse = true;

    for (Param param : params) {
      switch (param.key) {
        case LANG:
          if (!param.value.isPresent()) { break; }
          String langSource = param.value.get();
          try {
            int dot = langSource.lastIndexOf('.');
            String cn = langSource.substring(0, dot);
            String fn = langSource.substring(dot + 1);
            Class<?> clazz = Class.forName(cn);
            Field f = clazz.getField(fn);
            lang = (Language) f.get(null);
          } catch (ClassNotFoundException  // invalid cn
              | IllegalAccessException  // invalid fn
              | NoSuchFieldException  // invalid fn
              | IndexOutOfBoundsException  // no dot
              | ClassCastException  // fn names field with wrong type
              | NullPointerException e) {  // fn names field with null value
            e.printStackTrace();
          }
          break;
        case START_PROD:
          if (!param.value.isPresent()) { break; }
          startProdName = new ProdName(param.value.get());
          break;
        case OPTIMIZED:
          optimized = true;
          break;
        case STEP_LIMIT:
          if (!param.value.isPresent()) { break; }
          try {
            stepLimit = Integer.parseInt(param.value.get());
          } catch (NumberFormatException ex) {
            ex.printStackTrace();
          }
          break;
        case FINISH_PARSE:
          if (param.value.isPresent()) {
            finishParse = coerceToBool(param.value, true);
          }
          break;
        case BRANCH:
          if (param.value.isPresent()) {
            currentBranch = param.value.get();
          }
          break;
        case INPUT:
          if (param.value.isPresent()) {
            String rawChars = param.value.get();
            Source source = new Source(
                "branch#" + currentBranch,
                inputsPerBranch.get(currentBranch).size());
            inputsPerBranch.put(
                currentBranch,
                new BranchRunner.StringSourcePair(rawChars, source));
          }
          break;
        case PRECEDES:
          if (param.value.isPresent()) {
            followersPerBranch.put(currentBranch, param.value.get());
            break;
          }
      }
    }

    final class DemoBranchMaker {
      Map<String, DemoBranch> made = new HashMap<>();

      DemoBranch make(String branchId) {
        if (!made.containsKey(branchId)) {
          // The branch graph should be a DAG, so put null in here to stop any
          // inf. recursion.  We will check it's acyclic later.
          made.put(branchId, null);

          ImmutableList<BranchRunner.StringSourcePair> inputs =
              ImmutableList.copyOf(inputsPerBranch.get(branchId));
          ImmutableList.Builder<DemoBranch> followers = ImmutableList.builder();
          for (String followerBranchId : followersPerBranch.get(branchId)) {
            followers.add(make(followerBranchId));
          }
          made.put(
              branchId,
              new DemoBranch(branchId, inputs, followers.build()));
        }
        DemoBranch branch = made.get(branchId);
        if (branch == null) {
          throw new IllegalArgumentException("Branch graph is cyclic");
        }
        return branch;
      }
    }

    DemoBranch startBranch = new DemoBranchMaker().make("0");

    if (startProdName == null) {
      startProdName = lang.defaultStartProdName;
    }
    if (optimized) {
      lang = lang.optimized();
    }
    return new Run(startBranch, lang, startProdName, stepLimit, finishParse);
  }

  private static boolean coerceToBool(
      Optional<String> sOpt, boolean defaultValue) {
    if (sOpt.isPresent()) {
      String s = sOpt.get();
      if (s.length() != 0) {
        switch (s.charAt(0)) {
          case 't': case 'T':
          case 'y': case 'Y':
            return true;
          case 'f': case 'F':
          case 'n': case 'N':
            return false;
        }
        return 0 != Integer.parseInt(s);
      }
    }
    return defaultValue;
  }
}
