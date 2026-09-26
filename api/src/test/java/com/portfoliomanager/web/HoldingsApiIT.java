package com.portfoliomanager.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class HoldingsApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private PriceRepository prices;

    private AccountEntity brokerage;
    private AccountEntity retirement;

    @BeforeEach
    void seedAccounts() {
        brokerage = accounts.save(new AccountEntity("Brokerage", AccountType.BROKERAGE));
        retirement = accounts.save(new AccountEntity("Retirement", AccountType.RETIREMENT));
    }

    private InstrumentEntity instrument(String symbol, AssetType type, PriceSource source) {
        return instruments.save(new InstrumentEntity(symbol, symbol, type, source, symbol));
    }

    private void txn(
            AccountEntity account,
            InstrumentEntity instrument,
            TxnType type,
            String date,
            String qty,
            String price) {
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        instrument.getId(),
                        type,
                        LocalDate.parse(date),
                        new BigDecimal(qty),
                        new BigDecimal(price),
                        null));
    }

    private void split(
            AccountEntity account, InstrumentEntity instrument, String date, int n, int m) {
        transactions.saveAndFlush(
                TransactionEntity.split(
                        account.getId(), instrument.getId(), LocalDate.parse(date), n, m, null));
    }

    private void feedPrice(InstrumentEntity instrument, String price, String previous) {
        PriceEntity row = new PriceEntity(instrument.getId());
        row.recordFeedPrice(
                new BigDecimal(price),
                previous == null ? null : new BigDecimal(previous),
                LocalDate.now(),
                Instant.now());
        prices.save(row);
    }

    private void manualPrice(InstrumentEntity instrument, String price, LocalDate asOf) {
        PriceEntity row = new PriceEntity(instrument.getId());
        row.setManualPrice(new BigDecimal(price), asOf);
        prices.save(row);
    }

    @Test
    void cryptoEtfAndManualFundEachShowCorrectValueAndGain() throws Exception {
        InstrumentEntity btc = instrument("BTC", AssetType.CRYPTO, PriceSource.COINGECKO);
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        InstrumentEntity fund = instrument("VTSAX", AssetType.MUTUAL_FUND, PriceSource.MANUAL);
        txn(brokerage, btc, TxnType.BUY, "2026-01-05", "0.5", "40000");
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "10", "100");
        txn(retirement, fund, TxnType.BUY, "2026-01-05", "20", "50");
        feedPrice(btc, "60000", null);
        feedPrice(vti, "120", "118");
        manualPrice(fund, "55", LocalDate.now().minusDays(3));

        mvc.perform(get("/api/holdings").param("sort", "symbol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].assetType").value("CRYPTO"))
                .andExpect(jsonPath("$[0].quantity").value("0.50000000"))
                .andExpect(jsonPath("$[0].avgCost").value("40000.0000"))
                .andExpect(jsonPath("$[0].price").value("60000.00000000"))
                .andExpect(jsonPath("$[0].value").value("30000.0000"))
                .andExpect(jsonPath("$[0].unrealized").value("10000.0000"))
                .andExpect(jsonPath("$[0].unrealizedPct").value("50.0000"))
                .andExpect(jsonPath("$[0].priceStatus").value("OK"))
                .andExpect(jsonPath("$[1].symbol").value("VTI"))
                .andExpect(jsonPath("$[1].value").value("1200.0000"))
                .andExpect(jsonPath("$[1].unrealized").value("200.0000"))
                .andExpect(jsonPath("$[2].symbol").value("VTSAX"))
                .andExpect(jsonPath("$[2].value").value("1100.0000"))
                .andExpect(jsonPath("$[2].unrealized").value("100.0000"))
                .andExpect(jsonPath("$[2].unrealizedPct").value("10.0000"))
                .andExpect(jsonPath("$[2].priceStatus").value("MANUAL"))
                .andExpect(jsonPath("$[2].accountName").value("Retirement"));
    }

    @Test
    void averageCostIsDerivedFromRemainingLotsAfterAPartialSell() throws Exception {
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "10", "100");
        txn(brokerage, vti, TxnType.BUY, "2026-02-05", "10", "120");
        txn(brokerage, vti, TxnType.SELL, "2026-03-05", "15", "130");
        feedPrice(vti, "130", null);

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].quantity").value("5.00000000"))
                .andExpect(jsonPath("$[0].avgCost").value("120.0000"))
                .andExpect(jsonPath("$[0].costBasis").value("600.0000"));
    }

    @Test
    void splitAdjustsQuantityAndAverageCost() throws Exception {
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "10", "120");
        split(brokerage, vti, "2026-02-05", 2, 1);

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].quantity").value("20.00000000"))
                .andExpect(jsonPath("$[0].avgCost").value("60.0000"));
    }

    @Test
    void positionsWithZeroQuantityAreOmitted() throws Exception {
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "10", "100");
        txn(brokerage, vti, TxnType.SELL, "2026-01-06", "10", "100");

        mvc.perform(get("/api/holdings")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void instrumentWithoutAnyPriceIsUnpricedWithNullValue() throws Exception {
        InstrumentEntity fund = instrument("VTSAX", AssetType.MUTUAL_FUND, PriceSource.MANUAL);
        txn(retirement, fund, TxnType.BUY, "2026-01-05", "10", "100");

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].priceStatus").value("UNPRICED"))
                .andExpect(jsonPath("$[0].price").value(nullValue()))
                .andExpect(jsonPath("$[0].value").value(nullValue()))
                .andExpect(jsonPath("$[0].unrealized").value(nullValue()))
                .andExpect(jsonPath("$[0].costBasis").value("1000.0000"));
    }

    @Test
    void oldManualPriceIsManualStale() throws Exception {
        InstrumentEntity fund = instrument("VTSAX", AssetType.MUTUAL_FUND, PriceSource.MANUAL);
        txn(retirement, fund, TxnType.BUY, "2026-01-05", "10", "100");
        manualPrice(fund, "110", LocalDate.now().minusDays(60));

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].priceStatus").value("MANUAL_STALE"))
                .andExpect(
                        jsonPath("$[0].priceAsOf").value(LocalDate.now().minusDays(60).toString()))
                .andExpect(jsonPath("$[0].value").value("1100.0000"));
    }

    @Test
    void manualPriceOnAFeedInstrumentIsIgnoredForValuation() throws Exception {
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "10", "100");
        manualPrice(vti, "999", LocalDate.now());

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].priceStatus").value("UNPRICED"))
                .andExpect(jsonPath("$[0].value").value(nullValue()));
    }

    @Test
    void feedErrorKeepsTheLastGoodPriceButMarksItStale() throws Exception {
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "10", "100");
        PriceEntity row = new PriceEntity(vti.getId());
        row.recordFeedPrice(new BigDecimal("120"), null, LocalDate.now(), Instant.now());
        row.recordFeedError("timeout");
        prices.save(row);

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$[0].priceStatus").value("STALE"))
                .andExpect(jsonPath("$[0].value").value("1200.0000"));
    }

    @Test
    void filtersByAccountAndAssetType() throws Exception {
        InstrumentEntity btc = instrument("BTC", AssetType.CRYPTO, PriceSource.COINGECKO);
        InstrumentEntity vti = instrument("VTI", AssetType.ETF, PriceSource.YAHOO);
        txn(brokerage, btc, TxnType.BUY, "2026-01-05", "1", "100");
        txn(brokerage, vti, TxnType.BUY, "2026-01-05", "1", "100");
        txn(retirement, vti, TxnType.BUY, "2026-01-05", "1", "100");

        mvc.perform(get("/api/holdings").param("accountId", retirement.getId().toString()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("VTI"));
        mvc.perform(get("/api/holdings").param("assetType", "CRYPTO"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("BTC"));
        mvc.perform(get("/api/holdings").param("accountId", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sortsByAnyColumnAscendingOrDescendingWithMissingValuesLast() throws Exception {
        InstrumentEntity aaa = instrument("AAA", AssetType.STOCK, PriceSource.YAHOO);
        InstrumentEntity bbb = instrument("BBB", AssetType.STOCK, PriceSource.YAHOO);
        InstrumentEntity ccc = instrument("CCC", AssetType.STOCK, PriceSource.YAHOO);
        txn(brokerage, aaa, TxnType.BUY, "2026-01-05", "1", "100");
        txn(brokerage, bbb, TxnType.BUY, "2026-01-05", "1", "100");
        txn(brokerage, ccc, TxnType.BUY, "2026-01-05", "1", "100");
        feedPrice(aaa, "150", null);
        feedPrice(bbb, "90", null);

        mvc.perform(get("/api/holdings").param("sort", "value"))
                .andExpect(jsonPath("$[*].symbol", contains("BBB", "AAA", "CCC")));
        mvc.perform(get("/api/holdings").param("sort", "-value"))
                .andExpect(jsonPath("$[*].symbol", contains("AAA", "BBB", "CCC")));
        mvc.perform(get("/api/holdings").param("sort", "-unrealizedPct"))
                .andExpect(jsonPath("$[*].symbol", contains("AAA", "BBB", "CCC")));
        mvc.perform(get("/api/holdings").param("sort", "-symbol"))
                .andExpect(jsonPath("$[*].symbol", contains("CCC", "BBB", "AAA")));
    }

    @Test
    void unknownSortColumnReturns400() throws Exception {
        mvc.perform(get("/api/holdings").param("sort", "nonsense"))
                .andExpect(status().isBadRequest());
    }
}
