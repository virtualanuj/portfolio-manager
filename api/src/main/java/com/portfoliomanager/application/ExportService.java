package com.portfoliomanager.application;

import com.portfoliomanager.importing.CsvWriter;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data export: a transactions CSV in the import schema, a snapshots CSV, and a full JSON backup.
 */
@Service
public class ExportService {

    static final List<String> TRANSACTION_COLUMNS =
            List.of(
                    "date",
                    "account",
                    "symbol",
                    "asset_type",
                    "type",
                    "quantity",
                    "price",
                    "split_ratio",
                    "source_id",
                    "account_type",
                    "note");

    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final TargetAllocationService targetService;
    private final SnapshotService snapshotService;

    public ExportService(
            TransactionRepository transactions,
            AccountRepository accounts,
            InstrumentRepository instruments,
            AccountService accountService,
            InstrumentService instrumentService,
            TargetAllocationService targetService,
            SnapshotService snapshotService) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.instruments = instruments;
        this.accountService = accountService;
        this.instrumentService = instrumentService;
        this.targetService = targetService;
        this.snapshotService = snapshotService;
    }

    /** Transactions in insertion order, so importing the file again rebuilds the same positions. */
    @Transactional(readOnly = true)
    public String transactionsCsv() {
        Map<UUID, AccountEntity> accountById = byId(accounts.findAll(), AccountEntity::getId);
        Map<UUID, InstrumentEntity> instrumentById =
                byId(instruments.findAll(), InstrumentEntity::getId);

        List<List<String>> rows = new ArrayList<>();
        for (TransactionEntity t : transactions.findAll(Sort.by("seq"))) {
            AccountEntity account = accountById.get(t.getAccountId());
            InstrumentEntity instrument = instrumentById.get(t.getInstrumentId());
            rows.add(
                    List.of(
                            t.getTradeDate().toString(),
                            CsvWriter.safeText(account.getName()),
                            CsvWriter.safeText(instrument.getSymbol()),
                            instrument.getAssetType().name(),
                            t.getType().name(),
                            plain(t.getQuantity()),
                            plain(t.getUnitPrice()),
                            t.getSplitNumerator() == null
                                    ? ""
                                    : t.getSplitNumerator() + ":" + t.getSplitDenominator(),
                            CsvWriter.safeText(instrument.getSourceId()),
                            account.getType().name(),
                            CsvWriter.safeText(t.getNote())));
        }
        return CsvWriter.write(TRANSACTION_COLUMNS, rows);
    }

    @Transactional(readOnly = true)
    public String snapshotsCsv() {
        List<List<String>> rows =
                snapshotService.history(EARLIEST, LATEST).stream()
                        .map(
                                p ->
                                        List.of(
                                                p.date().toString(),
                                                plain(p.totalValue()),
                                                plain(p.totalCostBasis())))
                        .toList();
        return CsvWriter.write(List.of("date", "total_value", "total_cost_basis"), rows);
    }

    @Transactional(readOnly = true)
    public FullExport fullExport() {
        List<InstrumentView> instrumentViews = instrumentService.list();
        List<FullExport.ManualPrice> manualPrices =
                instrumentViews.stream()
                        .filter(i -> i.manualPrice() != null)
                        .map(
                                i ->
                                        new FullExport.ManualPrice(
                                                i.symbol(),
                                                i.assetType().name(),
                                                i.manualPrice(),
                                                i.manualAsOf()))
                        .toList();
        List<FullExport.Target> targets = new ArrayList<>();
        targetService
                .current()
                .forEach((type, pct) -> targets.add(new FullExport.Target(type.name(), pct)));

        Map<UUID, AccountEntity> accountById = byId(accounts.findAll(), AccountEntity::getId);
        Map<UUID, InstrumentEntity> instrumentById =
                byId(instruments.findAll(), InstrumentEntity::getId);
        List<TransactionView> transactionViews =
                transactions.findAll(Sort.by("seq")).stream()
                        .map(
                                t ->
                                        new TransactionView(
                                                t.getId(),
                                                t.getSeq(),
                                                t.getAccountId(),
                                                accountById.get(t.getAccountId()).getName(),
                                                t.getInstrumentId(),
                                                instrumentById.get(t.getInstrumentId()).getSymbol(),
                                                t.getType(),
                                                t.getTradeDate(),
                                                t.getQuantity(),
                                                t.getUnitPrice(),
                                                t.getSplitNumerator(),
                                                t.getSplitDenominator(),
                                                t.getNote()))
                        .toList();
        return new FullExport(
                accountService.list(),
                instrumentViews,
                transactionViews,
                manualPrices,
                targets,
                snapshotService.history(EARLIEST, LATEST));
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static <T> Map<UUID, T> byId(List<T> items, java.util.function.Function<T, UUID> id) {
        Map<UUID, T> map = new HashMap<>();
        items.forEach(item -> map.put(id.apply(item), item));
        return map;
    }
}
