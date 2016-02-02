package com.google.template.autoesc.inp;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class CaseSensitivityTest extends TestCase {

  @Test
  public static final void testRangesCanonical() {
    for (int cp :
         new int[] { 0, 1, 'A', 'B', 'Z', 'a', 'b', 'z', '0', '9',
                     Character.MAX_CODE_POINT }) {
      String cpText = "U+" + Integer.toString(cp, 16);
      RangeSet<Integer> sensitiveCps = CaseSensitivity.SENSITIVE.enumerate(cp);
      assertRangesAreCanonical(cpText, sensitiveCps);
      assertMemberCount(cpText, sensitiveCps, 1);
      assertContains(cpText, sensitiveCps, cp);

      RangeSet<Integer> ignoreCps = CaseSensitivity.IGNORE.enumerate(cp);
      assertRangesAreCanonical(cpText, ignoreCps);
      assertMemberCount(cpText, ignoreCps, Character.isLetter(cp) ? 2 : 1);
      assertContains(cpText, ignoreCps, cp);
      assertContains(cpText, ignoreCps, Character.toLowerCase(cp));
      assertContains(cpText, ignoreCps, Character.toUpperCase(cp));
    }
  }

  private static void assertRangesAreCanonical(
      String msg, RangeSet<Integer> cps) {
    for (Range<Integer> r : cps.asRanges()) {
      Assert.assertEquals(
          msg, r, r.canonical(UniRanges.CodepointsDomain.INSTANCE));
    }
  }

  private static void assertContains(
      String msg, RangeSet<Integer> cps, int cp) {
    Assert.assertTrue(msg, cps.contains(cp));
  }

  private static void assertMemberCount(
      String msg, RangeSet<Integer> cps, int expected) {
    long memberCount = 0;
    for (Range<Integer> r : cps.asRanges()) {
      int lower = r.lowerEndpoint();
      if (!r.contains(lower)) {
        ++lower;
      }
      int upper = r.upperEndpoint();
      if (!r.contains(upper)) {
        --upper;
      }
      memberCount += UniRanges.CodepointsDomain.INSTANCE.distance(lower, upper) + 1;
    }
    Assert.assertEquals(msg, expected, memberCount);
  }

}
