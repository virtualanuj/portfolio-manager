package com.portfoliomanager.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

class DashboardApiIT extends AbstractIntegrationTest {

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

    private InstrumentEntity held(String symbol, PriceSource source, String qty, String cost) {
        InstrumentEntity instrument =
                instruments.save(
                        new InstrumentEntity(symbol, symbol, AssetType.STOCK, source, symbol));
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        instrument.getId(),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal(qty),
                        new BigDecimal(cost),
                        null));
        return instrument;
    }

    private void feed(InstrumentEntity instrument, String price, String previous, LocalDate date) {
        PriceEntity row = new PriceEntity(instrument.getId());
        row.recordFeedPrice(
                new BigDecimal(price),
                previous == null ? null : new BigDecimal(previous),
                date,
                Instant.now());
        prices.save(row);
    }

    @Test
    void emptyDatabaseReturnsZerosWithoutError() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value("0.0000"))
                .andExpect(jsonPath("$.totalCostBasis").value("0.0000"))
                .andExpect(jsonPath("$.unrealized").value("0.0000"))
                .andExpect(jsonPath("$.unrealizedPct").value(nullValue()))
                .andExpect(jsonPath("$.dayChange").value(nullValue()))
                .andExpect(jsonPath("$.pricedPositions").value(0))
                .andExpect(jsonPath("$.stalePositions").value(0))
                .andExpect(jsonPath("$.unpricedPositions").value(0))
                .andExpect(jsonPath("$.lastRefresh").value(nullValue()));
    }

    @Test
    void totalsCoverPricedPositionsOnlyAndCountTheRest() throws Exception {
        InstrumentEntity priced = held("AAA", PriceSource.YAHOO, "10", "100");
        held("BBB", PriceSource.YAHOO, "5", "200");
        feed(priced, "120", null, LocalDate.now());

        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.totalValue").value("1200.0000"))
                .andExpect(jsonPath("$.totalCostBasis").value("1000.0000"))
                .andExpect(jsonPath("$.unrealized").value("200.0000"))
                .andExpect(jsonPath("$.unrealizedPct").value("20.0000"))
                .andExpect(jsonPath("$.pricedPositions").value(1))
                .andExpect(jsonPath("$.unpricedPositions").value(1))
                .andExpect(jsonPath("$.stalePositions").value(0));
    }

    @Test
    void stalePositionsAreIncludedAndCounted() throws Exception {
        InstrumentEntity old = held("AAA", PriceSource.YAHOO, "10", "100");
        feed(old, "120", null, LocalDate.now().minusDays(10));

        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.totalValue").value("1200.0000"))
                .andExpect(jsonPath("$.stalePositions").value(1))
                .andExpect(jsonPath("$.pricedPositions").value(1));
    }

    @Test
    void dayChangeAppearsOnlyWhenAPositionHasAPreviousPrice() throws Exception {
        InstrumentEntity noPrevious = held("AAA", PriceSource.YAHOO, "10", "100");
        feed(noPrevious, "120", null, LocalDate.now());
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.dayChange").value(nullValue()));

        InstrumentEntity withPrevious = held("BBB", PriceSource.YAHOO, "10", "100");
        feed(withPrevious, "110", "108", LocalDate.now());
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.dayChange").value("20.0000"));
    }
}
