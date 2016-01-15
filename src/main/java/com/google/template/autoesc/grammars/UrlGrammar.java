package com.google.template.autoesc.grammars;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.var.MultiVariable;
import com.google.template.autoesc.var.Variable;


/**
 * A grammar for URLs which recognizes some important protocols.
 */
public final class UrlGrammar extends GrammarFactory {
  private UrlGrammar() {}

  /** The protocol of the URL. */
  public static final Variable<ProtocolT> PROTOCOL =
      Variable.create("protocol", ProtocolT.class);
  /** The kinds of paths that are allowed. */
  public static final MultiVariable<PathKindT> PATH_RESTRICTION =
      Variable.createMulti("path_restriction", PathKindT.class);

  /** The protocol of the URL. */
  @SuppressWarnings("javadoc")
  public enum ProtocolT {
    _NONE,
    HTTP,
    HTTPS,
    TEL,
    MAILTO,
    JAVASCRIPT,
    _UNKNOWN,
  }

  /**
   * A set of Protocols that have opaque content even if their protocol
   * specific part starts with a slash.
   */
  public static final ImmutableSet<ProtocolT> KNOWN_OPAQUE_PROTOCOLS =
      ImmutableSet.of(ProtocolT.TEL, ProtocolT.MAILTO, ProtocolT.JAVASCRIPT);

  /** The kind of a path. */
  public enum PathKindT {
    /** An absolute path that starts with a {@code '/'}. */
    ABSOLUTE,
    /** A relative URL. */
    RELATIVE,
    /** No Path of any kind since the scheme-specific part is opaque. */
    OPAQUE,
  }

  /**
   * Name of a production that matches a URL reference.
   */
  public static final ProdName N_URL = new ProdName("Url");

  /** Name of a production that matches the authority portion of a URL. */
  public static final ProdName N_AUTHORITY = new ProdName("Authority");
  /** Name of a production that matches the protocol portion of a URL. */
  public static final ProdName N_PROTOCOL = new ProdName("Protocol");
  /** Name of a production that matches the fragment portion of a URL. */
  public static final ProdName N_FRAGMENT = new ProdName("Fragment");
  /** Name of a production that matches the path portion of a URL. */
  public static final ProdName N_PATH = new ProdName("Path");
  /** Name of a production that matches the query portion of a URL. */
  public static final ProdName N_QUERY = new ProdName("Query");

  private Language make() {
    final Language.Builder lang = new Language.Builder();
    String demoServerQuery;
    try {
      demoServerQuery = DemoServerQuery.builder()
          .grammarField(UrlGrammar.class.getDeclaredField("LANG"))
          .build();
    } catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }
    lang.demoServerQuery(Optional.of(demoServerQuery));
    lang.defaultStartProdName(N_URL);

        lang.define(N_URL,
            decl(PROTOCOL,
                seq(
                    // URI fragments are not exclusive to hierarchical URLs.
                    //
                    // RFC 3986 defines "URI" which has no fragment and
                    // "URI reference" that has an optional fragment so we
                    // handle fragments separately.
                    //
                    // We include fragments as part of the Url production.
                    // Most standards besides RFC 3986
                    //     (HTML 5, XML, Java's core libraries)
                    // use the term "URI" or "URL" in a way that includes
                    // fragments.
                    until(
                        ref("UrlBody"),
                        chars('#')),
                    opt(ref(N_FRAGMENT))))
            );
        lang.define("UrlBody",
            decl(
                PATH_RESTRICTION,
                seq(
                    or(
                        // An absolute URL or protocol-relative URL
                        seq(
                            // Either we have a protocol, or it is none.
                            or(
                                ref(N_PROTOCOL),
                                set(PROTOCOL, ProtocolT._NONE)),
                            or(
                                // If we have no protocol, we may still have
                                // a hierarchical protocol relative URL.
                                ref("ProtocolRelative"),
                                // If we have a protocol, but the next character
                                // is not slash, then we have an opaque URL.
                                seq(
                                    notIn(PROTOCOL, ProtocolT._NONE),
                                    set(PATH_RESTRICTION,
                                        ImmutableSet.of(PathKindT.OPAQUE))
                                    ),
                                // Expect an absolute or relative path without
                                // any authority or protocol.
                                seq(
                                    set(PATH_RESTRICTION,
                                        ImmutableSet.of(
                                            PathKindT.ABSOLUTE,
                                            PathKindT.RELATIVE)
                                    )
                                )
                            )
                        )
                    ),
                    or(
                        seq(
                            has(PATH_RESTRICTION, PathKindT.OPAQUE),
                            ref("OpaqueBody")
                            ),
                        seq(
                            opt(ref(N_PATH)),
                            opt(ref(N_QUERY)))
                    )
                )
            ))
            .unbounded();

