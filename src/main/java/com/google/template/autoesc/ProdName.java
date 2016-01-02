package com.google.template.autoesc;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;


/** The name of a non-terminal. */
public final class ProdName implements Comparable<ProdName> {
  /** A {@link #isIdentifier valid identifier}. */
  public final String text;

  /** @param text a {@link #isIdentifier valid identifier}. */
  public ProdName(String text) {
    Preconditions.checkArgument(isIdentifier(text));
    this.text = text;
  }

  /** @return prefix.text */
  public ProdName withPrefix(ProdName prefix) {
    return new ProdName(prefix.text + "." + text);
  }

  @Override
  public int hashCode() {
    return text.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ProdName other = (ProdName) obj;
    return text.equals(other.text);
  }

  @Override
  public String toString() {
    return text;
  }


  /** True for valid production names. */
  public static boolean isIdentifier(String s) {
    int n = s.length();
    if (n == 0) { return false; }
    int i = 0;
    // A dotted path.

    int cp0 = s.codePointAt(i);
    if (!(Character.isLetter(cp0) || cp0 == '_' || cp0 == '$')) {
      return false;
    }
    while (i < n) {
      for (int nc; i < n; i += nc) {
        int cp = s.codePointAt(i);
        if (cp == '.') {
          if (i + 1 == n) { return false; }
          ++i;
          break;
        }
        if (!(Character.isLetterOrDigit(cp) || cp == '_' || cp == '$')) {
          return false;
        }
        nc = Character.charCount(cp);
      }
    }
    return true;
  }

  @Override
  public int compareTo(ProdName o) {
    return text.compareTo(o.text);
  }
}
