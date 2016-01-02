package com.google.template.autoesc;

/**
 * Just the bits of a parser that can be joined so that we can
 * separate the concerns of parsing from joining branches.
 */
interface Joinable {
  /** The branch being joined. */
  ParseWatcher.Branch getBranch();
  /** The end state of the branch being joined. */
  Parse getParse();
}
