/**
 * A pausable parser that consumes template text
 * and provides annotations that make clear the
 * structure of the strings produced by the template.
 *
 * <h3>Example</h3>
 * <p>For an HTML template:
 * <pre>
 *   Hello, &lt;a href="<u>{$world}</u>"&gt;World&lt;/a&gt;!
 * </pre>
 * The parser might get two chunks of input:
 * <ol>
 *   <li><code>"Hello, &lt;a href=\""</code>
 *   <li><code>"\"&gt;World&lt;/a&gt;!"</code>
 * </ol>
 * and provide an annotated output like
 * <ul>
 *   <li>String: <code>"Hello, "</code></li>
 *   <li>Tag: <ul>
 *     <li>String: <code>"&lt;a "</code></li>
 *     <li>Attribute : <ul>
 *       <li>String: <code>"href=\""</code></li>
 *       <li>Embedded URL <ul><li>Pause</li></ul></li>
 *       <li>String: <code>"\"</code></li>
 *     </ul></li>
 *     <li>String: <code>"&gt;"</code></li>
 *   </ul></li>
 *   <li>String: <code>"World"</code>
 *   <li>Tag: <ul><li>String: <code>"&lt;/a&gt;"</code></li></ul>
 *   <li>String: <code>"!"</code></li>
 * </ul>
 * <p>
 * This structure provides enough information to decide how
 * to safely escape <code><u>{$world}</u></code> to preserve
 * template author intent and security even if the value of
 * <i>$world</i> is controlled by an attacker.
 * </p>
 *
 * <h3>Starting Points</h3>
 * <ul>
 * <li>{@link com.google.template.autoesc.Parser} -
 *   actually performs a parse.</li>
 * <li>{@link com.google.template.autoesc.Language} - defines a grammar</li>
 * <li>{@link com.google.template.autoesc.Combinators} -
 *   useful for defining grammars.
 *   See also {@link com.google.template.autoesc.grammars grammars}
 *   sub-package.</li>
 * <li>{@link com.google.template.autoesc.demo.DemoServer} -
 *   for debugging and visualizing parses.</li>
 * </ul>
 *
 * <p>
 * The parser is <b>pausable</b> meaning it does not require
 * the entire input.  Parses can be forked and joined to allow
 * static analysis of templates.
 * </p>
 *
 * <p>
 * The parser is <b>analytic</b> meaning it does not simultaneously
 * follow all paths through the grammar.  Instead, branches are tried
 * in-order.
 * </p>
 *
 * <p>
 * The parser is <b>side-effect free</b>.  It produces an output stream
 * that can be replayed after a consistent end state is reached
 * to achieve the desired side-effects.
 * </p>
 *
 * <p>
 * The parser is based on <b>combinator</b>s but not in the traditional
 * way.  Each step in a parse maps more closely to a function call, function
 * exit, consume of an input character, or append to an output buffer so
 * mirrors the way a recursive-descent parser operates in practice.
 * </p>
 *
 * <p>
 * Parses can be <b>forked</b> and <b>joined</b>.  When a template
 * branches, for example due to an <tt>if</tt> or sub-template call,
 * the parse state can be forked and each alternative followed.
 * Once all alternatives have been analyzed, their end states can be
 * joined on the other side of the branching construct.
 * </p>
 *
 * @author Mike Samuel (mikesamuel@gmail.com)
 */
@javax.annotation.ParametersAreNonnullByDefault
//@org.eclipse.jdt.annotation.NonnullByDefault
package com.google.template.autoesc;
