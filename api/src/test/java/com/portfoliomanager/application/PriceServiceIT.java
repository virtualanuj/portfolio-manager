package com.portfoliomanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
import com.portfoliomanager.pricing.PriceFetchStatus;
import com.portfoliomanager.pricing.PriceResult;
import com.portfoliomanager.pricing.PriceSource;
import com.portfoliomanager.testsupport.FakePriceProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

class PriceServiceIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired private InstrumentRepository instruments;
    @Autowired private PriceRepository prices;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;

    private FakePriceProvider yahoo;
    private FakePriceProvider coingecko;
    private PriceService service;
    private InstrumentEntity vti;
    private InstrumentEntity btc;
    private InstrumentEntity fund;

    @BeforeEach
    void setUp() {
        yahoo = new FakePriceProvider(PriceSource.YAHOO);
        coingecko = new FakePriceProvider(PriceSource.COINGECKO);
        service =
                new PriceService(
                        List.of(yahoo, coingecko), instruments, prices, CLOCK, transactionManager);
        vti =
                instruments.save(
                        new InstrumentEntity(
                                "VTI", "VTI", AssetType.ETF, PriceSource.YAHOO, "VTI"));
        btc =
                instruments.save(
                        new InstrumentEntity(
                                "BTC", "BTC", AssetType.CRYPTO, PriceSource.COINGECKO, "bitcoin"));
        fund =
                instruments.save(
                        new InstrumentEntity(
                                "VTSAX", "VTSAX", AssetType.MUTUAL_FUND, PriceSource.MANUAL, null));
    }

    private static PriceResult ok(String price, String previous, String date) {
        return PriceResult.ok(
                new BigDecimal(price), new BigDecimal(previous), LocalDate.parse(date));
    }

    @Test
    void successfulFetchStoresPriceAndFetchTime() {
        yahoo.answers.put("VTI", ok("379.77", "378.08", "2026-09-25"));

        List<PriceRefreshOutcome> outcomes = service.refresh(List.of(vti.getId()));

        PriceEntity row = prices.findById(vti.getId()).orElseThrow();
        assertThat(row.getPrice()).isEqualByComparingTo("379.77");
        assertThat(row.getPrevPrice()).isEqualByComparingTo("378.08");
        assertThat(row.getPriceDate()).isEqualTo(LocalDate.parse("2026-09-25"));
        assertThat(row.getFetchedAt()).isEqualTo(NOW);
        assertThat(row.getStatus()).isEqualTo(PriceFetchStatus.OK);
        assertThat(outcomes)
                .singleElement()
                .satisfies(
                        o -> {
                            assertThat(o.symbol()).isEqualTo("VTI");
                            assertThat(o.ok()).isTrue();
                        });
    }

    @Test
    void failedFetchKeepsTheLastGoodValuesAndRecordsTheError() {
        yahoo.answers.put("VTI", ok("379.77", "378.08", "2026-09-25"));
        service.refresh(List.of(vti.getId()));
        yahoo.answers.put("VTI", PriceResult.failure("Yahoo returned HTTP 500"));

        List<PriceRefreshOutcome> outcomes = service.refresh(List.of(vti.getId()));

        PriceEntity row = prices.findById(vti.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PriceFetchStatus.ERROR);
        assertThat(row.getLastError()).isEqualTo("Yahoo returned HTTP 500");
        assertThat(row.getPrice()).isEqualByComparingTo("379.77");
        assertThat(row.getPrevPrice()).isEqualByComparingTo("378.08");
        assertThat(row.getPriceDate()).isEqualTo(LocalDate.parse("2026-09-25"));
        assertThat(row.getFetchedAt()).isEqualTo(NOW);
        assertThat(outcomes.get(0).ok()).isFalse();
        assertThat(outcomes.get(0).message()).isEqualTo("Yahoo returned HTTP 500");
    }

    @Test
    void failureOnAnInstrumentThatNeverHadAPriceStillCreatesTheErrorRow() {
        yahoo.answers.put("VTI", PriceResult.failure("No data found"));

        service.refresh(List.of(vti.getId()));

        PriceEntity row = prices.findById(vti.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PriceFetchStatus.ERROR);
        assertThat(row.getPrice()).isNull();
    }

    @Test
    void laterSuccessOverwritesAndClearsTheError() {
        yahoo.answers.put("VTI", PriceResult.failure("timeout"));
        service.refresh(List.of(vti.getId()));
        yahoo.answers.put("VTI", ok("380", "379", "2026-09-26"));

        service.refresh(List.of(vti.getId()));

        PriceEntity row = prices.findById(vti.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PriceFetchStatus.OK);
        assertThat(row.getLastError()).isNull();
        assertThat(row.getPrice()).isEqualByComparingTo("380");
    }

    @Test
    void manualInstrumentsAreNeverSentToAProvider() {
        List<PriceRefreshOutcome> outcomes = service.refresh(List.of(fund.getId()));

        assertThat(outcomes).isEmpty();
        assertThat(yahoo.calls).isEmpty();
        assertThat(coingecko.calls).isEmpty();
        assertThat(prices.findById(fund.getId())).isEmpty();
    }

    @Test
    void instrumentsGoToTheirOwnProviderUsingTheirSourceId() {
        yahoo.answers.put("VTI", ok("380", "379", "2026-09-26"));
        coingecko.answers.put("bitcoin", ok("84122", "84686", "2026-09-26"));

        service.refresh(List.of(vti.getId(), btc.getId(), fund.getId()));

        assertThat(yahoo.calls).containsExactly(List.of("VTI"));
        assertThat(coingecko.calls).containsExactly(List.of("bitcoin"));
    }

    @Test
    void aProviderThatOmitsAnInstrumentCountsAsAFailureForIt() {
        List<PriceRefreshOutcome> outcomes = service.refresh(List.of(vti.getId()));

        assertThat(outcomes).singleElement().satisfies(o -> assertThat(o.ok()).isFalse());
        assertThat(prices.findById(vti.getId()).orElseThrow().getStatus())
                .isEqualTo(PriceFetchStatus.ERROR);
    }

    @Test
    void oneFailureDoesNotStopTheOthersFromBeingStored() {
        yahoo.answers.put("VTI", PriceResult.failure("boom"));
        coingecko.answers.put("bitcoin", ok("84122", "84686", "2026-09-26"));

        List<PriceRefreshOutcome> outcomes = service.refresh(List.of(vti.getId(), btc.getId()));

        assertThat(outcomes).hasSize(2);
        assertThat(prices.findById(btc.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo("84122");
    }

    @Test
    void afterAFailedFetchHoldingsShowTheLastPriceAsStale() throws Exception {
        AccountEntity account = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE));
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        vti.getId(),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal("10"),
                        new BigDecimal("100"),
                        null));
        PriceEntity row = new PriceEntity(vti.getId());
        row.recordFeedPrice(new BigDecimal("120"), null, LocalDate.now(), Instant.now());
        prices.save(row);
        yahoo.answers.put("VTI", PriceResult.failure("Yahoo returned HTTP 500"));

        service.refresh(List.of(vti.getId()));

        mvc.perform(get("/api/holdings"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].priceStatus").value("STALE"))
                .andExpect(jsonPath("$[0].value").value("1200.0000"));
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.stalePositions").value(1));
    }
}
