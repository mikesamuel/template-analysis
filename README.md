# Template Analysis

A pausable parser that consumes template text
and provides annotations that make clear the
structure of the strings produced by the template.

## Problem

Network messages sent by web applications are often composed from
untrustworthy inputs leading to security vulnerabilities like XSS, SQL
Injection, Shell injection, etc.

Tools like HTML template languages would benefit from an understanding
of the structure of the messages they produce.

For example, it is obvious to a human reader that the HTML template

```HTML
<p>{$text}</p>
```

is meant to specify a paragraph tag containing text specified by the expression
`$text`.

This is not necessarily the case, for example when `$text` is

```HTML
<script>alert(1337)</script>
```

and, because of the way languages nest, can execute attacker-specified
code with the permissions of the server's origin.

Template languages can make explicit the structure of the code
(e.g. XHP) and take steps to ensure that the author's apparent intent
is preserved even in the face of inputs controlled by an attacker.

This complicates the template language, ties it to a particular output
language, and makes it difficult to port common template patterns like

```HTML
# main.template
{import header}

<p>{$text</p>

{import footer}

# header.template
<html><head>...</head><body>

# footer.template
</body></html>
```

where an element crosses compilation units.

This library fills this gap by providing
1. a grammar-based approach to describing network message languages
2. operators for handling language nesting
3. a parser that can be paused at a hole in the input
4. a way to parse states can be forked and joined based on a template's control-flow
5. a way to represent the parse of an unbalanced portion of an input

This could prove of value to
1. Auto-escaping template languages.
2. Linters that identify common errors in templates.
3. Automated refactoring.

## Example

For an HTML template:

<pre>
Hello, &lt;a href="<b>{$world}</b>"&gt;World&lt;/a&gt;!
</pre>

The parser might get two chunks of input:

1. `Hello, <a href="`
2. `">World</a>!`

and provide an annotated output like

 * String: `"Hello, "`
 * Tag:
   * String: `"<a "`
   * Attribute
     * String: `"href=\""`
     * Embedded URL
       * Template Hole
     * String: `"\""`
   * String: `">"`
 * String: `"World"`
 * Tag:
   * String: `"</a>"`
 * String: `"!"`

This structure provides enough information to decide how to safely
escape `{$world}` to preserve template author intent and security even
if the result of `$world` is controlled by an attacker.

## Pausing
The parser is *pausable* meaning it does not require
the entire input.  Parses can be forked and joined to allow
static analysis of templates.

## Branching
Parses can be *forked* and *joined*.  When a template
branches, for example due to an <tt>if</tt> or sub-template call,
the parse state can be forked and each alternative followed.
Once all alternatives have been analyzed, their end states can be
joined on the other side of the branching construct.

## Analytic
The parser is *analytic* meaning it does not simultaneously
follow all paths through the grammar.  Instead, branches are tried
in-order and the first-passing branch is the one handled.

This makes it less suitable for parsing ambiguous natural-languages
but makes it easier to handle programming languages and to
write a grammar that puts dangerous special-cases first.

## Side effects
The parser is *side-effect free*.  It produces an output stream
that can be replayed after a consistent end state is reached
to achieve the desired side-effects.

## Combinator
The parser is based on *combinator*s but not in the traditional
way.  Each step in a parse maps more closely to a function call, function
exit, consume of an input character, or append to an output buffer so
mirrors the way a recursive-descent parser operates in practice.

Since the parser is combinator based, it can handle features like
lookahead, and scoped variables that make it much easier to handle
schema-based languages without exploding the grammar.
