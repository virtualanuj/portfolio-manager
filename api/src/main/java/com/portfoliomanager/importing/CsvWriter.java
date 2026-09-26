package com.portfoliomanager.importing;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Writes CSV text. Like {@link CsvParser}, the only place that touches Commons CSV for output. */
public final class CsvWriter {

    private static final CSVFormat FORMAT =
            CSVFormat.RFC4180.builder().setRecordSeparator("\n").get();

    private CsvWriter() {}

    public static String write(List<String> header, List<List<String>> rows) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, FORMAT)) {
            printer.printRecord(header);
            for (List<String> row : rows) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /**
     * Guards free text against spreadsheet formula injection: a cell starting with = + - or @ is
     * prefixed with an apostrophe so it opens as text.
     */
    public static String safeText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
    }
}
