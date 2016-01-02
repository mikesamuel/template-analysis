/**
 * Defines a grammar for grammars so that grammars can be parsed from a file.
 *
 * <p>
 * {@link com.google.template.autoesc.file.LanguageParser} is the main entry
 * point.
 * </p>
 *
 * <p>
 * This parser is structured as a
 * {@linkplain com.google.template.autoesc.file.GrammarGrammar grammar}
 * that produces an output which is converted to a
 * {@linkplain com.google.template.autoesc.out.PartialOutput parse tree}
 * which is fed to a
 * {@linkplain com.google.template.autoesc.file.TreeProcessor tree processor}
 * which converts the parse tree to a
 * {@linkplain com.google.template.autoesc.Language language} without
 * building a complete intermediate abstract syntax tree.
 *
 * @author Mike Samuel (mikesamuel@gmail.com)
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.google.template.autoesc.file;