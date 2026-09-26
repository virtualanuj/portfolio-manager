package com.portfoliomanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.RefreshStatus;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.RefreshRunEntity;
import com.portfoliomanager.persistence.RefreshRunRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceResult;
import com.portfoliomanager.pricing.PriceSource;
import com.portfoliomanager.testsupport.FakePriceProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Import(RefreshServiceIT.TestBeans.class)
class RefreshServiceIT extends AbstractIntegrationTest {

    static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    /** Collects refresh tasks instead of running them, so a test decides when the worker runs. */
    static final class ManualRunner implements RefreshRunner {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public synchronized void run(Runnable task) {
            tasks.add(task);
        }

        synchronized void runAll() {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ManualRunner manualRunner() {
            return new ManualRunner();
        }

        @Bean
        FakePriceProvider fakeYahoo() {
            return new FakePriceProvider(PriceSource.YAHOO);
        }

        @Bean
        FakePriceProvider fakeCoinGecko() {
            return new FakePriceProvider(PriceSource.COINGECKO);
        }

        @Bean
        @Primary
        PriceService fakePriceService(
                FakePriceProvider fakeYahoo,
                FakePriceProvider fakeCoinGecko,
                InstrumentRepository instruments,
                com.portfoliomanager.persistence.PriceRepository prices,
                Clock clock,
                PlatformTransactionManager transactionManager) {
            return new PriceService(
                    List.of(fakeYahoo, fakeCoinGecko),
                    instruments,
                    prices,
                    clock,
                    transactionManager);
        }

        @Bean
        @Primary
        RefreshRunner primaryRunner(ManualRunner manualRunner) {
            return manualRunner;
        }
    }

    @Autowired private RefreshService service;
    @Autowired private ManualRunner runner;
    @Autowired private FakePriceProvider fakeYahoo;
    @Autowired private FakePriceProvider fakeCoinGecko;
    @Autowired private RefreshRunRepository runs;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private AccountEntity account;

