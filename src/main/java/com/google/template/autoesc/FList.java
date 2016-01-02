package com.google.template.autoesc;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;


/**
 * A functional list consisting of a head and tail.
 * @param <T> List element type.
 */
public abstract class FList<T> implements Iterable<T> {
  private FList() {
  }

  /** The empty list. */
  @SuppressWarnings("unchecked")
  public static <T> FList<T> empty() {
    return (FList<T>) EmptyFList.INSTANCE;
  }

  /** A list that consists of hd followed by the elements on tl. */
  public static <T> FList<T> cons(T hd, FList<T> tl) {
    return new ConsFList<>(hd, tl);
  }

  /** of(x, y, z) is cons(z, cons(y, cons(x, empty()))) */
  @SafeVarargs  // Not modified
  public static <T> FList<T> of(T... els) {
    FList<T> ls = empty();
    for (T el : els) {
      ls = cons(el, ls);
    }
    return ls;
  }

  /** O(1) */
  public abstract int length();

  /** True if the list has neither a head nor a tail. */
  public abstract boolean isEmpty();
  /**
   * The head of the list.
   * @throws NoSuchElementException iff !{@link #isEmpty}.
   */
  public abstract T hd() throws NoSuchElementException;
  /** The head of the list or absent if empty. */
  public abstract Optional<T> hdOpt();
  /**
   * The tail of the list.
   * @throws NoSuchElementException iff !{@link #isEmpty}.
   */
  public abstract FList<T> tl() throws NoSuchElementException;
  /** The tail of the list or absent if empty. */
  public abstract Optional<FList<T>> tlOpt();

  @Override
  public final Iterator<T> iterator() {
    return new FListIterator<>(this);
  }

  /** O(n) */
  public final FList<T> subList(int n) {
    Preconditions.checkArgument(n >= 0);

    FList<T> ls = this;
    for (int i = n; --i >= 0;) {
      ls = ls.tl();
    }
    return ls;
  }

  /**
   * A list with all elements starting with the head if any.
   * This is O(n).
   */
  public final ImmutableList<T> toList() {
    return ImmutableList.copyOf(this);
  }

  /**
   * A list with all elements ending with the head if any.
   * This is O(n).
   */
  public final ImmutableList<T> toReverseList() {
    return ImmutableList.copyOf(this).reverse();
  }

  /**
   * A list of the same length such that {@code map(f).toList().get(i))} is
   * {@code f.apply(toList().get(i))}.
   */
  public final <O> FList<O> map(Function<T, O> f) {
    return isEmpty()
        ? FList.<O>empty()
        : FList.<O>cons(f.apply(hd()), tl().map(f));
  }

  /**
   * A list like {@code map(f)} filtered to remove absent elements and then
   * mapped again to flatten out optionals.
   * @param f maps elements of this to elements on the output.
   *    An absent return value means no output element corresponds to the input
   *    element.
   * @return a list consisting of the present return values of f applied to the
   *    elements of this.
   */
  public final <O> FList<O> mapFiltered(Function<T, Optional<O>> f) {
    FList<T> ls = this;
    // Keep the depth of the stack small by
    while (!ls.isEmpty()) {
      Optional<O> newHd = f.apply(ls.hd());
      if (newHd.isPresent()) {
        return FList.cons(newHd.get(), ls.tl().mapFiltered(f));
      } else {
        ls = ls.tl();
      }
    }
    return FList.<O>empty();
  }

  /** The reverse of this list. */
  public FList<T> rev() {
    return FList.<T>empty().appendRev(this);
  }

  /**
   * The list that contains all the elements of this list followed by the
   * elements of {@link #rev ls.rev()}.
   * @param ls the list to append.
   * @return this tail-most with ls.rev() head-most.
   */
  public FList<T> appendRev(FList<T> ls) {
    FList<T> app = this;
    for (FList<T> rest = ls; !rest.isEmpty(); rest = rest.tl()) {
      app = FList.cons(rest.hd(), app);
    }
    return app;
  }

  /**
   * The list with all and only elements of this matching p in reverse order.
   * @param p a return value of true means the input appears on the output.
   * @return the filtered version of this in reverse order.
   */
  public FList<T> filterRev(Predicate<? super T> p) {
    return filterRevOnto(p, FList.<T>empty());
  }

  /**
   * {@code x.filterRevOnto(p, y)}.equals(y.revAppend(x.filter(p)))} but this
   * method is more efficient.
   */
  public FList<T> filterRevOnto(Predicate<? super T> p, FList<T> onto) {
    FList<T> filtered = onto;
    for (T x : this) {
      if (p.apply(x)) {
        filtered = cons(x, filtered);
      }
    }
    return filtered;
  }

  /**
   * The list with all and only elements of this matching p in the same order.
   */
  public FList<T> filter(Predicate<? super T> p) {
    FList<T> filtered = FList.empty();
    boolean filteredSome = false;
    for (T x : this) {
      if (p.apply(x)) {
        filtered = cons(x, filtered);
      } else {
        filteredSome = true;
      }
    }
    return filteredSome ? filtered.rev() : this;
  }


  @SuppressWarnings("synthetic-access")
  private static final class EmptyFList<T> extends FList<T> {

    static final EmptyFList<?> INSTANCE = new EmptyFList<>();

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public int length() {
      return 0;
    }

    @Override
    public T hd() throws NoSuchElementException {
      throw new NoSuchElementException();
    }

    @Override
    public Optional<T> hdOpt() {
      return Optional.absent();
    }

    @Override
    public FList<T> tl() throws NoSuchElementException {
      throw new NoSuchElementException();
    }

    @Override
    public Optional<FList<T>> tlOpt() {
      return Optional.absent();
    }

    @Override
    public String toString() {
      return "[]";
    }
  }


  private static final class ConsFList<T> extends FList<T> {
    private final T hd;
    private final FList<T> tl;
    private final int length;
    private int hashCode;

    @SuppressWarnings("synthetic-access")
    ConsFList(T hd, FList<T> tl) {
      this.hd = hd;
      this.tl = tl;
      this.length = tl.length() + 1;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public int length() {
      return length;
    }

    @Override
    public T hd() {
      return hd;
    }

    @Override
    public Optional<T> hdOpt() {
      return Optional.of(hd);
    }

    @Override
    public FList<T> tl() {
      return tl;
    }

    @Override
    public Optional<FList<T>> tlOpt() {
      return Optional.of(tl);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      sb.append(hd);
      for (FList<T> ls = tl; !ls.isEmpty(); ls = ls.tl()) {
        sb.append(", ");
        sb.append(ls.hd());
      }
      sb.append(']');
      return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ConsFList)) { return false; }
      ConsFList<?> that = (ConsFList<?>) o;
      return Objects.equal(this.hd, that.hd) && tl.equals(that.tl);
    }

    @Override
    public int hashCode() {
      if (hashCode == 0) {
        int hc = Objects.hashCode(hd, tl);
        hashCode = hc != 0 ? hc : -1;
      }
      return hashCode;
    }
  }
}


final class FListIterator<T> implements Iterator<T> {
  private FList<T> ls;

  FListIterator(FList<T> ls) {
    this.ls = ls;
  }

  @Override
  public boolean hasNext() {
    return !ls.isEmpty();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public T next() throws NoSuchElementException {
    if (ls.isEmpty()) {
      throw new NoSuchElementException();
    }
    T hd = ls.hd();
    ls = ls.tl();
    return hd;
  }
}
