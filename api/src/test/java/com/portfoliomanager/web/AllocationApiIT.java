package com.portfoliomanager.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.PriceEntity;
import com.portfoliomanager.persistence.PriceRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AllocationApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private PriceRepository prices;

    private AccountEntity account;

    @BeforeEach
    void seedAccount() {
        account = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE));
    }

    private void holding(String symbol, AssetType type, String quantity, String price) {
        InstrumentEntity instrument =
                instruments.save(
                        new InstrumentEntity(symbol, symbol, type, PriceSource.YAHOO, symbol));
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        instrument.getId(),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal(quantity),
                        new BigDecimal("1"),
                        null));
        if (price != null) {
            PriceEntity row = new PriceEntity(instrument.getId());
            row.recordFeedPrice(new BigDecimal(price), null, LocalDate.now(), Instant.now());
            prices.save(row);
        }
    }

    private void targets(String json) throws Exception {
        mvc.perform(
                        put("/api/allocation/targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void reportsValueAndActualPercentPerAssetType() throws Exception {
        holding("VTI", AssetType.ETF, "10", "75");
        holding("BTC", AssetType.CRYPTO, "1", "250");

        mvc.perform(get("/api/allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value("1000.0000"))
                .andExpect(jsonPath("$.targetsSet").value(false))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[?(@.assetType=='ETF')].value").value("750.0000"))
                .andExpect(jsonPath("$.rows[?(@.assetType=='ETF')].actualPct").value("75.0000"))
                .andExpect(jsonPath("$.rows[?(@.assetType=='CRYPTO')].actualPct").value("25.0000"));
    }

    @Test
    void withTargetsRowsCarryTargetAndDrift() throws Exception {
        holding("VTI", AssetType.ETF, "10", "75");
        holding("BTC", AssetType.CRYPTO, "1", "250");
        targets(
                "[{\"assetType\":\"ETF\",\"targetPct\":\"60\"},{\"assetType\":\"CRYPTO\",\"targetPct\":\"40\"}]");

        mvc.perform(get("/api/allocation"))
                .andExpect(jsonPath("$.targetsSet").value(true))
                .andExpect(jsonPath("$.rows[?(@.assetType=='ETF')].targetPct").value("60.0000"))
                .andExpect(jsonPath("$.rows[?(@.assetType=='ETF')].driftPct").value("15.0000"))
                .andExpect(jsonPath("$.rows[?(@.assetType=='CRYPTO')].driftPct").value("-15.0000"));
    }

    @Test
    void withoutTargetsTargetAndDriftAreNull() throws Exception {
        holding("VTI", AssetType.ETF, "10", "75");

        mvc.perform(get("/api/allocation"))
                .andExpect(jsonPath("$.rows[0].targetPct").value(nullValue()))
                .andExpect(jsonPath("$.rows[0].driftPct").value(nullValue()));
    }

    @Test
    void unpricedPositionsAreExcludedFromValues() throws Exception {
        holding("VTI", AssetType.ETF, "10", "100");
        holding("NOPRICE", AssetType.CRYPTO, "5", null);

        mvc.perform(get("/api/allocation"))
                .andExpect(jsonPath("$.totalValue").value("1000.0000"))
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].assetType").value("ETF"))
                .andExpect(jsonPath("$.rows[0].actualPct").value("100.0000"));
    }

    @Test
    void zeroPortfolioValueGivesZeroPercentagesForTargetedTypes() throws Exception {
        targets("[{\"assetType\":\"ETF\",\"targetPct\":\"100\"}]");

        mvc.perform(get("/api/allocation"))
                .andExpect(jsonPath("$.totalValue").value("0.0000"))
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].actualPct").value("0.0000"))
                .andExpect(jsonPath("$.rows[0].driftPct").value("-100.0000"));
    }

    @Test
    void emptyPortfolioWithoutTargetsHasNoRows() throws Exception {
        mvc.perform(get("/api/allocation"))
                .andExpect(jsonPath("$.rows", hasSize(0)))
                .andExpect(jsonPath("$.totalValue").value("0.0000"));
    }

    @Test
    void theResponseNeverContainsTradeSuggestions() throws Exception {
        holding("VTI", AssetType.ETF, "10", "75");
        targets("[{\"assetType\":\"ETF\",\"targetPct\":\"100\"}]");

        String body =
                mvc.perform(get("/api/allocation")).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body.toLowerCase())
                .doesNotContain("buy")
                .doesNotContain("sell")
                .doesNotContain("rebalance");
    }
}
