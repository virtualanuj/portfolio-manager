package com.portfoliomanager.importing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads the generic import schema (spec section 8). A thin wrapper over Apache Commons CSV: the
 * rest of {@code importing} never touches the library. It handles the byte order mark, the 2 MB and
 * 5,000-row limits, case-insensitive headers, and turns library errors into {@link
 * CsvFormatException} with a line number.
 */
public final class CsvParser {

    /** Every column the schema defines. */
    public static final Set<String> KNOWN_COLUMNS =
            Set.of(
                    "date",
                    "account",
                    "symbol",
                    "asset_type",
                    "type",
                    "quantity",
                    "price",
                    "split_ratio",
                    "source_id",
                    "account_type",
                    "note");

    private static final List<String> REQUIRED_COLUMNS =
            List.of("date", "account", "symbol", "asset_type", "type");

    private static final CSVFormat FORMAT =
            CSVFormat.RFC4180
                    .builder()
                    .setIgnoreSurroundingSpaces(true)
                    .setIgnoreEmptyLines(false)
                    .get();

    private CsvParser() {}

    public static CsvDocument parse(InputStream input) {
        byte[] bytes = readBounded(input);
        int offset = hasByteOrderMark(bytes) ? 3 : 0;
        InputStreamReader reader =
                new InputStreamReader(
                        new ByteArrayInputStream(bytes, offset, bytes.length - offset),
                        StandardCharsets.UTF_8);

        try (CSVParser parser = FORMAT.parse(reader)) {
            return read(parser);
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            throw new CsvFormatException("The file is not valid CSV: " + e.getMessage(), lineOf(e));
        }
    }

    /** Reads at most the size limit plus one byte, so an oversized file is rejected unparsed. */
    private static byte[] readBounded(InputStream input) {
        try {
            byte[] bytes = input.readNBytes(CsvLimits.MAX_BYTES + 1);
            if (bytes.length > CsvLimits.MAX_BYTES) {
                throw new CsvTooLargeException();
            }
            return bytes;
        } catch (IOException e) {
            throw new CsvFormatException("The file could not be read: " + e.getMessage());
        }
    }

    private static boolean hasByteOrderMark(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private static CsvDocument read(CSVParser parser) {
        Iterator<CSVRecord> records = parser.iterator();

        CSVRecord headerRecord = nextNonBlank(records);
        if (headerRecord == null) {
            throw new CsvFormatException("The file is empty");
        }
        List<String> headers = new ArrayList<>();
        headerRecord.forEach(name -> headers.add(name.trim().toLowerCase(Locale.ROOT)));

        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !headers.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new CsvFormatException(
                    "The header is missing required columns: " + String.join(", ", missing));
        }
        List<String> warnings = new ArrayList<>();
        for (String header : headers) {
            if (!KNOWN_COLUMNS.contains(header)) {
                warnings.add("Column \"" + header + "\" is not part of the schema and was ignored");
            }
        }

        List<CsvRow> rows = new ArrayList<>();
        CSVRecord record;
        while ((record = nextNonBlank(records)) != null) {
            if (rows.size() == CsvLimits.MAX_ROWS) {
                throw new CsvFormatException("The file has more than 5,000 rows");
            }
            rows.add(new CsvRow((int) parser.getCurrentLineNumber(), valuesOf(headers, record)));
        }
        if (rows.isEmpty()) {
            throw new CsvFormatException("The file has a header but no rows");
        }
        return new CsvDocument(List.copyOf(headers), List.copyOf(rows), List.copyOf(warnings));
    }

    private static Map<String, String> valuesOf(List<String> headers, CSVRecord record) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size() && i < record.size(); i++) {
            String header = headers.get(i);
            if (KNOWN_COLUMNS.contains(header)) {
                values.put(header, record.get(i).trim());
            }
        }
        return values;
    }

    private static CSVRecord nextNonBlank(Iterator<CSVRecord> records) {
        while (records.hasNext()) {
            CSVRecord record = records.next();
            boolean blank = record.size() == 1 && record.get(0).isBlank();
            if (!blank) {
                return record;
            }
        }
        return null;
    }

    private static final Pattern LINE_IN_MESSAGE = Pattern.compile("(?:line: |startline )(\\d+)");

    /** Commons CSV puts the line into its messages, for example "(startline 2) EOF reached...". */
    private static int lineOf(Exception e) {
        Matcher matcher = LINE_IN_MESSAGE.matcher(String.valueOf(e.getMessage()));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
