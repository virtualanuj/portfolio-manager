package com.portfoliomanager.application;

import com.portfoliomanager.domain.PortfolioTotals;
import com.portfoliomanager.domain.PositionValue;
import com.portfoliomanager.domain.Valuation;
import com.portfoliomanager.persistence.SnapshotHoldingEntity;
import com.portfoliomanager.persistence.SnapshotHoldingRepository;
import com.portfoliomanager.persistence.SnapshotRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records today's portfolio value. The date comes from the application clock, so it follows {@code
 * app.timezone} rather than the server's zone. Everything is written in one transaction and the
 * write is idempotent: a second run the same day replaces the first.
 */
@Service
public class SnapshotService {

    private final PortfolioService portfolio;
    private final SnapshotRepository snapshots;
    private final SnapshotHoldingRepository holdings;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public SnapshotService(
            PortfolioService portfolio,
            SnapshotRepository snapshots,
            SnapshotHoldingRepository holdings,
            EntityManager entityManager,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.portfolio = portfolio;
        this.snapshots = snapshots;
        this.holdings = holdings;
        this.entityManager = entityManager;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Upserts today's snapshot and replaces today's per-holding rows. Returns the date written. */
    public LocalDate snapshotToday() {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        transactions.executeWithoutResult(status -> write(today, now));
        return today;
    }

    private void write(LocalDate date, Instant now) {
        List<HoldingView> open = portfolio.allHoldings();
        PortfolioTotals totals =
                Valuation.totals(open.stream().map(HoldingView::position).toList());

        snapshots.upsert(
                date,
                totals.totalValue(),
                totals.totalCostBasis(),
                totals.pricedPositions(),
                totals.stalePositions(),
                totals.unpricedPositions(),
                now);
        holdings.deleteBySnapDate(date);
        for (HoldingView holding : open) {
            PositionValue position = holding.position();
            entityManager.persist(
                    new SnapshotHoldingEntity(
                            date,
                            holding.accountId(),
                            holding.instrumentId(),
                            position.quantity(),
                            position.price(),
                            position.value(),
                            position.costBasis(),
                            position.state().isStale()));
        }
    }
}
