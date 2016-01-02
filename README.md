# Template Analysis

A pausable parser that consumes template text
and provides annotations that make clear the
structure of the strings produced by the template.

## Example

For an HTML template:

<pre>
Hello, &lt;a href="<u>{$world}</u>"&gt;World&lt;/a&gt;!
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