        lang.define("ProtocolRelative", seq(
            notIn(PROTOCOL, KNOWN_OPAQUE_PROTOCOLS),
            lit("//"),
            opt(ref(N_AUTHORITY)),
            set(PATH_RESTRICTION, ImmutableSet.of(PathKindT.ABSOLUTE))
            ))
            .unbounded();

        // scheme      = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
        lang.define(N_PROTOCOL, or(
            seq(litIgnCase("https:"), set(PROTOCOL, ProtocolT.HTTPS)),
            seq(litIgnCase("http:"), set(PROTOCOL, ProtocolT.HTTP)),
            seq(litIgnCase("javascript:"), set(PROTOCOL, ProtocolT.JAVASCRIPT)),
            seq(litIgnCase("mailto:"), set(PROTOCOL, ProtocolT.MAILTO)),
            seq(litIgnCase("tel:"), set(PROTOCOL, ProtocolT.TEL)),
            seq(
                chars(UniRanges.union(
                    UniRanges.btw('A', 'Z'), UniRanges.btw('a', 'z'))),
                star(
                    chars(UniRanges.union(
                        UniRanges.btw('A', 'Z'), UniRanges.btw('a', 'z'),
                        UniRanges.btw('0', '9'), UniRanges.of('+', '-', '.')))),
                lit(":"),
                set(PROTOCOL, ProtocolT._UNKNOWN))
            ));

        lang.define(N_AUTHORITY, seq(
            star(invChars('#', '?', '/'))));
        lang.define(N_PATH, seq(
            not(
                or(
                    // Disallow a double slash which starts a protocol-relative
                    // URL.
                    lit("//"),
                    // Disallow a semicolon in a path segment that might be
                    // mistaken for a protocol.
                    seq(
                        star(invChars('/', ':', '?', '#')),
                        chars(':'))
                )),
            or(
                seq(
                    lit("/"),
                    has(PATH_RESTRICTION, PathKindT.ABSOLUTE)
                    ),
                has(PATH_RESTRICTION, PathKindT.RELATIVE)
            ),
            opt(seq(
                ref("PathSegment"),
                star(seq(lit("/"), ref("PathSegment")))
                )),
            // Terminal "/"
            opt(lit("/"))
            ));
        lang.define(N_QUERY, seq(
            lit("?"),
            star(invChars('#'))));
        lang.define(N_FRAGMENT, seq(
            lit("#"),
            star(anyChar())));
        lang.define("PathSegment", seq(
            plus(invChars('/', '#', '?'))))
            .unbounded();

        lang.define("OpaqueBody", or(
            seq(
                in(PROTOCOL, ProtocolT.JAVASCRIPT),
                ref(JsGrammar.N_PROGRAM.withPrefix(Prefixes.JS_PREFIX))
                ),
            seq(
                notIn(PROTOCOL, ProtocolT.JAVASCRIPT),
                star(anyChar()))
            ));

        lang.include(Prefixes.JS_PREFIX, JsGrammar.LANG);

    return lang.build().reachableFrom(N_URL);
  }

  /** A grammar for URLs which recognizes some important protocols. */
  public static final Language LANG = new UrlGrammar().make();

  /** An optimized version of {@link #LANG}. */
  public static final Language OPT_LANG = LANG.optimized();
}
