package com.portfoliomanager.testsupport;

import com.portfoliomanager.application.PriceService;
import com.portfoliomanager.application.RefreshRunner;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.PriceRepository;
import com.portfoliomanager.pricing.PriceSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Makes refresh deterministic in tests: a fixed clock, fake providers, and a runner that queues
 * background tasks until the test calls {@link ManualRunner#runAll()}.
 */
@TestConfiguration
public class RefreshTestConfig {

    public static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    /** Collects refresh tasks instead of running them. */
    public static final class ManualRunner implements RefreshRunner {
        public final List<Runnable> tasks = new ArrayList<>();

        @Override
        public synchronized void run(Runnable task) {
            tasks.add(task);
        }

        public synchronized void runAll() {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    public ManualRunner manualRunner() {
        return new ManualRunner();
    }

    @Bean
    public FakePriceProvider fakeYahoo() {
        return new FakePriceProvider(PriceSource.YAHOO);
    }

    @Bean
    public FakePriceProvider fakeCoinGecko() {
        return new FakePriceProvider(PriceSource.COINGECKO);
    }

    @Bean
    @Primary
    PriceService fakePriceService(
            FakePriceProvider fakeYahoo,
            FakePriceProvider fakeCoinGecko,
            InstrumentRepository instruments,
            PriceRepository prices,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        return new PriceService(
                List.of(fakeYahoo, fakeCoinGecko), instruments, prices, clock, transactionManager);
    }

    @Bean
    @Primary
    RefreshRunner primaryRunner(ManualRunner manualRunner) {
        return manualRunner;
    }
}
