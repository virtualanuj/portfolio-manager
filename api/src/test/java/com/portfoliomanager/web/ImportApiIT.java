package com.portfoliomanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ImportApiIT extends AbstractIntegrationTest {

    private static final String HEADER =
            "date,account,symbol,asset_type,type,quantity,price,split_ratio,source_id,account_type,note\n";

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @MockitoSpyBean private TransactionRepository transactions;
    @Autowired private JdbcTemplate jdbc;

    private static byte[] sample() throws IOException {
        try (var in = ImportApiIT.class.getResourceAsStream("/csv/sample.csv")) {
            return in.readAllBytes();
        }
    }

    private MvcResult upload(byte[] content) throws Exception {
        return mvc.perform(
                        multipart("/api/imports")
                                .file(
                                        new MockMultipartFile(
                                                "file", "broker.csv", "text/csv", content)))
                .andReturn();
    }

    private String stage(byte[] content) throws Exception {
        MvcResult result = upload(content);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static byte[] csv(String... rows) {
        return (HEADER + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void uploadReturnsAPreviewWithRowStatusesAndASummaryAndWritesNothing() throws Exception {
        mvc.perform(
                        multipart("/api/imports")
                                .file(
                                        new MockMultipartFile(
                                                "file", "broker.csv", "text/csv", sample())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("broker.csv"))
                .andExpect(jsonPath("$.status").value("STAGED"))
                .andExpect(jsonPath("$.summary.totalRows").value(6))
                .andExpect(jsonPath("$.summary.errors").value(0))
                .andExpect(jsonPath("$.summary.newAccounts").value(3))
                .andExpect(jsonPath("$.summary.newInstruments").value(3))
                .andExpect(jsonPath("$.rows", hasSize(6)))
                .andExpect(jsonPath("$.rows[0].lineNo").value(2))
                .andExpect(jsonPath("$.rows[0].status").value("WILL_CREATE"))
                .andExpect(jsonPath("$.rows[0].values.symbol").value("VTSAX"));
        assertThat(accounts.count()).isZero();
        assertThat(instruments.count()).isZero();
        assertThat(transactions.count()).isZero();
    }

    @Test
    void getReturnsTheStagedPreviewAgain() throws Exception {
        String id = stage(sample());

        mvc.perform(get("/api/imports/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.rows", hasSize(6)))
                .andExpect(jsonPath("$.summary.totalRows").value(6));
    }

    @Test
    void errorRowsAreReportedWithTheirMessagesAndBlockCommit() throws Exception {
        String id = stage(csv("2026-01-05,Main,VTI,ETF,SELL,5,100,,,,"));

        mvc.perform(get("/api/imports/" + id))
                .andExpect(jsonPath("$.summary.errors").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("ERROR"))
                .andExpect(
                        jsonPath("$.rows[0].errors[0].message")
                                .value(org.hamcrest.Matchers.containsString("exceeds")));
        mvc.perform(post("/api/imports/" + id + "/commit"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(transactions.count()).isZero();
    }

    @Test
    void commitCreatesAccountsInstrumentsAndTransactionsAndHoldingsMatchTheHandCalculation()
            throws Exception {
        String id = stage(sample());

        mvc.perform(post("/api/imports/" + id + "/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated").value(6))
                .andExpect(jsonPath("$.accountsCreated").value(3))
                .andExpect(jsonPath("$.instrumentsCreated").value(3));

        assertThat(accounts.count()).isEqualTo(3);
        assertThat(instruments.count()).isEqualTo(3);
        mvc.perform(get("/api/holdings").param("sort", "symbol"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].costBasis").value("20000.0000"))
                .andExpect(jsonPath("$[1].symbol").value("VTI"))
                .andExpect(jsonPath("$[1].quantity").value("10.00000000"))
                .andExpect(jsonPath("$[1].costBasis").value("600.0000"))
                .andExpect(jsonPath("$[2].symbol").value("VTSAX"))
                .andExpect(jsonPath("$[2].costBasis").value("1000.0000"));
        mvc.perform(get("/api/instruments"))
                .andExpect(jsonPath("$[?(@.symbol=='BTC')].priceSource").value("COINGECKO"))
                .andExpect(jsonPath("$[?(@.symbol=='BTC')].sourceId").value("bitcoin"))
                .andExpect(jsonPath("$[?(@.symbol=='VTI')].sourceId").value("VTI"));
        mvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[?(@.name=='Fidelity 401k')].type").value("RETIREMENT"));
    }

    @Test
    void commitPreservesFileOrderInSeq() throws Exception {
        String id = stage(sample());
        mvc.perform(post("/api/imports/" + id + "/commit")).andExpect(status().isOk());

        List<String> notesInSeqOrder =
                jdbc.queryForList(
                        "SELECT coalesce(note, '') FROM \"transaction\" ORDER BY seq",
                        String.class);

        assertThat(notesInSeqOrder).containsExactly("first buy", "", "", "partial sell", "", "");
    }

    @Test
    void commitIsAllOrNothing() throws Exception {
        String id = stage(sample());
        int[] calls = {0};
        Mockito.doAnswer(
                        invocation -> {
                            if (++calls[0] == 6) {
                                throw new IllegalStateException(
                                        "simulated failure on the last row");
                            }
                            return invocation.callRealMethod();
                        })
                .when(transactions)
                .saveAndFlush(Mockito.any(TransactionEntity.class));

        mvc.perform(post("/api/imports/" + id + "/commit"))
                .andExpect(status().isInternalServerError());

        Mockito.reset(transactions);
        assertThat(transactions.count()).isZero();
        assertThat(accounts.count()).isZero();
        assertThat(instruments.count()).isZero();
        mvc.perform(get("/api/imports/" + id)).andExpect(jsonPath("$.status").value("STAGED"));
    }

    @Test
    void flaggedDuplicatesAreSkippedUnlessIncluded() throws Exception {
        String row = "2026-01-05,Main,VTI,ETF,BUY,10,100,,,BROKERAGE,";
        String first = stage(csv(row));
        mvc.perform(post("/api/imports/" + first + "/commit")).andExpect(status().isOk());

        String again = stage(csv(row));
        mvc.perform(get("/api/imports/" + again))
                .andExpect(jsonPath("$.rows[0].status").value("WARNING"))
                .andExpect(jsonPath("$.summary.duplicates").value(1));
        mvc.perform(post("/api/imports/" + again + "/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated").value(0))
                .andExpect(jsonPath("$.duplicatesSkipped").value(1));
        assertThat(transactions.count()).isEqualTo(1);

        String third = stage(csv(row));
        mvc.perform(post("/api/imports/" + third + "/commit").param("includeDuplicates", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated").value(1));
        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test
    void committingTwiceReturns409() throws Exception {
        String id = stage(sample());
        mvc.perform(post("/api/imports/" + id + "/commit")).andExpect(status().isOk());

        mvc.perform(post("/api/imports/" + id + "/commit")).andExpect(status().isConflict());
        assertThat(transactions.count()).isEqualTo(6);
    }

    @Test
    void deleteDiscardsAStagedBatch() throws Exception {
        String id = stage(sample());

        mvc.perform(delete("/api/imports/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/imports/" + id)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_row", Long.class)).isZero();
    }

    @Test
    void batchesOlderThan24HoursAreDiscardedWhenANewImportStarts() throws Exception {
        String old = stage(sample());
        jdbc.update("UPDATE import_batch SET created_at = now() - interval '25 hours'");

        stage(csv("2026-01-05,Main,VTI,ETF,BUY,10,100,,,,"));

        mvc.perform(get("/api/imports/" + old)).andExpect(status().isNotFound());
    }

    @Test
    void unreadableFilesReturn400WithTheReason() throws Exception {
        MvcResult result = upload((HEADER).getBytes(StandardCharsets.UTF_8));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("no rows");
    }

    @Test
    void unknownBatchReturns404() throws Exception {
        mvc.perform(get("/api/imports/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/imports/" + java.util.UUID.randomUUID() + "/commit"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/imports/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void filesOverTwoMegabytesReturn413() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 10];
        java.util.Arrays.fill(big, (byte) 'a');

        MvcResult result = upload(big);

        assertThat(result.getResponse().getStatus()).isEqualTo(413);
    }
}
