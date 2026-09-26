package com.portfoliomanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.portfoliomanager.pricing.PriceSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("e2e")
class E2eStubIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;

    private String refreshAndWait() throws Exception {
        String body =
                mvc.perform(post("/api/refresh"))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String runId = JsonPath.read(body, "$.runId");
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String run =
                    mvc.perform(get("/api/refresh/" + runId))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            String state = JsonPath.read(run, "$.status");
            if (!state.equals("RUNNING")) {
                return state;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Refresh did not finish in time");
    }

    @Test
    void scriptedPricesFlowThroughRefreshIntoHoldings() throws Exception {
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

        mvc.perform(
                        put("/api/e2e/prices")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"yahoo":{"prices":{"VTI":{"price":"123.45","prevPrice":"120","priceDate":"%s"}}}}"""
                                                .formatted(LocalDate.now())))
                .andExpect(status().isNoContent());

        assertThat(refreshAndWait()).isEqualTo("SUCCEEDED");
        mvc.perform(get("/api/holdings")).andExpect(jsonPath("$[0].price").value("123.45000000"));

        mvc.perform(
                        put("/api/e2e/prices")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"yahoo\":{\"unreachable\":true}}"))
                .andExpect(status().isNoContent());

        assertThat(refreshAndWait()).isEqualTo("FAILED");
        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].price").value("123.45000000"))
                .andExpect(jsonPath("$[0].priceStatus").value("STALE"));
    }
}
