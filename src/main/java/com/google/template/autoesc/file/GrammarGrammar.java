package com.google.template.autoesc.file;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.Combinator;
import com.google.template.autoesc.Combinators;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.demo.DemoServerQuery;
import com.google.template.autoesc.inp.StringTransform;
import com.google.template.autoesc.inp.StringTransforms;
import com.google.template.autoesc.inp.UniRanges;
import com.google.template.autoesc.var.MultiVariable;
import com.google.template.autoesc.var.RawTypeGuard;
import com.google.template.autoesc.var.SetTypeGuard;
import com.google.template.autoesc.var.TypeGuard;
import com.google.template.autoesc.var.Variable;
import com.google.template.autoesc.viz.DetailLevel;
import com.google.template.autoesc.viz.TextVizOutput;

/**
 * Defines a grammar for languages.
 */
public final class GrammarGrammar {
  private GrammarGrammar() {
    // Uninstantiable
  }

  static final ProdName N_ANY_CHAR = new ProdName("AnyChar");
  static final ProdName N_ATOM_NODE = new ProdName("AtomNode");
  static final ProdName N_BOUNDED_REFERENCE = new ProdName("BoundedReference");
  static final ProdName N_CHAR_RANGE = new ProdName("CharRange");
  static final ProdName N_CHAR_RANGE_ENDPOINT =
      new ProdName("CharRangeEndpoint");
  static final ProdName N_CHAR_SET_CHAR = new ProdName("CharSetChar");
  static final ProdName N_CHAR_SET_NAME = new ProdName("CharSetName");
  static final ProdName N_CHAR_SET_NODE = new ProdName("CharSetNode");
  static final ProdName N_CHAR_SET_PART = new ProdName("CharSetPart");
  static final ProdName N_COMMENT = new ProdName("Comment");
  static final ProdName N_COMMENT_BODY = new ProdName("CommentBody");
  static final ProdName N_CURLY_BLOCK = new ProdName("CurlyBlock");
  static final ProdName N_CURLY_ASSIGN = new ProdName("CurlyAssign");
  static final ProdName N_CURLY_IF = new ProdName("CurlyIf");
  static final ProdName N_CURLY_VAR = new ProdName("CurlyVar");
  static final ProdName N_DOC_COMMENT = new ProdName("DocComment");
  static final ProdName N_EMBED = new ProdName("Embed");
  static final ProdName N_END_OF_FILE = new ProdName("EndOfFile");
  static final ProdName N_ESC = new ProdName("Esc");
  static final ProdName N_GRAMMAR = new ProdName("Grammar");
  static final ProdName N_HEX = new ProdName("Hex");
  static final ProdName N_HEX2 = new ProdName("Hex2");
  static final ProdName N_HEX4 = new ProdName("Hex4");
  static final ProdName N_IGN = new ProdName("Ign");
  static final ProdName N_IGNS = new ProdName("Igns");
  static final ProdName N_INV_MARKER = new ProdName("InvMarker");
  static final ProdName N_LIT_CHAR = new ProdName("LitChar");
  static final ProdName N_LIT_NODE = new ProdName("LitNode");
  static final ProdName N_LOOKAHEAD_KIND = new ProdName("LookaheadKind");
  static final ProdName N_LOOKAHEAD_NODE = new ProdName("LookaheadNode");
  static final ProdName N_NAME = new ProdName("Name");
  static final ProdName N_NAME_PART = new ProdName("NamePart");
  static final ProdName N_OR_NODE = new ProdName("OrNode");
  static final ProdName N_PAREN_BLOCK = new ProdName("ParenBlock");
  static final ProdName N_PROD = new ProdName("Prod");
  static final ProdName N_REFERENCE = new ProdName("Reference");
  static final ProdName N_SEQ_NODE = new ProdName("SeqNode");
  static final ProdName N_SPACE = new ProdName("Space");
  static final ProdName N_SUFFIX = new ProdName("Suffix");
  static final ProdName N_SUFFIX_NODE = new ProdName("SuffixNode");
  static final ProdName N_TOP_LEVEL = new ProdName("TopLevel");
  static final ProdName N_UNTIL = new ProdName("Until");
  static final ProdName N_VAR = new ProdName("Var");
  static final ProdName N_VAR_TEST = new ProdName("VarTest");
  static final ProdName N_VAR_VALUE = new ProdName("VarValue");
  static final ProdName N_VAR_VALUES = new ProdName("VarValues");
  static final ProdName N_VAR_TYPE_VARIANT = new ProdName("VarTypeVariant");

