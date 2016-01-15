package com.google.template.autoesc.combimpl;

import org.junit.Test;

import com.google.template.autoesc.Combinators;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class CombinatorToStringTest extends TestCase {

  @Test
  public static void testSuffixOperators() {
    Combinators c = Combinators.get();
    assertEquals(".*", c.star(c.anyChar()).toString());
    assertEquals(".+", c.plus(c.anyChar()).toString());
    assertEquals(".?", c.opt(c.anyChar()).toString());
    assertEquals(".*", c.opt(c.plus(c.anyChar())).toString());
    assertEquals(".?+", c.plus(c.opt(c.anyChar())).toString());
  }

}
