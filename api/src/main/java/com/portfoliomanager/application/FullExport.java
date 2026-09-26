package com.portfoliomanager.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the app stores that cannot be recomputed, for the JSON backup. */
public record FullExport(
        List<AccountView> accounts,
        List<InstrumentView> instruments,
        List<TransactionView> transactions,
        List<ManualPrice> manualPrices,
        List<Target> targets,
        List<SnapshotPoint> snapshots) {

    public record ManualPrice(String symbol, String assetType, BigDecimal price, LocalDate asOf) {}

    public record Target(String assetType, BigDecimal targetPct) {}
}