  static final ImmutableMap<ProdName, StringTransform> STRING_TRANSFORMS =
      ImmutableMap.of(
          new ProdName(TextVizOutput.vizToString(
              StringTransforms.HTML, DetailLevel.LONG)),
          StringTransforms.HTML,
          new ProdName(TextVizOutput.vizToString(
              StringTransforms.URI_PATH, DetailLevel.LONG)),
          StringTransforms.URI_PATH,
          new ProdName(TextVizOutput.vizToString(
              StringTransforms.URI_QUERY, DetailLevel.LONG)),
          StringTransforms.URI_QUERY
          );

  private static final StringOutputProcessor<String> STRING_IDENTITY =
      new StringOutputProcessor<String>() {
    @Override
    Optional<String> process(String s, String rawChars, TreeProcessor t) {
      return Optional.of(s);
    }
  };


  /**
   * A grammar for grammars.
   */
  public static final Language LANG;

  /**
   * Associates {@link OutputProcessor}s with
   * each production so that the {@link TreeProcessor} can build productions.
   */
  static final ImmutableMap<ProdName, OutputProcessor<?>> OUTPUT_PROCESSORS;

  static {
    Field langField;
    try {
      langField = GrammarGrammar.class.getDeclaredField("LANG");
    } catch (NoSuchFieldException ex) {
      throw (NoSuchFieldError) new NoSuchFieldError().initCause(ex);
    }

    class Builder {
      final Language.Builder lang = new Language.Builder();
      final ImmutableMap.Builder<ProdName, OutputProcessor<?>> processors =
          ImmutableMap.builder();
      void define(
          ProdName name, Combinator c,
          @Nullable OutputProcessor<?> p) {
        lang.define(name, c);
        if (p != null) {
          processors.put(name, p);
        }
      }
    }
    Builder b = new Builder();

    b.lang.demoServerQuery(Optional.of(
        DemoServerQuery.builder()
        .grammarField(langField)
        .build()));

    Combinators c = Combinators.get();

    b.define(
        N_GRAMMAR,
        c.seq(c.plus(c.ref(N_TOP_LEVEL)), c.endOfInput()),
        new OutputProcessor<ImmutableList<Production>>() {
          @Override
          Optional<ImmutableList<Production>> post(
              ImmutableList<Object> children, TreeProcessor t) {
            ImmutableList.Builder<Production> prods = ImmutableList.builder();
            for (Object o : children) {
              prods.add((Production) o);
            }
            return Optional.of(prods.build());
          }
        });
    b.define(
        N_IGN,
        c.or(c.bref(N_DOC_COMMENT), c.ref(N_COMMENT), c.ref(N_SPACE)),
        null);
    b.define(
        N_DOC_COMMENT,
        c.seq(
            c.lit("/**"),
            c.not(c.chars('/')),
            c.ref(N_COMMENT_BODY),
            c.lit("*/")
            ),
        new StringOutputProcessor<Void>() {
          @Override
          Optional<Void> process(String s, String rawChars, TreeProcessor t) {
            t.setDocComment(s);
            return Optional.absent();
          }
        });
    b.define(
        N_COMMENT,
        c.or(
            c.seq(
                c.lit("/*"),
                c.not(c.chars('*', '/')),
                c.ref(N_COMMENT_BODY),
                c.lit("*/")),
            c.seq(
                c.lit("//"),
                c.star(c.invChars('\n', '\r')))
            ),
        null);
    b.define(
        N_COMMENT_BODY,
        c.star(
            c.or(
                c.invChars('*'),
                c.plus(
                    c.seq(
                        c.chars('*'),
                        c.not(c.chars('/')))))),
        null);
    b.define(
        N_IGNS,
        c.star(c.ref(N_IGN)),
        null);
    b.define(
        N_TOP_LEVEL,
        c.or(c.bref(N_PROD), c.ref(N_IGNS)),
        null);
    b.define(
        N_NAME,
        c.seq(
            c.chars(UniRanges.union(
                UniRanges.btw('A', 'Z'), UniRanges.btw('a', 'z'),
                UniRanges.of('_')
                )),
            c.star(c.ref(N_NAME_PART))
            ),
        new StringOutputProcessor<ProdName>() {
          @Override
          Optional<ProdName> process(
              String s, String rawChars, TreeProcessor t) {
            return Optional.of(new ProdName(s));
          }
        });
    b.define(
        N_NAME_PART,
        c.chars(UniRanges.union(
            UniRanges.btw('A', 'Z'), UniRanges.btw('a', 'z'),
            UniRanges.btw('0', '9'), UniRanges.of('_'), UniRanges.of('$')
            )),
        null);
    b.define(
        N_PROD,
        c.seq(
            c.bref(N_NAME),
            c.ref(N_IGNS),
            c.lit(":="),
            c.ref(N_IGNS),
            c.bref(N_OR_NODE),
            c.ref(N_IGNS),
            c.opt(c.or(c.lit(";"), c.endOfInput()))
            ),
        new OutputProcessor<Production>() {
          @Override
          Optional<Production> post(
              ImmutableList<Object> children, TreeProcessor t) {
            Preconditions.checkArgument(children.size() == 2);
            return Optional.of(
                new Production(
                    (ProdName) children.get(0),
                    (Combinator) children.get(1)));
          }
        });
    b.define(
        N_OR_NODE,
        c.seq(
            c.bref(N_SEQ_NODE),
            c.star(c.seq(
                c.ref(N_IGNS),
                c.lit("/"),
                c.ref(N_IGNS),
                c.bref(N_SEQ_NODE)))),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            return Optional.of(
                t.combinators.or(runtimeCastList(children, Combinator.class)));
          }
        });
    b.define(
        N_SEQ_NODE,
        c.seq(
            // The zero token string is a valid sequence node.
            c.star(c.seq(
                c.ref(N_IGNS),
                c.bref(N_SUFFIX_NODE)))),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            return Optional.of(
                t.combinators.seq(runtimeCastList(children, Combinator.class)));
          }
        });
    b.define(
        N_SUFFIX,
        c.chars('+', '?', '*'),
        STRING_IDENTITY);
    b.define(
        N_SUFFIX_NODE,
        c.seq(
            c.bref(N_ATOM_NODE),
            c.ref(N_IGNS),
            c.opt(c.bref(N_SUFFIX))),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            Preconditions.checkArgument(
                1 <= children.size() && children.size() <= 2);
            Combinator node = (Combinator) children.get(0);
            String suffix =
                children.size() == 2 ? (String) children.get(1) : null;
            if (suffix == null) {
              return Optional.of(node);
            }
            switch (suffix.charAt(0)) {
              case '+':
                return Optional.of(t.combinators.plus(node));
              case '?':
                return Optional.of(t.combinators.opt(node));
              case '*':
                return Optional.of(t.combinators.star(node));
            }
            throw new AssertionError(suffix);
          }
        });
    b.define(
        N_ATOM_NODE,
        c.or(
            c.bref(N_PAREN_BLOCK),
            c.bref(N_CURLY_BLOCK),
            c.bref(N_LIT_NODE),
            c.bref(N_CHAR_SET_NODE),
            c.bref(N_ANY_CHAR),
            c.bref(N_END_OF_FILE),
            c.bref(N_REFERENCE),
            c.bref(N_BOUNDED_REFERENCE)
            ),
        null);
    b.define(
        N_LIT_NODE,
        c.or(
            c.seq(
                c.chars('"'),
                c.star(c.or(c.bref(N_ESC), c.bref(N_LIT_CHAR))),
                c.chars('"')),
            c.seq(
                c.chars('\u201c'),
                c.star(c.or(c.bref(N_ESC), c.bref(N_LIT_CHAR))),
                c.chars('\u201d'))),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            ImmutableList.Builder<Combinator> chars = ImmutableList.builder();
            for (Object o : children) {
              Integer codePoint = (Integer) o;
              chars.add(t.combinators.chars(codePoint));
            }
            return Optional.of(t.combinators.seq(chars.build()));
          }
        });
    b.define(
        N_LIT_CHAR,
        c.invChars('"', '\\', '\n', '\r', '\u201d'),
        new StringOutputProcessor<Integer>() {
          @Override
          Optional<Integer> process(
              String s, String rawChars, TreeProcessor t) {
            return Optional.of(Integer.valueOf(s.codePointAt(0)));
          }
        });
    b.define(N_ESC,
        c.seq(
            c.chars('\\'),
            c.or(
                c.seq(c.lit("U{"), c.plus(c.ref(N_HEX)), c.lit("}")),
                c.seq(c.lit("u"), c.ref(N_HEX4)),
                c.seq(c.lit("x"), c.ref(N_HEX2)),
                c.chars(
                    '[', ']', '\\', '"', 't', 'r', 'n', 'f', 'v', 'b', '-',
                    '^', '|', '/', '.', '$', '0'))),
        // TODO: should \0 followed by an octal digit be disallowed?
        new StringOutputProcessor<Integer>() {
          @Override
          Optional<Integer> process(
              String s, String rawChars, TreeProcessor t) {
            int cp;
            switch (s.charAt(1)) {
              case 'U':
                String hex = s.substring(3, s.length() - 1);
                cp = Integer.parseInt(hex, 16);
                if (!(0 <= cp && cp <= Character.MAX_CODE_POINT) ){
                  t.error("Invalid code-point 0x" + s);
                  cp = 0xFFFD;
                }
                break;
              case 'u': case 'x':
                cp = Integer.parseInt(s.substring(2), 16);
                break;
              case 't':
                cp = '\t';
                break;
              case 'r':
                cp = '\r';
                break;
              case 'n':
                cp = '\n';
                break;
              case 'f':
                cp = '\f';
                break;
              case 'v':
                cp = 0xB;
                break;
              case 'b':
                cp = '\b';
                break;
              case '0':
                cp = 0;
                break;
              default:
                cp = s.charAt(1);
            }
            return Optional.of(Integer.valueOf(cp));
          }
        });
    b.define(
        N_CHAR_SET_NODE, c.seq(
            c.lit("["),
            c.bref(N_INV_MARKER),
            c.star(c.bref(N_CHAR_SET_PART)),
            c.lit("]")
            ),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            String invMarker = (String) children.get(0);
            boolean isInverted = "^".equals(invMarker);

            List<ImmutableRangeSet<Integer>> chars = Lists.newArrayList();
            for (Object o : children.subList(1, children.size())) {
              @SuppressWarnings("unchecked")
              ImmutableRangeSet<Integer> charSetPart =
                  (ImmutableRangeSet<Integer>) o;
              chars.add(charSetPart);
            }
            ImmutableRangeSet<Integer> ranges = UniRanges.union(chars);
            if (isInverted) {
              ranges = UniRanges.invert(ranges);
            }
            return Optional.of(t.combinators.chars(ranges));
          }
        });
    b.define(
        N_CHAR_SET_NAME,
        c.seq(c.bref(N_NAME)),
        new OutputProcessor<ImmutableRangeSet<Integer>>() {
          @Override
          Optional<ImmutableRangeSet<Integer>> post(
              ImmutableList<Object> children, TreeProcessor t) {
            ProdName categoryName = (ProdName) children.get(0);
              return Optional.of(UniRanges.categories(categoryName.text));
          }
        });
    b.define(
        N_ANY_CHAR, c.lit("."),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            return Optional.of(t.combinators.anyChar());
          }
        });
    b.define(
        N_END_OF_FILE, c.lit("$"),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            return Optional.of(t.combinators.endOfInput());
          }
        });
    b.define(
        N_REFERENCE,
        c.seq(
            c.bref(N_NAME),
            c.star(
                c.seq(
                    c.lit("."),
                    c.bref(N_NAME)))),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            ProdName name;
            int n = children.size();
            if (n == 1) {
              name = (ProdName) children.get(0);
            } else {
              StringBuilder sb = new StringBuilder();
              for (ProdName part : runtimeCastList(children, ProdName.class)) {
                if (sb.length() != 0) {
                  sb.append('.');
                }
                sb.append(part.text);
              }
              name = new ProdName(sb.toString());
            }
            return Optional.of(t.combinators.ref(name));
          }
        });
    b.define(
        N_BOUNDED_REFERENCE,
        c.seq(
            c.lit("<"),
            c.bref(N_NAME),
            c.lit(">")),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            return Optional.of(t.combinators.bref((ProdName) children.get(0)));
          }
        });
    b.define(
        N_LOOKAHEAD_KIND, c.lits("?=", "?!", ""), STRING_IDENTITY);
    b.define(
        N_PAREN_BLOCK,
        c.seq(
            c.lit("("),
            c.bref(N_LOOKAHEAD_KIND),
            c.ref(N_IGNS),
            c.opt(
                c.seq(
                    c.bref(N_EMBED),
                    c.ref(N_IGNS)
                    )
                ),
            c.bref(N_OR_NODE),
            c.ref(N_IGNS),
            c.opt(
                c.seq(
                    c.bref(N_UNTIL),
                    c.ref(N_IGNS))),
            c.lit(")")
            ),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            String lookaheadKind = (String) children.get(0);
            StringTransform embedXform = null;
            int bodyIndex = 1;
            if (children.get(1) instanceof StringTransform) {
              embedXform = (StringTransform) children.get(1);
              bodyIndex = 2;
            }
            Combinator body = (Combinator) children.get(bodyIndex);
            Combinator untilLimit = null;
            if (bodyIndex + 1 < children.size()) {
              untilLimit = (Combinator) children.get(bodyIndex + 1);
            }

            Combinator combinator = body;
            switch (lookaheadKind) {
              case "?!":
                combinator = t.combinators.not(combinator);
                break;
              case "?=":
                combinator = t.combinators.la(combinator);
                break;
              case "":
                break;
              default:
                throw new AssertionError("lookaheadKind " + lookaheadKind);
            }

            if (embedXform != null) {
              combinator = t.combinators.embed(combinator, embedXform);
            }

            if (untilLimit != null) {
              combinator = t.combinators.until(combinator, untilLimit);
            }

            return Optional.of(combinator);
          }
        });
    b.define(
        N_EMBED,
        c.seq(
            c.lit("embed"),
            c.not(c.ref(N_NAME_PART)),
            c.ref(N_IGNS),
            c.bref(N_NAME),
            c.ref(N_IGNS),
            c.chars(':')),
        new OutputProcessor<StringTransform>() {
          @Override
          Optional<StringTransform> post(
              ImmutableList<Object> children, TreeProcessor t) {
            ProdName name = (ProdName) children.get(0);
            StringTransform xform = STRING_TRANSFORMS.get(name);
            if (xform == null) {
              t.error("No string transform " + name);
              return Optional.absent();
            } else {
              return Optional.of(xform);
            }
          }
        });
    b.define(
        N_UNTIL,
        c.seq(
            c.lit(":until"),
            c.ref(N_IGNS),
            c.bref(N_OR_NODE)),
        null);
    b.define(
        N_CURLY_BLOCK,
        c.or(
            c.bref(N_CURLY_IF),
            c.bref(N_CURLY_VAR),
            c.bref(N_CURLY_ASSIGN)),
        null);
    b.define(
        N_CURLY_VAR,
        c.seq(
            c.lit("{var"),
            c.not(c.ref(N_NAME_PART)),
            c.ref(N_IGNS),
            c.bref(N_VAR),
            c.ref(N_IGNS),
            c.lit("}"),
            c.ref(N_IGNS),
            c.ref(N_SEQ_NODE),
            c.ref(N_IGNS),
            c.lit("{/var"),
            c.ref(N_IGNS),
            c.lit("}")),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            Variable<?> var = (Variable<?>) children.get(0);
            Combinator body = (Combinator) children.get(1);
            return Optional.of(t.combinators.decl(var, body));
          }
        });
    b.define(
        N_VAR,
        c.seq(
            c.bref(N_NAME),
            c.ref(N_IGNS),
            c.opt(
                c.seq(
                    c.lit(":"),
                    c.ref(N_IGNS),
                    c.bref(N_NAME),
                    c.ref(N_IGNS),
                    c.bref(N_VAR_TYPE_VARIANT)))),
        new OutputProcessor<Variable<?>>() {
          @Override
          Optional<Variable<?>> post(
              ImmutableList<Object> children, TreeProcessor t) {
            ProdName varName = (ProdName) children.get(0);
            switch (children.size()) {
              case 1:
                return Optional.<Variable<?>>of(
                    t.getPreviouslyDeclaredVariable(varName.text)
                    .or(VOID_VAR));
              case 3:
                break;
              default:
                throw new AssertionError("" + children.size());
            }

            ProdName typeName = (ProdName) children.get(1);
            String typeVariant = (String) children.get(2);

            Optional<TypeGuard<?>> gOpt = t.types.getNominalType(typeName.text);
            if (!gOpt.isPresent()) {
              t.error("Unrecognized type name " + typeName);
              return Optional.<Variable<?>>of(VOID_VAR);
            }
            TypeGuard<?> g = gOpt.get();
            Variable<?> var;
            switch (typeVariant) {
              case "":
                var = Variable.create(varName.text, g);
                break;
              case "*":
                if (!(g instanceof RawTypeGuard)) {
                  t.error("Cannot create multi-variable from non enum-type");
                  return Optional.<Variable<?>>of(VOID_VAR);
                }
                Class<?> typeToken = ((RawTypeGuard<?>) g).typeToken;
                if (!Enum.class.isAssignableFrom(typeToken)) {
                  t.error("Cannot create multi-variable from non enum-type");
                  return Optional.<Variable<?>>of(VOID_VAR);
                }
                @SuppressWarnings("synthetic-access")
                MultiVariable<?> multivar = createMultiTypeUNSAFE(
                    varName.text, typeToken);
                var = multivar;
                break;
              default:
                throw new AssertionError(typeVariant);
            }

            return Optional.<Variable<?>>of(t.defineVariable(var));
          }
        });
    b.define(N_VAR_TYPE_VARIANT, c.opt(c.chars('*')), STRING_IDENTITY);
    b.define(
        N_CURLY_IF,
        c.seq(
            c.lit("{if"),
            c.not(c.ref(N_NAME_PART)),
            c.ref(N_IGNS),
            c.bref(N_VAR),
            c.ref(N_IGNS),
            c.bref(N_VAR_TEST),
            c.ref(N_IGNS),
            c.bref(N_VAR_VALUES),
            c.ref(N_IGNS),
            c.lit("}")),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            Variable<?> var = (Variable<?>) children.get(0);
            VarOp operator = (VarOp) children.get(1);
            ImmutableList<ProdName> values = runtimeCastList(
                children.subList(2, children.size()), ProdName.class);
            ImmutableList<?> valueCoerced = coerceValues(t, values, var);
            Combinator test = null;
            switch (operator) {
              case HAS:
                if (var instanceof MultiVariable) {
                  test = typeSafeMultivarTest(
                      t, (MultiVariable<?>) var, valueCoerced);
                } else {
                  t.error("Set membership can only be tested for multi-vars");
                  test = Combinators.error();
                }
                break;
              case IN:
                test = typeSafeVarTest(t, var, valueCoerced, false);
                break;
              case NOT_IN:
                test = typeSafeVarTest(t, var, valueCoerced, true);
                break;
            }
            if (test == null) {
              throw new AssertionError(operator);
            }
            return Optional.of(test);
          }
        });
    b.define(
        N_VAR_TEST,
        c.or(
            c.lits("==", "!=", "\u220b"),
            c.seq(
                c.or(
                    c.lits("has", "in"),
                    c.seq(
                        c.lit("not"),
                        c.not(c.ref(N_NAME_PART)),
                        c.ref(N_IGNS),
                        c.lit("in"))),
                c.not(c.ref(N_NAME_PART)))),
        new StringOutputProcessor<VarOp>() {
          @Override
          Optional<VarOp> process(String s, String rawChars, TreeProcessor t) {
            switch (s) {
              case "==": case "in":
                return Optional.of(VarOp.IN);
              case "!=":
                return Optional.of(VarOp.NOT_IN);
              case "has": case "\u220b":
                return Optional.of(VarOp.HAS);
              default:
                Preconditions.checkArgument(
                    s.startsWith("not") && s.endsWith("in"));
                return Optional.of(VarOp.NOT_IN);
            }
          }
        });
    b.define(
        N_VAR_VALUES,
        c.opt(
            c.seq(
                c.bref(N_VAR_VALUE),
                c.ref(N_IGNS),
                c.star(
                    c.seq(
                        c.lit(","),
                        c.ref(N_IGNS),
                        c.bref(N_VAR_VALUE))),
                c.ref(N_IGNS),
                c.opt(c.lit(",")))),
        null);
    b.define(
        N_VAR_VALUE,
        c.or(
            c.bref(N_NAME),
            c.seq(
                c.chars('('),
                c.ref(N_IGNS),
                c.bref(N_VAR_VALUES),
                c.ref(N_IGNS),
                c.chars(')'))),
        // TODO: There's currently no case where flattening values is incorrect,
        // but this seems dodgy.
        null);
    b.define(
        N_CURLY_ASSIGN,
        c.seq(
            c.lit("{"),
            c.ref(N_IGNS),
            c.bref(N_VAR),
            c.ref(N_IGNS),
            c.lit("="),
            c.ref(N_IGNS),
            c.bref(N_VAR_VALUES),
            c.ref(N_IGNS),
            c.lit("}")),
        new OutputProcessor<Combinator>() {
          @Override
          Optional<Combinator> post(
              ImmutableList<Object> children, TreeProcessor t) {
            Variable<?> var = (Variable<?>) children.get(0);
            List<ProdName> values = runtimeCastList(
                children.subList(1, children.size()), ProdName.class);
            List<?> valueList = coerceValues(t, values, var);
            Object value;
            switch (valueList.size()) {
              case 1:
                value = valueList.get(0);
                break;
              default:
                value = null;
                t.error("Cannot coerce " + values + " to unique value");
            }
            return Optional.of(typeSafeAssignment(t, var, value));
          }
        });
    b.define(N_INV_MARKER, c.opt(c.chars('^')), STRING_IDENTITY);
    b.define(
        N_CHAR_SET_PART,
        c.or(
            c.seq(c.lit("[:"), c.bref(N_CHAR_SET_NAME), c.lit(":]")),
            c.bref(N_CHAR_RANGE)),
        null);
    b.define(
        N_CHAR_RANGE,
        c.seq(
            c.bref(N_CHAR_RANGE_ENDPOINT),
            c.opt(c.seq(
                c.lit("-"),
                c.bref(N_CHAR_RANGE_ENDPOINT)))
            ),
        new OutputProcessor<ImmutableRangeSet<Integer>>() {
          @Override
          Optional<ImmutableRangeSet<Integer>> post(
              ImmutableList<Object> children, TreeProcessor t) {
            switch (children.size()) {
              case 1:
                return Optional.of(ImmutableRangeSet.of(
                    Range.singleton((Integer) children.get(0))));
              case 2:
                int start = (Integer) children.get(0);
                int end = (Integer) children.get(1);
                if (start > end) {
                  t.error(
                      "Range end-points out of order: " + start + " > " + end);
                  end = start;
                }
                return Optional.of(ImmutableRangeSet.of(
                    Range.closed(start, end)));
            }
            throw new AssertionError("" + children.size());
          }
        });
    b.define(
        N_CHAR_RANGE_ENDPOINT,
        c.or(
            c.bref(N_ESC),
            c.bref(N_CHAR_SET_CHAR)),
        null);
    b.define(
        N_CHAR_SET_CHAR,
        c.invChars('-', '^', ']', '\\', '\r', '\n'),
        new StringOutputProcessor<Integer>() {
          @Override
          Optional<Integer> process(
              String s, String rawChars, TreeProcessor t) {
            return Optional.of(Integer.valueOf(s.codePointAt(0)));
          }
        });
    b.define(N_SPACE, c.chars('\t', '\n', '\r', ' '), null);
    b.define(
        N_HEX,
        c.chars(UniRanges.union(
            UniRanges.btw('0', '9'),
            UniRanges.btw('A', 'F'), UniRanges.btw('a', 'f'))),
        null);
    b.define(N_HEX2, c.seq(c.ref(N_HEX), c.ref(N_HEX)), null);
    b.define(N_HEX4, c.seq(c.ref(N_HEX2), c.ref(N_HEX2)), null);

    LANG = b.lang.build().optimized();
    OUTPUT_PROCESSORS = b.processors.build();
  }

  static <T>
  ImmutableList<T> runtimeCastList(ImmutableList<Object> inp, Class<T> typ) {
    for (Object o : inp) {
      Preconditions.checkState(typ.isInstance(o));
    }
    @SuppressWarnings("unchecked")
    ImmutableList<T> checked = (ImmutableList<T>) inp;
    return checked;
  }

  @SuppressWarnings("unchecked")
  private static MultiVariable<?> createMultiTypeUNSAFE(
      String varName, Class<?> typeToken) {
    // This should be the case for all uses of the syntactic sugar mentioned
    // below but is not true for class Enum itself.
    Preconditions.checkArgument(Modifier.isFinal(typeToken.getModifiers()));
    return Variable.createMulti(
        varName,
        // This is type unsound because it assumes all
        // Enum instances I are defined as "final class I extends Enum<I>{...}"
        // which is not necessarily the case, but should be in all cases
        // that use the syntactic sugar "enum I{...}".
        typeToken.asSubclass(Enum.class));
  }

  /** A placeholder for variables that couldn't be parsed properly. */
  static Variable<?> VOID_VAR = Variable.create("void", Void.class);

  static <V> ImmutableList<V> coerceValues(
      TreeProcessor t, Iterable<ProdName> parsedValues, Variable<V> var) {
    TypeGuard<V> typeGuard = var.typeGuard;
    if (typeGuard instanceof SetTypeGuard<?>) {
      SetTypeGuard<?> stg = (SetTypeGuard<?>) typeGuard;
      ImmutableList<?> elements = coerceValues(
          t, parsedValues, Variable.create(var.name, stg.elementGuard));
      Optional<V> bundled = typeGuard.check(
          ImmutableSet.copyOf(elements));
      if (bundled.isPresent()) {
        return ImmutableList.<V>of(bundled.get());
      } else {
        t.error("Could not coerce values to set.");
      }
    } else if (typeGuard instanceof RawTypeGuard<?>) {
      Class<V> typeToken = ((RawTypeGuard<V>) typeGuard).typeToken;
      if (Enum.class.isAssignableFrom(typeToken)) {
        // Using raw Enum here is ok as long as it is the case that all concrete
        // enum classes have the form:
        //     final class Concrete extends Enum<Concrete> {...}
        // because we check using typeToken below.
        @SuppressWarnings("rawtypes")
        Class<? extends Enum> et = typeToken.asSubclass(Enum.class);
        ImmutableList.Builder<V> values = ImmutableList.builder();
        for (ProdName name : parsedValues) {
          Object coerced = null;
          try {
            @SuppressWarnings("unchecked")  // <?> has no implications.
            Enum<?> e = Enum.valueOf(et, name.text);
            coerced = e;
          } catch (@SuppressWarnings("unused") IllegalArgumentException ex) {
            // We will report an error below on an invalid name.
          }
          Optional<V> checked = coerced != null
              ? typeGuard.check(coerced)
              : Optional.<V>absent();
          if (checked.isPresent()) {
            values.add(checked.get());
          } else {
            t.error("Cannot coerce " + name + " to " + typeGuard);
          }
        }
        return values.build();
      } else {
        t.error("Cannot coerce values of type " + typeToken);
      }
    } else {
      t.error("Unrecognized type guard " + typeGuard);
    }
    return ImmutableList.of();
  }

  static <V extends Enum<V>> Combinator typeSafeMultivarTest(
      TreeProcessor t, MultiVariable<V> v, ImmutableList<?> values) {
    V value = null;
    if (values.isEmpty()) {
      t.error("Missing value for multi-variable test");
    } else {
      // The values should have been bundled into a set during coercion
      // when the coercer saw a SetTypeGuard<V>.
      Preconditions.checkArgument(values.size() == 1);
      Optional<ImmutableSet<V>> valueSetOpt = v.typeGuard.check(values.get(0));
      if (valueSetOpt.isPresent()) {
        ImmutableSet<V> valueSet = valueSetOpt.get();
        if (valueSet.size() == 1) {
          value = valueSet.iterator().next();
        } else {
          t.error("Multi-variable tests can only be done on one variable");
        }
      } else {
        t.error("Values rejected by guard");
      }
    }
    if (v != null) {
      return t.combinators.has(v, value);
    }
    return Combinators.error();
  }

  static <V>
  Combinator typeSafeVarTest(
      TreeProcessor t, Variable<V> var, ImmutableList<?> values,
      boolean inverted) {
    Collection<V> valuesTypeSafe = new ArrayList<>();
    for (Object value : values) {
      Optional<V> checked = var.typeGuard.check(value);
      if (checked.isPresent()) {
        valuesTypeSafe.add(checked.get());
      } else {
        t.error("" + value + " failed guard for " + var);
      }
    }
    if (inverted) {
      valuesTypeSafe = invertValues(t, var, valuesTypeSafe);
    }
    return t.combinators.<V>in(var, valuesTypeSafe);
  }

  static <V>
  ImmutableSet<V> invertValues(
      TreeProcessor t, Variable<V> var, Collection<V> values) {
    if (var.typeGuard instanceof RawTypeGuard) {
      Class<V> typeToken = ((RawTypeGuard<V>) var.typeGuard).typeToken;
      if (Enum.class.isAssignableFrom(typeToken)) {
        @SuppressWarnings("rawtypes")
        Class<? extends Enum> et = typeToken.asSubclass(Enum.class);
        @SuppressWarnings("unchecked")
        Set<V> all = EnumSet.allOf(et);
        all.removeAll(values);
        return ImmutableSet.copyOf(all);
      }
    }

    t.error("Cannot invert values for " + var.typeGuard);
    return ImmutableSet.of();
  }

  static <V> Combinator typeSafeAssignment(
      TreeProcessor t, Variable<V> var, Object value) {
    Optional<V> checked = var.typeGuard.check(value);
    if (checked.isPresent()) {
      return t.combinators.set(var, checked.get());
    }
    t.error("Right-hand side " + value + " does not pass guard for " + var);
    return Combinators.error();
  }

  enum VarOp {
    IN,
    NOT_IN,
    HAS,
  }
}