    @BeforeEach
    void reset() {
        runner.tasks.clear();
        fakeYahoo.answers.clear();
        fakeYahoo.calls.clear();
        fakeYahoo.failure = null;
        fakeCoinGecko.answers.clear();
        fakeCoinGecko.calls.clear();
        account = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE));
    }

    private InstrumentEntity held(
            String symbol, AssetType type, PriceSource source, String sourceId) {
        InstrumentEntity instrument =
                instruments.save(new InstrumentEntity(symbol, symbol, type, source, sourceId));
        trade(instrument, TxnType.BUY, "10");
        return instrument;
    }

    private void trade(InstrumentEntity instrument, TxnType type, String quantity) {
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        instrument.getId(),
                        type,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal(quantity),
                        new BigDecimal("100"),
                        null));
    }

    private static PriceResult ok(String price) {
        return PriceResult.ok(new BigDecimal(price), null, LocalDate.parse("2026-09-25"));
    }

    @Test
    void startingARunInsertsARunningRow() {
        UUID id = service.start();

        RefreshRunEntity run = runs.findById(id).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RefreshStatus.RUNNING);
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(runner.tasks).hasSize(1);
    }

    @Test
    void aSecondStartWhileRunningIsAConflictCarryingTheExistingRunId() {
        UUID first = service.start();

        assertThatThrownBy(() -> service.start())
                .isInstanceOfSatisfying(
                        RefreshInProgressException.class,
                        e -> assertThat(e.getRunId()).isEqualTo(first));
        assertThat(runner.tasks).hasSize(1);
    }

    @Test
    void twoConcurrentStartsLetExactlyOneWin() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Object> attempt =
                () -> {
                    barrier.await();
                    try {
                        return service.start();
                    } catch (RefreshInProgressException e) {
                        return e;
                    }
                };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> a = pool.submit(attempt);
            Future<Object> b = pool.submit(attempt);
            List<Object> outcomes = List.of(get(a), get(b));

            assertThat(outcomes.stream().filter(UUID.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(RefreshInProgressException.class::isInstance))
                    .hasSize(1);
        }
        assertThat(runs.count()).isEqualTo(1);
    }

    private static Object get(Future<Object> future)
            throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Test
    void aRunningRowOlderThanFiveMinutesIsFailedAndANewRunMayStart() {
        RefreshRunEntity stale =
                runs.saveAndFlush(RefreshRunEntity.started(NOW.minusSeconds(6 * 60)));

        UUID fresh = service.start();

        assertThat(fresh).isNotEqualTo(stale.getId());
        RefreshRunEntity abandoned = runs.findById(stale.getId()).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(RefreshStatus.FAILED);
        assertThat(abandoned.getFinishedAt()).isEqualTo(NOW);
        assertThat(runs.findById(fresh).orElseThrow().getStatus()).isEqualTo(RefreshStatus.RUNNING);
    }

    @Test
    void aRunningRowYoungerThanFiveMinutesStillBlocks() {
        runs.saveAndFlush(RefreshRunEntity.started(NOW.minusSeconds(4 * 60)));

        assertThatThrownBy(() -> service.start()).isInstanceOf(RefreshInProgressException.class);
    }

    @Test
    void onlyInstrumentsWithAnOpenPositionAreFetched() {
        held("OPEN", AssetType.STOCK, PriceSource.YAHOO, "OPEN");
        InstrumentEntity sold = held("SOLD", AssetType.STOCK, PriceSource.YAHOO, "SOLD");
        trade(sold, TxnType.SELL, "10");
        instruments.save(
                new InstrumentEntity("IDLE", "IDLE", AssetType.STOCK, PriceSource.YAHOO, "IDLE"));
        fakeYahoo.answers.put("OPEN", ok("120"));

        service.start();
        runner.runAll();

        assertThat(fakeYahoo.calls).containsExactly(List.of("OPEN"));
    }

    @Test
    void allPricesFetchedFinishesSucceededWithPerInstrumentResults() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        held("BTC", AssetType.CRYPTO, PriceSource.COINGECKO, "bitcoin");
        fakeYahoo.answers.put("VTI", ok("120"));
        fakeCoinGecko.answers.put("bitcoin", ok("84000"));

        UUID id = service.start();
        runner.runAll();

        RefreshRunView run = service.find(id);
        assertThat(run.status()).isEqualTo(RefreshStatus.SUCCEEDED);
        assertThat(run.finishedAt()).isEqualTo(NOW);
        assertThat(run.results())
                .extracting(RefreshResultItem::symbol, RefreshResultItem::ok)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("VTI", true),
                        org.assertj.core.groups.Tuple.tuple("BTC", true));
    }

    @Test
    void someFailuresFinishPartialAndReportTheMessages() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        held("BTC", AssetType.CRYPTO, PriceSource.COINGECKO, "bitcoin");
        fakeYahoo.answers.put("VTI", ok("120"));
        fakeCoinGecko.answers.put("bitcoin", PriceResult.failure("CoinGecko returned HTTP 429"));

        UUID id = service.start();
        runner.runAll();

        RefreshRunView run = service.find(id);
        assertThat(run.status()).isEqualTo(RefreshStatus.PARTIAL);
        assertThat(run.results())
                .filteredOn(r -> !r.ok())
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.symbol()).isEqualTo("BTC");
                            assertThat(r.message()).isEqualTo("CoinGecko returned HTTP 429");
                        });
    }

    @Test
    void everyFetchFailingFinishesFailed() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        fakeYahoo.answers.put("VTI", PriceResult.failure("Yahoo returned HTTP 500"));

        UUID id = service.start();
        runner.runAll();

        assertThat(service.find(id).status()).isEqualTo(RefreshStatus.FAILED);
    }

    @Test
    void onlyManualPositionsNeedNoFetchAndSucceed() {
        held("VTSAX", AssetType.MUTUAL_FUND, PriceSource.MANUAL, null);

        UUID id = service.start();
        runner.runAll();

        assertThat(service.find(id).status()).isEqualTo(RefreshStatus.SUCCEEDED);
        assertThat(fakeYahoo.calls).isEmpty();
    }

    @Test
    void aProviderBugFinishesTheRunFailedInsteadOfLeavingItRunning() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        fakeYahoo.failure = new IllegalStateException("provider exploded");

        UUID id = service.start();
        runner.runAll();

        RefreshRunView run = service.find(id);
        assertThat(run.status()).isEqualTo(RefreshStatus.FAILED);
        assertThat(run.results()).singleElement().satisfies(r -> assertThat(r.ok()).isFalse());
    }

    @Test
    void afterARunFinishesANewOneMayStart() {
        service.start();
        runner.runAll();

        assertThat(service.start()).isNotNull();
    }

    @Test
    void findingAnUnknownRunIsNotFound() {
        assertThatThrownBy(() -> service.find(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    private void existingSnapshotWithTotal(String total) {
        jdbc.update(
                "INSERT INTO snapshot (snap_date, total_value, total_cost_basis) VALUES (DATE '2026-09-26', ?::numeric, 1)",
                total);
    }

    private BigDecimal snapshotTotal() {
        return jdbc.queryForObject(
                "SELECT total_value FROM snapshot WHERE snap_date = DATE '2026-09-26'",
                BigDecimal.class);
    }

    @Test
    void aSucceededRunWritesTodaysSnapshot() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        fakeYahoo.answers.put("VTI", ok("120"));

        service.start();
        runner.runAll();

        assertThat(snapshotTotal()).isEqualByComparingTo("1200");
    }

    @Test
    void aPartialRunStillWritesASnapshotUsingLastKnownPrices() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        held("BTC", AssetType.CRYPTO, PriceSource.COINGECKO, "bitcoin");
        fakeYahoo.answers.put("VTI", ok("120"));
        fakeCoinGecko.answers.put("bitcoin", PriceResult.failure("HTTP 429"));

        UUID id = service.start();
        runner.runAll();

        assertThat(service.find(id).status()).isEqualTo(RefreshStatus.PARTIAL);
        assertThat(snapshotTotal()).isEqualByComparingTo("1200");
        assertThat(jdbc.queryForObject("SELECT unpriced_positions FROM snapshot", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void aFailedRunLeavesTheExistingSnapshotUntouched() {
        existingSnapshotWithTotal("777");
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        fakeYahoo.answers.put("VTI", PriceResult.failure("HTTP 500"));

        UUID id = service.start();
        runner.runAll();

        assertThat(service.find(id).status()).isEqualTo(RefreshStatus.FAILED);
        assertThat(snapshotTotal()).isEqualByComparingTo("777");
    }

    @Test
    void refreshingTwiceTheSameDayLeavesOneSnapshotRow() {
        held("VTI", AssetType.ETF, PriceSource.YAHOO, "VTI");
        fakeYahoo.answers.put("VTI", ok("120"));

        service.start();
        runner.runAll();
        fakeYahoo.answers.put("VTI", ok("130"));
        service.start();
        runner.runAll();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM snapshot", Long.class)).isEqualTo(1);
        assertThat(snapshotTotal()).isEqualByComparingTo("1300");
    }
}
