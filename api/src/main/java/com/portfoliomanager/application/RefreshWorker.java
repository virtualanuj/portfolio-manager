package com.portfoliomanager.application;

import com.portfoliomanager.domain.RefreshStatus;
import com.portfoliomanager.persistence.RefreshRunEntity;
import com.portfoliomanager.persistence.RefreshRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Executes one refresh run in the background and always leaves it in a final state. */
@Component
public class RefreshWorker {

    private static final Logger log = LoggerFactory.getLogger(RefreshWorker.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final PortfolioService portfolio;
    private final PriceService priceService;
    private final SnapshotService snapshotService;
    private final RefreshRunRepository runs;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RefreshWorker(
            PortfolioService portfolio,
            PriceService priceService,
            SnapshotService snapshotService,
            RefreshRunRepository runs,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.portfolio = portfolio;
        this.priceService = priceService;
        this.snapshotService = snapshotService;
        this.runs = runs;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Fetches prices for open positions, then records today's snapshot unless every fetch failed,
     * so a total outage leaves the previous snapshot untouched.
     */
    public void run(UUID runId) {
        List<RefreshResultItem> results;
        RefreshStatus status;
        try {
            Set<UUID> instrumentIds =
                    portfolio.allHoldings().stream()
                            .map(HoldingView::instrumentId)
                            .collect(Collectors.toSet());
            List<PriceRefreshOutcome> outcomes = priceService.refresh(instrumentIds);
            results =
                    outcomes.stream()
                            .map(o -> new RefreshResultItem(o.symbol(), o.ok(), o.message()))
                            .toList();
            status = statusOf(outcomes);
            if (status != RefreshStatus.FAILED) {
                snapshotService.snapshotToday();
            }
        } catch (Exception e) {
            // Boundary: nothing may escape a background run, or it would stay RUNNING.
            log.error("Refresh {} failed unexpectedly", runId, e);
            results =
                    List.of(
                            new RefreshResultItem(
                                    "(refresh)", false, "Unexpected error: " + e.getMessage()));
            status = RefreshStatus.FAILED;
        }
        finish(runId, status, results);
    }

    /** No fetch attempted or all succeeded is SUCCEEDED; every attempt failing is FAILED. */
    private static RefreshStatus statusOf(List<PriceRefreshOutcome> outcomes) {
        long failed = outcomes.stream().filter(o -> !o.ok()).count();
        if (failed == 0) {
            return RefreshStatus.SUCCEEDED;
        }
        return failed == outcomes.size() ? RefreshStatus.FAILED : RefreshStatus.PARTIAL;
    }

    private void finish(UUID runId, RefreshStatus status, List<RefreshResultItem> results) {
        String json = MAPPER.writeValueAsString(results);
        transactions.executeWithoutResult(
                s -> {
                    RefreshRunEntity run = runs.findById(runId).orElseThrow();
                    run.finish(status, json, Instant.now(clock));
                    runs.save(run);
                });
    }
}
