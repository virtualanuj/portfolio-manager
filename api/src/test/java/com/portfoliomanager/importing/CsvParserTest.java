package com.portfoliomanager.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    private static final String HEADER = "date,account,symbol,asset_type,type,quantity,price";

    private static CsvDocument parse(String text) {
        return CsvParser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static CsvDocument parse(byte[] bytes) {
        return CsvParser.parse(new ByteArrayInputStream(bytes));
    }

    @Test
    void stripsAUtf8ByteOrderMark() {
        byte[] body =
                (HEADER + "\n2026-01-05,Main,VTI,ETF,BUY,1,100\n").getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        CsvDocument document = parse(withBom);

        assertThat(document.headers()).startsWith("date");
        assertThat(document.rows()).hasSize(1);
        assertThat(document.rows().get(0).get("date")).isEqualTo("2026-01-05");
    }

    @Test
    void readsBothCrlfAndLfLineEndings() {
        String rows = "2026-01-05,Main,VTI,ETF,BUY,1,100";

        assertThat(parse(HEADER + "\r\n" + rows + "\r\n" + rows + "\r\n").rows()).hasSize(2);
        assertThat(parse(HEADER + "\n" + rows + "\n" + rows + "\n").rows()).hasSize(2);
        assertThat(parse(HEADER + "\n" + rows).rows()).hasSize(1);
    }

    @Test
    void keepsQuotedCommasAndQuotedNewlines() {
        String text =
                "date,account,symbol,asset_type,type,quantity,price,note\n"
                        + "2026-01-05,\"Main, joint\",VTI,ETF,BUY,1,100,\"line one\nline two, with comma\"\n";

        CsvRow row = parse(text).rows().get(0);

        assertThat(row.get("account")).isEqualTo("Main, joint");
        assertThat(row.get("note")).isEqualTo("line one\nline two, with comma");
    }

    @Test
    void headerNamesAreTrimmedAndCaseInsensitive() {
        String text =
                " Date , ACCOUNT,Symbol ,Asset_Type,TYPE, Quantity,PRICE\n2026-01-05, Main ,VTI,ETF,BUY, 1 ,100\n";

        CsvRow row = parse(text).rows().get(0);

        assertThat(row.get("account")).isEqualTo("Main");
        assertThat(row.get("quantity")).isEqualTo("1");
        assertThat(row.get("asset_type")).isEqualTo("ETF");
    }

    @Test
    void unknownColumnsAreIgnoredWithAWarning() {
        String text = HEADER + ",broker_ref\n2026-01-05,Main,VTI,ETF,BUY,1,100,XYZ-1\n";

        CsvDocument document = parse(text);

        assertThat(document.warnings()).singleElement().asString().contains("broker_ref");
        assertThat(document.rows().get(0).get("broker_ref")).isEmpty();
    }

    @Test
    void absentOptionalColumnsReadAsEmpty() {
        CsvRow row = parse(HEADER + "\n2026-01-05,Main,VTI,ETF,BUY,1,100\n").rows().get(0);

        assertThat(row.get("note")).isEmpty();
        assertThat(row.get("split_ratio")).isEmpty();
    }

    @Test
    void blankLinesAreSkippedAndRowsKeepTheirFileLineNumber() {
        String text =
                HEADER
                        + "\n\n2026-01-05,Main,VTI,ETF,BUY,1,100\n\n2026-01-06,Main,VTI,ETF,BUY,2,101\n";

        CsvDocument document = parse(text);

        assertThat(document.rows()).extracting(CsvRow::lineNo).containsExactly(3, 5);
    }

    @Test
    void rejectsAFileOverTwoMegabytes() {
        byte[] tooBig = new byte[CsvLimits.MAX_BYTES + 1];
        java.util.Arrays.fill(tooBig, (byte) 'a');

        assertThatThrownBy(() -> parse(tooBig))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void acceptsExactlyFiveThousandRowsAndRejectsMore() {
        StringBuilder ok = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < CsvLimits.MAX_ROWS; i++) {
            ok.append("2026-01-05,Main,VTI,ETF,BUY,1,100\n");
        }
        assertThat(parse(ok.toString()).rows()).hasSize(CsvLimits.MAX_ROWS);

        String tooMany = ok + "2026-01-05,Main,VTI,ETF,BUY,1,100\n";
        assertThatThrownBy(() -> parse(tooMany))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("5,000");
    }

    @Test
    void emptyFileHasAClearError() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> parse("\n\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void headerOnlyFileHasAClearError() {
        assertThatThrownBy(() -> parse(HEADER + "\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("no rows");
    }

    @Test
    void missingRequiredColumnsAreNamed() {
        assertThatThrownBy(() -> parse("date,account,symbol\n2026-01-05,Main,VTI\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("asset_type")
                .hasMessageContaining("type");
    }

    @Test
    void malformedQuotingReportsTheLine() {
        String text =
                HEADER
                        + "\n2026-01-05,\"Main,VTI,ETF,BUY,1,100\n2026-01-06,Main,VTI,ETF,BUY,1,100\n";

        assertThatThrownBy(() -> parse(text))
                .isInstanceOfSatisfying(
                        CsvFormatException.class, e -> assertThat(e.getLineNumber()).isPositive());
    }

    @Test
    void readingIsBoundedNotWholeFileForRowLimit() throws IOException {
        // A stream that would be huge if fully read: the parser must stop once past the row cap.
        java.io.InputStream endless =
                new java.io.InputStream() {
                    private final byte[] header = (HEADER + "\n").getBytes(StandardCharsets.UTF_8);
                    private final byte[] row =
                            "2026-01-05,Main,VTI,ETF,BUY,1,100\n".getBytes(StandardCharsets.UTF_8);
                    private long position;

                    @Override
                    public int read() {
                        long p = position++;
                        if (p < header.length) {
                            return header[(int) p];
                        }
                        return row[(int) ((p - header.length) % row.length)];
                    }
                };

        assertThatThrownBy(() -> CsvParser.parse(endless)).isInstanceOf(CsvFormatException.class);
    }
}
