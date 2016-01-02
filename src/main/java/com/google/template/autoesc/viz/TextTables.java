package com.google.template.autoesc.viz;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * Utilities for dumping series of {@link Visualizable}s in tabular form.
 */
public final class TextTables {
  /** A column in a table. */
  public static final class Col {
    final String name;
    final Iterable<? extends Visualizable> rows;
    final Function<? super Visualizable, ? extends String> decorator;

    Col(String name, Iterable<? extends Visualizable> rows,
        Function<? super Visualizable, ? extends String> decorator) {
      this.name = name;
      this.rows = rows;
      this.decorator = decorator;
    }
  }

  /** The default table cell decorator */
  public static final Function<Visualizable, String> DEFAULT_DECORATOR =
      new Function<Visualizable, String> () {
    @Override
    public String apply(Visualizable v) {
      StringBuilder sb = new StringBuilder();
      TextVizOutput vizOut = new TextVizOutput(sb);
      try {
        v.visualize(DetailLevel.SHORT, vizOut);
      } catch (IOException e) {  // Should not throw given StringBuilder
        Throwables.propagate(e);
      }
      return sb.toString();
    }
  };

  /** */
  public static Function<Visualizable, String> typeDecorator(
      final Function<? super Visualizable, ? extends String> decorator) {
    return new Function<Visualizable, String>() {
      @Override
      public String apply(Visualizable input) {
        String typeName = input != null
            ? input.getClass().getSimpleName() : "<null>";
        return decorator.apply(input) + " : " + typeName;
      }
    };
  }

  /** Creates a column in a table*/
  public static Col column(String name, Iterable<? extends Visualizable> rows) {
    return column(name, rows, DEFAULT_DECORATOR);
  }

  /** Creates a column in a table*/
  public static Col column(
      String name, Iterable<? extends Visualizable> rows,
      Function<? super Visualizable, ? extends String> decorator) {
    return new Col(name, rows, decorator);
  }

  /** Writes a table with the given column to out. */
  public static void appendTable(Appendable out, Col... cols)
  throws IOException {
    appendTable(out, ImmutableList.copyOf(cols));
  }

  /** Writes a table with the given column to out. */
  @SafeVarargs
  public static void appendTable(PrintStream out, Col... cols) {
    try {
      appendTable((Appendable) out, cols);
    } catch (IOException ex) {
      Throwables.propagate(ex);
    }
  }

  /** Writes a table with the given column to out. */
  public static void appendTable(PrintStream out, List<? extends Col> cols) {
    try {
      appendTable((Appendable) out, cols);
    } catch (IOException ex) {
      Throwables.propagate(ex);
    }
  }

  /** Writes a table with the given column to out. */
  public static void appendTable(Appendable out, List<? extends Col> cols)
  throws IOException {
    final int tableWidth;
    if (out == System.out || out == System.err) {
      tableWidth = jline.TerminalFactory.get().getWidth();
    } else {
      tableWidth = 120;
    }

    int nc = cols.size();
    String[][] table = new String[nc][];
    int columnWidthLimit = nc != 0 ? tableWidth / nc : tableWidth;
    for (int i = 0; i < nc; ++i) {
      Col col = cols.get(i);
      ImmutableList<Visualizable> vizList = ImmutableList.copyOf(col.rows);
      int nr = vizList.size();
      String[] rows = new String[nr + 1];
      rows[0] = col.name;
      table[i] = rows;
      for (int j = 0; j < nr; ++j) {
        String cellText = col.decorator.apply(vizList.get(j));
        if (cellText.length() > columnWidthLimit) {
          cellText = new StringBuilder(columnWidthLimit)
              // TODO: might split code-point
              .append(cellText, 0, columnWidthLimit - 1)
              .append("\u2026").toString();
        }
        rows[j + 1] = cellText
            // HACK: Pause sign breaks alignment in Eclipse console.
            .replace("\u2759\u2759", "#");
      }
    }
    // Pad the table if there seems to be a reason to bottom-justify.
    {
      int maxNRows = 0;
      Set<String> lastValues = new HashSet<>();
      for (String[] column : table) {
        int nr = column.length;
        maxNRows = Math.max(maxNRows, nr);
        if (nr != 0) { lastValues.add(column[nr - 1]); }
      }
      // Bottom justify if the number of unique last values is small.
      if (lastValues.size() <= table.length / 2) {
        for (int i = 0; i < table.length; ++i) {
          String[] column = table[i];
          int nr = column.length;
          if (nr == 0 || nr == maxNRows) { continue; }
          String[] justifiedColumn = new String[maxNRows];
          Arrays.fill(justifiedColumn, "");
          justifiedColumn[0] = column[0];
          System.arraycopy(
              column, 1, justifiedColumn, maxNRows - (nr - 1), nr - 1);
          table[i] = justifiedColumn;
        }
      }
    }
    appendTable(table, out);
  }

  private static void appendTable(String[][] table, Appendable out)
  throws IOException {
    int nc = table.length;
    int[] maxColWidths = new int[nc];
    for (int i = 0; i < nc; ++i) {
      for (String cell : table[i]) {
        maxColWidths[i] = Math.max(maxColWidths[i], cell.length());
      }
    }
    int maxCellWidth = 0;
    for (int maxColWidth : maxColWidths) {
      maxCellWidth = Math.max(maxCellWidth, maxColWidth);
    }
    String padding;
    {
      char[] spaces = new char[maxCellWidth];
      Arrays.fill(spaces, ' ');
      padding = new String(spaces);
    }
    int maxRowCount = 0;
    for (String[] rows : table) {
      maxRowCount = Math.max(maxRowCount, rows.length);
    }
    for (int j = 0; j < maxRowCount; ++j) {
      writeBreakLine(maxColWidths, j != 0, true, maxCellWidth, out);
      for (int i = 0; i < nc; ++i) {
        out.append(i == 0 ? "\u2551 " : " \u2551 ");
        String[] rows = table[i];
        String cell = j < rows.length ? rows[j] : "";
        String paddingLeft, paddingRight;
        int nPadding = maxColWidths[i] - cell.length();
        if (j == 0) {
          paddingLeft = padding.substring(0, nPadding / 2);
          paddingRight = padding.substring(0, nPadding - paddingLeft.length());
        } else {
          paddingLeft = "";
          paddingRight = padding.substring(0, nPadding);
        }
        if (paddingLeft.length() != 0) {
          out.append(paddingLeft);
        }
        out.append(cell);
        if (paddingRight.length() != 0) {
          out.append(paddingRight);
        }
      }
      out.append(" \u2551\n");
    }
    writeBreakLine(maxColWidths, true, false, maxCellWidth, out);
  }

  private static void writeBreakLine(
      int[] colWidths, boolean joinUp, boolean joinDown,
      int maxCellWidth, Appendable out)
  throws IOException {
    int joinIndex = (joinUp ? 2 : 0) | (joinDown ? 1 : 0);
    String horiz;
    {
      char[] chars = new char[maxCellWidth + 2];
      Arrays.fill(chars, '\u2550');
      horiz = new String(chars);
    }
    for (int i = 0; i < colWidths.length; ++i) {
      out.append(
          (i == 0 ? "#\u2554\u255A\u2560" : "\u2550\u2566\u2569\u256C")
          .charAt(joinIndex));

      out.append(horiz.substring(0, colWidths[i] + 2));
    }
    out.append("#\u2557\u255D\u2563".charAt(joinIndex) + "\n");
  }
}
