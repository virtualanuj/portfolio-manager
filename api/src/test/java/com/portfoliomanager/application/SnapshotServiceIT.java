package com.portfoliomanager.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.portfoliomanager.persistence.SnapshotHoldingRepository;
import com.portfoliomanager.persistence.SnapshotRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class SnapshotServiceIT extends AbstractIntegrationTest {

    private static final Instant NOON = Instant.parse("2026-09-26T12:00:00Z");
    private static final Clock UTC_NOON = Clock.fixed(NOON, ZoneOffset.UTC);

    @Autowired private PortfolioService portfolio;
    @Autowired private SnapshotRepository snapshots;
    @Autowired private SnapshotHoldingRepository snapshotHoldings;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private PriceRepository prices;
    @Autowired private JdbcTemplate jdbc;

    private AccountEntity account;

    @BeforeEach
    void seedAccount() {
        account = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE));
    }

    private SnapshotService serviceAt(Clock clock) {
        return new SnapshotService(
                portfolio, snapshots, snapshotHoldings, entityManager, clock, transactionManager);
    }

    private InstrumentEntity held(
            String symbol, String quantity, String cost, String price, LocalDate priceDate) {
        InstrumentEntity instrument =
                instruments.save(
                        new InstrumentEntity(
                                symbol, symbol, AssetType.STOCK, PriceSource.YAHOO, symbol));
        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        instrument.getId(),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal(quantity),
                        new BigDecimal(cost),
                        null));
        if (price != null) {
            setPrice(instrument, price, priceDate);
        }
        return instrument;
    }

    private void setPrice(InstrumentEntity instrument, String price, LocalDate priceDate) {
        PriceEntity row =
                prices.findById(instrument.getId())
                        .orElseGet(() -> new PriceEntity(instrument.getId()));
        row.recordFeedPrice(new BigDecimal(price), null, priceDate, NOON);
        prices.saveAndFlush(row);
    }

    private long snapshotRows() {
        return jdbc.queryForObject("SELECT count(*) FROM snapshot", Long.class);
    }

    private long holdingRows() {
        return jdbc.queryForObject("SELECT count(*) FROM snapshot_holding", Long.class);
    }

    @Test
    void firstRunWritesOneSnapshotWithPerHoldingRowsAndStaleFlags() {
        held("FRESH", "10", "100", "120", LocalDate.parse("2026-09-25"));
        held("OLD", "5", "200", "210", LocalDate.parse("2026-09-01"));
        held("NONE", "1", "50", null, null);

        serviceAt(UTC_NOON).snapshotToday();

        assertThat(snapshotRows()).isEqualTo(1);
        Map<String, Object> snapshot = jdbc.queryForMap("SELECT * FROM snapshot");
        assertThat(snapshot.get("snap_date").toString()).isEqualTo("2026-09-26");
        assertThat((BigDecimal) snapshot.get("total_value")).isEqualByComparingTo("2250");
        assertThat((BigDecimal) snapshot.get("total_cost_basis")).isEqualByComparingTo("2000");
        assertThat(snapshot.get("priced_positions")).isEqualTo(2);
        assertThat(snapshot.get("stale_positions")).isEqualTo(1);
        assertThat(snapshot.get("unpriced_positions")).isEqualTo(1);

        List<Map<String, Object>> holdings =
                jdbc.queryForList(
                        "SELECT i.symbol, h.value, h.is_stale FROM snapshot_holding h"
                                + " JOIN instrument i ON i.id = h.instrument_id ORDER BY i.symbol");
        assertThat(holdings).hasSize(3);
        assertThat(holdings.get(0).get("symbol")).isEqualTo("FRESH");
        assertThat(holdings.get(0).get("is_stale")).isEqualTo(false);
        assertThat(holdings.get(1).get("symbol")).isEqualTo("NONE");
        assertThat(holdings.get(1).get("value")).isNull();
        assertThat(holdings.get(2).get("symbol")).isEqualTo("OLD");
        assertThat(holdings.get(2).get("is_stale")).isEqualTo(true);
    }

    @Test
    void aSecondRunTheSameDayKeepsOneSnapshotAndReplacesItsHoldings() {
        InstrumentEntity stock = held("AAA", "10", "100", "120", LocalDate.parse("2026-09-25"));
        SnapshotService service = serviceAt(UTC_NOON);
        service.snapshotToday();

        setPrice(stock, "150", LocalDate.parse("2026-09-26"));
        service.snapshotToday();

        assertThat(snapshotRows()).isEqualTo(1);
        assertThat(holdingRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_value FROM snapshot", BigDecimal.class))
                .isEqualByComparingTo("1500");
        assertThat(jdbc.queryForObject("SELECT value FROM snapshot_holding", BigDecimal.class))
                .isEqualByComparingTo("1500");
    }

    @Test
    void aClosedPositionDisappearsFromTheSameDaysHoldingsOnRerun() {
        InstrumentEntity a = held("AAA", "10", "100", "120", LocalDate.parse("2026-09-25"));
        held("BBB", "10", "100", "120", LocalDate.parse("2026-09-25"));
        SnapshotService service = serviceAt(UTC_NOON);
        service.snapshotToday();
        assertThat(holdingRows()).isEqualTo(2);

        transactions.saveAndFlush(
                TransactionEntity.trade(
                        account.getId(),
                        a.getId(),
                        TxnType.SELL,
                        LocalDate.parse("2026-09-26"),
                        new BigDecimal("10"),
                        new BigDecimal("120"),
                        null));
        service.snapshotToday();

        assertThat(holdingRows()).isEqualTo(1);
    }

    @Test
    void theSnapshotDateIsComputedInTheConfiguredTimeZone() {
        held("AAA", "1", "100", "100", LocalDate.parse("2026-09-25"));
        // 03:30Z on the 26th is 23:30 on the 25th in New York
        Instant lateEveningInNewYork = Instant.parse("2026-09-26T03:30:00Z");

        serviceAt(Clock.fixed(lateEveningInNewYork, ZoneId.of("America/New_York"))).snapshotToday();
        assertThat(jdbc.queryForObject("SELECT snap_date::text FROM snapshot", String.class))
                .isEqualTo("2026-09-25");

        jdbc.update("DELETE FROM snapshot");
        serviceAt(Clock.fixed(lateEveningInNewYork, ZoneOffset.UTC)).snapshotToday();
        assertThat(jdbc.queryForObject("SELECT snap_date::text FROM snapshot", String.class))
                .isEqualTo("2026-09-26");
    }

    @Test
    void noOpenPositionsWritesAZeroSnapshot() {
        serviceAt(UTC_NOON).snapshotToday();

        assertThat(snapshotRows()).isEqualTo(1);
        assertThat(holdingRows()).isZero();
        Map<String, Object> snapshot = jdbc.queryForMap("SELECT * FROM snapshot");
        assertThat((BigDecimal) snapshot.get("total_value")).isEqualByComparingTo("0");
        assertThat((BigDecimal) snapshot.get("total_cost_basis")).isEqualByComparingTo("0");
        assertThat(snapshot.get("priced_positions")).isEqualTo(0);
    }
}
