package com.portfoliomanager.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceResult;
import com.portfoliomanager.pricing.PriceSource;
import com.portfoliomanager.testsupport.FakePriceProvider;
import com.portfoliomanager.testsupport.RefreshTestConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(RefreshTestConfig.class)
class RefreshApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private RefreshTestConfig.ManualRunner runner;
    @Autowired private FakePriceProvider fakeYahoo;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;

    @BeforeEach
    void seed() {
        runner.tasks.clear();
        fakeYahoo.answers.clear();
        fakeYahoo.calls.clear();
        AccountEntity account = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE));
        InstrumentEntity vti =
                instruments.save(
                        new InstrumentEntity(
                                "VTI", "VTI", AssetType.ETF, PriceSource.YAHOO, "VTI"));
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        vti.getId(),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal("10"),
                        new BigDecimal("100"),
                        null));
        fakeYahoo.answers.put(
                "VTI",
                PriceResult.ok(
                        new BigDecimal("120"),
                        new BigDecimal("118"),
                        LocalDate.parse("2026-09-25")));
    }

    private String startRun() throws Exception {
        MvcResult result =
                mvc.perform(post("/api/refresh"))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.runId").isNotEmpty())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.runId");
    }

    @Test
    void postReturns202WithTheRunIdAndTheRunIsRunningUntilTheWorkerFinishes() throws Exception {
        String runId = startRun();

        mvc.perform(get("/api/refresh/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.finishedAt").value(nullValue()))
                .andExpect(jsonPath("$.results", hasSize(0)));

        runner.runAll();

        mvc.perform(get("/api/refresh/" + runId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].symbol").value("VTI"))
                .andExpect(jsonPath("$.results[0].ok").value(true));
    }

    @Test
    void aSecondPostWhileRunningReturns409WithTheRunningRunId() throws Exception {
        String runId = startRun();

        mvc.perform(post("/api/refresh"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.detail").value("A refresh is already running"));
    }

    @Test
    void failedInstrumentsAreListedWithTheirMessages() throws Exception {
        fakeYahoo.answers.put("VTI", PriceResult.failure("Yahoo returned HTTP 500"));
        String runId = startRun();
        runner.runAll();

        mvc.perform(get("/api/refresh/" + runId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].ok").value(false))
                .andExpect(jsonPath("$.results[0].message").value("Yahoo returned HTTP 500"));
    }

    @Test
    void latestReturnsTheMostRecentRun() throws Exception {
        startRun();
        runner.runAll();
        // The fixed clock gives both runs the same start time, so make the first one older.
        jdbc.update("UPDATE refresh_run SET started_at = started_at - interval '1 hour'");
        String second = startRun();

        mvc.perform(get("/api/refresh/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void latestBeforeAnyRunReturns404() throws Exception {
        mvc.perform(get("/api/refresh/latest")).andExpect(status().isNotFound());
    }

    @Test
    void e2eControlEndpointsDoNotExistWithoutTheE2eProfile() throws Exception {
        mvc.perform(post("/api/e2e/reset")).andExpect(status().isNotFound());
    }

    @Test
    void unknownRunReturns404() throws Exception {
        mvc.perform(get("/api/refresh/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void dashboardShowsTheLastRefresh() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.lastRefresh").value(nullValue()));

        String runId = startRun();
        runner.runAll();

        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.lastRefresh.runId").value(runId))
                .andExpect(jsonPath("$.lastRefresh.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.lastRefresh.finishedAt").isNotEmpty());
    }
}
