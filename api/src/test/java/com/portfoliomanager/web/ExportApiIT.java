package com.portfoliomanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.portfoliomanager.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class ExportApiIT extends AbstractIntegrationTest {

    private static final String SCHEMA_HEADER =
            "date,account,symbol,asset_type,type,quantity,price,split_ratio,source_id,account_type,note";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private static byte[] sample() throws IOException {
        try (var in = ExportApiIT.class.getResourceAsStream("/csv/sample.csv")) {
            return in.readAllBytes();
        }
    }

    private void importCsv(byte[] content) throws Exception {
        String body =
                mvc.perform(
                                multipart("/api/imports")
                                        .file(
                                                new MockMultipartFile(
                                                        "file", "x.csv", "text/csv", content)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/imports/" + id + "/commit")).andExpect(status().isOk());
    }

    private String exportTransactions() throws Exception {
        return mvc.perform(get("/api/export/transactions.csv"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** Holdings without the generated ids, which legitimately differ after a re-import. */
    private List<Object> holdings() throws Exception {
        String json =
                mvc.perform(get("/api/holdings").param("sort", "symbol"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(
                json, "$[*].['accountName','symbol','assetType','quantity','avgCost','costBasis']");
    }

    @Test
    void transactionsCsvUsesExactlyTheImportSchemaWithDownloadHeaders() throws Exception {
        importCsv(sample());

        mvc.perform(get("/api/export/transactions.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        startsWith("attachment; filename=\"transactions-")));
        String csv = exportTransactions();
        assertThat(csv.lines().findFirst()).contains(SCHEMA_HEADER);
        assertThat(csv.lines().count()).isEqualTo(7);
        assertThat(csv).contains("2026-04-05,Brokerage,VTI,ETF,SPLIT,,,2:1,VTI,BROKERAGE,");
        assertThat(csv)
                .contains(
                        "2026-01-07,Coinbase,BTC,CRYPTO,BUY,0.50000000,40000.00000000,,bitcoin,CRYPTO,");
    }

    @Test
    void roundTripExportClearImportGivesIdenticalHoldings() throws Exception {
        importCsv(sample());
        List<Object> before = holdings();
        String exported = exportTransactions();

        jdbc.execute(
                "TRUNCATE snapshot_holding, snapshot, \"transaction\", import_row, import_batch, price, instrument, account CASCADE");
        importCsv(exported.getBytes(StandardCharsets.UTF_8));

        assertThat(holdings()).isNotEmpty().isEqualTo(before);
    }

    @Test
    void textFieldsThatStartLikeFormulasAreNeutralised() throws Exception {
        importCsv(
                ("date,account,symbol,asset_type,type,quantity,price,note\n"
                                + "2026-01-05,Main,VTI,ETF,BUY,1,100,=HYPERLINK(\"http://evil\")\n")
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(exportTransactions()).contains("'=HYPERLINK(");
    }

    @Test
    void emptyDatabaseExportsHeadersOnly() throws Exception {
        assertThat(exportTransactions().strip()).isEqualTo(SCHEMA_HEADER);
        mvc.perform(get("/api/export/snapshots.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")));
        assertThat(
                        mvc.perform(get("/api/export/snapshots.csv"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString()
                                .strip())
                .isEqualTo("date,total_value,total_cost_basis");
        mvc.perform(get("/api/export/all.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(0)))
                .andExpect(jsonPath("$.transactions", hasSize(0)));
    }

    @Test
    void snapshotsCsvHasDateAndTotals() throws Exception {
        jdbc.update(
                "INSERT INTO snapshot (snap_date, total_value, total_cost_basis) VALUES ('2026-02-01', 750.25, 700)");
        jdbc.update(
                "INSERT INTO snapshot (snap_date, total_value, total_cost_basis) VALUES ('2026-01-01', 500, 500)");

        String csv =
                mvc.perform(get("/api/export/snapshots.csv"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(csv.lines().toList())
                .containsExactly(
                        "date,total_value,total_cost_basis",
                        "2026-01-01,500.0000,500.0000",
                        "2026-02-01,750.2500,700.0000");
    }

    @Test
    void allJsonHoldsEverythingWithDecimalsAsStrings() throws Exception {
        importCsv(sample());
        jdbc.update(
                "INSERT INTO snapshot (snap_date, total_value, total_cost_basis) VALUES ('2026-02-01', 750.25, 700)");
        jdbc.update("INSERT INTO target_allocation (asset_type, target_pct) VALUES ('ETF', 100)");
        jdbc.update(
                "INSERT INTO price (instrument_id, manual_price, manual_as_of) SELECT id, 55.5, DATE '2026-09-01' FROM instrument WHERE symbol = 'VTSAX'");

        mvc.perform(get("/api/export/all.json"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        startsWith("attachment; filename=\"portfolio-")))
                .andExpect(jsonPath("$.accounts", hasSize(3)))
                .andExpect(jsonPath("$.instruments", hasSize(3)))
                .andExpect(jsonPath("$.transactions", hasSize(6)))
                .andExpect(jsonPath("$.transactions[0].quantity").value("20.00000000"))
                .andExpect(jsonPath("$.manualPrices", hasSize(1)))
                .andExpect(jsonPath("$.manualPrices[0].symbol").value("VTSAX"))
                .andExpect(jsonPath("$.manualPrices[0].price").value("55.50000000"))
                .andExpect(jsonPath("$.targets[0].assetType").value("ETF"))
                .andExpect(jsonPath("$.targets[0].targetPct").value("100.00"))
                .andExpect(jsonPath("$.snapshots[0].totalValue").value("750.2500"));
    }
}
