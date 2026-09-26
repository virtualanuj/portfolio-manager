package com.portfoliomanager.importing;

import java.util.Map;

/**
 * One data row. {@code lineNo} is the file line the row ends on (counting the header as line 1), so
 * for ordinary single-line rows it matches the line number in a text editor.
 */
public record CsvRow(int lineNo, Map<String, String> values) {

    public CsvRow {
        values = Map.copyOf(values);
    }

    /** The trimmed value of a column, or an empty string when absent or blank. */
    public String get(String column) {
        return values.getOrDefault(column, "");
    }
}
