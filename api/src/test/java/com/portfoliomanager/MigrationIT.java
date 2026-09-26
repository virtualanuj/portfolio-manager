package com.portfoliomanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private UUID accountId;
    private UUID instrumentId;

    private void clearTables() {
        jdbc.execute(
                "TRUNCATE snapshot_holding, snapshot, \"transaction\", price, refresh_run,"
                        + " instrument, account CASCADE");
    }

    @AfterEach
    void cleanUp() {
        clearTables();
    }

    @BeforeEach
    void seed() {
        clearTables();
        accountId = UUID.randomUUID();
        instrumentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO account (id, name, type) VALUES (?, 'Main', 'BROKERAGE')", accountId);
        jdbc.update(
                "INSERT INTO instrument (id, symbol, name, asset_type, price_source, source_id)"
                        + " VALUES (?, 'VTI', 'Vanguard Total', 'ETF', 'YAHOO', 'VTI')",
                instrumentId);
    }

    private void insertTransaction(String type, String quantity, String splitNumerator) {
        jdbc.update(
                "INSERT INTO \"transaction\" (id, account_id, instrument_id, type, trade_date,"
                        + " quantity, unit_price, split_numerator, split_denominator)"
                        + " VALUES (?, ?, ?, ?, DATE '2026-01-05', ?::numeric, 10, ?::int, ?::int)",
                UUID.randomUUID(),
                accountId,
                instrumentId,
                type,
                quantity,
                splitNumerator,
                splitNumerator == null ? null : "1");
    }

    @Test
    void flywayHistoryContainsBaseline() {
        Integer applied =
                jdbc.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                        Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void sellWithZeroQuantityViolatesCheck() {
        assertThatThrownBy(() -> insertTransaction("SELL", "0", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void splitWithQuantityViolatesCheck() {
        assertThatThrownBy(() -> insertTransaction("SPLIT", "5", "2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validBuyIsAccepted() {
        insertTransaction("BUY", "5", null);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM \"transaction\"", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void secondRunningRefreshRunViolatesPartialUniqueIndex() {
        jdbc.update(
                "INSERT INTO refresh_run (id, status) VALUES (?, 'RUNNING')", UUID.randomUUID());
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO refresh_run (id, status) VALUES (?, 'RUNNING')",
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finishedRunsDoNotBlockANewRunningRun() {
        jdbc.update(
                "INSERT INTO refresh_run (id, status) VALUES (?, 'SUCCEEDED')", UUID.randomUUID());
        jdbc.update(
                "INSERT INTO refresh_run (id, status) VALUES (?, 'RUNNING')", UUID.randomUUID());
    }

    @Test
    void snapshotHoldingsCascadeWhenSnapshotIsDeleted() {
        jdbc.update(
                "INSERT INTO snapshot (snap_date, total_value, total_cost_basis) VALUES"
                        + " (DATE '2026-01-05', 100, 90)");
        jdbc.update(
                "INSERT INTO snapshot_holding (snap_date, account_id, instrument_id, quantity,"
                        + " price, value, cost_basis, is_stale) VALUES (DATE '2026-01-05', ?, ?, 1,"
                        + " 100, 100, 90, false)",
                accountId,
                instrumentId);
        jdbc.update("DELETE FROM snapshot WHERE snap_date = DATE '2026-01-05'");
        Integer left = jdbc.queryForObject("SELECT count(*) FROM snapshot_holding", Integer.class);
        assertThat(left).isZero();
    }
}
