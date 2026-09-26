package com.portfoliomanager.application;

import com.portfoliomanager.application.ImportViews.CommitResult;
import com.portfoliomanager.application.ImportViews.Preview;
import com.portfoliomanager.application.ImportViews.RowView;
import com.portfoliomanager.application.ImportViews.Summary;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.ImportBatchStatus;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.importing.CsvDocument;
import com.portfoliomanager.importing.CsvParser;
import com.portfoliomanager.importing.CsvRow;
import com.portfoliomanager.importing.ImportIssue;
import com.portfoliomanager.importing.ImportRowResult;
import com.portfoliomanager.importing.ImportValidator;
import com.portfoliomanager.importing.ParsedRow;
import com.portfoliomanager.importing.RowStatus;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.ImportBatchEntity;
import com.portfoliomanager.persistence.ImportBatchRepository;
import com.portfoliomanager.persistence.ImportRowEntity;
import com.portfoliomanager.persistence.ImportRowRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceSource;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two-step CSV import: {@link #stage} validates a file and stores it for review without touching
 * real tables; {@link #commit} re-validates against current data and inserts everything in one
 * transaction, so an import either happens completely or not at all.
 */
@Service
public class ImportService {

    static final Duration STAGED_RETENTION = Duration.ofHours(24);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** What is kept in the {@code parsed} column, including the flags the preview needs. */
    record StoredParse(
            ParsedRow row, boolean createsAccount, boolean createsInstrument, boolean duplicate) {}

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final TransactionRepository transactions;
    private final PositionLoader positions;
    private final Clock clock;

    public ImportService(
            ImportBatchRepository batches,
            ImportRowRepository rows,
            AccountRepository accounts,
            InstrumentRepository instruments,
            TransactionRepository transactions,
            PositionLoader positions,
            Clock clock) {
        this.batches = batches;
        this.rows = rows;
        this.accounts = accounts;
        this.instruments = instruments;
        this.transactions = transactions;
        this.positions = positions;
        this.clock = clock;
    }

    @Transactional
    public Preview stage(String filename, InputStream content) {
        purgeStaleBatches();
        CsvDocument document = CsvParser.parse(content);
        List<ImportRowResult> results =
                ImportValidator.validate(
                        document.rows(), StoredData.load(accounts, instruments, positions), false);

        ImportBatchEntity batch =
                batches.saveAndFlush(new ImportBatchEntity(filename, Instant.now(clock)));
        List<ImportRowEntity> entities = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            ImportRowResult result = results.get(i);
            entities.add(
                    new ImportRowEntity(
                            batch.getId(),
                            result.lineNo(),
                            MAPPER.writeValueAsString(document.rows().get(i).values()),
                            MAPPER.writeValueAsString(
                                    new StoredParse(
                                            result.parsed(),
                                            result.createsAccount(),
                                            result.createsInstrument(),
                                            result.duplicate())),
                            MAPPER.writeValueAsString(result.errors()),
                            MAPPER.writeValueAsString(result.warnings())));
        }
        rows.saveAll(entities);
        return preview(batch, entities, document.warnings());
    }

    @Transactional(readOnly = true)
    public Preview get(UUID id) {
        ImportBatchEntity batch = find(id);
        return preview(batch, rows.findByBatchIdOrderByLineNo(id), List.of());
    }

    @Transactional
    public void discard(UUID id) {
        batches.delete(find(id));
    }

    /** Inserts the staged rows; fails as a whole if any row has an error against current data. */
    @Transactional
    public CommitResult commit(UUID id, boolean includeDuplicates) {
        ImportBatchEntity batch = find(id);
        if (batch.getStatus() == ImportBatchStatus.COMMITTED) {
            throw new ConflictException("This import has already been committed");
        }
        List<CsvRow> csvRows =
                rows.findByBatchIdOrderByLineNo(id).stream()
                        .map(r -> new CsvRow(r.getLineNo(), readValues(r.getRaw())))
                        .toList();
        List<ImportRowResult> results =
                ImportValidator.validate(
                        csvRows,
                        StoredData.load(accounts, instruments, positions),
                        includeDuplicates);

        long errorRows = results.stream().filter(r -> !r.errors().isEmpty()).count();
        if (errorRows > 0) {
            throw new UnprocessableException(
                    "%d row%s still have errors, so nothing was imported. Fix the file and upload it again."
                            .formatted(errorRows, errorRows == 1 ? "" : "s"));
        }

        List<ImportRowResult> toInsert =
                results.stream().filter(r -> includeDuplicates || !r.duplicate()).toList();
        int duplicatesSkipped = results.size() - toInsert.size();

        Map<String, AccountEntity> accountByName = new HashMap<>();
        accounts.findAll().forEach(a -> accountByName.put(a.getName(), a));
        Map<String, InstrumentEntity> instrumentByKey = new HashMap<>();
        instruments
                .findAll()
                .forEach(i -> instrumentByKey.put(key(i.getSymbol(), i.getAssetType()), i));

        int accountsCreated = 0;
        int instrumentsCreated = 0;
        for (ImportRowResult result : toInsert) {
            ParsedRow row = result.parsed();
            if (!accountByName.containsKey(row.accountName())) {
                accountByName.put(
                        row.accountName(),
                        accounts.saveAndFlush(
                                new AccountEntity(row.accountName(), row.accountType())));
                accountsCreated++;
            }
            String instrumentKey = key(row.symbol(), row.assetType());
            if (!instrumentByKey.containsKey(instrumentKey)) {
                instrumentByKey.put(
                        instrumentKey, instruments.saveAndFlush(newInstrument(row, toInsert)));
                instrumentsCreated++;
            }
        }

        for (ImportRowResult result : toInsert) {
            ParsedRow row = result.parsed();
            UUID accountId = accountByName.get(row.accountName()).getId();
            UUID instrumentId = instrumentByKey.get(key(row.symbol(), row.assetType())).getId();
            TransactionEntity entity =
                    row.type() == TxnType.SPLIT
                            ? TransactionEntity.split(
                                    accountId,
                                    instrumentId,
                                    row.date(),
                                    row.splitNumerator(),
                                    row.splitDenominator(),
                                    row.note())
                            : TransactionEntity.trade(
                                    accountId,
                                    instrumentId,
                                    row.type(),
                                    row.date(),
                                    row.quantity(),
                                    row.unitPrice(),
                                    row.note());
            entity.setImportBatchId(batch.getId());
            transactions.saveAndFlush(entity);
        }

        batch.markCommitted();
        batches.save(batch);
        return new CommitResult(
                toInsert.size(), accountsCreated, instrumentsCreated, duplicatesSkipped);
    }

    /**
     * Defaults follow the instrument rules: crypto via CoinGecko with its coin id, the rest via
     * Yahoo.
     */
    private static InstrumentEntity newInstrument(ParsedRow first, List<ImportRowResult> all) {
        String sourceId =
                all.stream()
                        .map(ImportRowResult::parsed)
                        .filter(
                                p ->
                                        p.symbol().equals(first.symbol())
                                                && p.assetType() == first.assetType())
                        .map(ParsedRow::sourceId)
                        .filter(s -> s != null)
                        .findFirst()
                        .orElse(null);
        if (first.assetType() == AssetType.CRYPTO) {
            return new InstrumentEntity(
                    first.symbol(), null, AssetType.CRYPTO, PriceSource.COINGECKO, sourceId);
        }
        return new InstrumentEntity(
                first.symbol(),
                null,
                first.assetType(),
                PriceSource.YAHOO,
                sourceId != null ? sourceId : first.symbol());
    }

    private void purgeStaleBatches() {
        Instant cutoff = Instant.now(clock).minus(STAGED_RETENTION);
        batches.deleteAll(batches.findByStatusAndCreatedAtBefore(ImportBatchStatus.STAGED, cutoff));
        batches.flush();
    }

    private ImportBatchEntity find(UUID id) {
        return batches.findById(id)
                .orElseThrow(() -> new NotFoundException("Import %s does not exist".formatted(id)));
    }

    private static String key(String symbol, AssetType type) {
        return symbol + "/" + type;
    }

    private static Map<String, String> readValues(String json) {
        return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
    }

    private static Preview preview(
            ImportBatchEntity batch, List<ImportRowEntity> entities, List<String> fileWarnings) {
        List<RowView> views = new ArrayList<>();
        for (ImportRowEntity entity : entities) {
            StoredParse stored = MAPPER.readValue(entity.getParsed(), StoredParse.class);
            List<ImportIssue> errors =
                    MAPPER.readValue(entity.getErrors(), new TypeReference<List<ImportIssue>>() {});
            List<ImportIssue> warnings =
                    MAPPER.readValue(
                            entity.getWarnings(), new TypeReference<List<ImportIssue>>() {});
            RowStatus status =
                    new ImportRowResult(
                                    entity.getLineNo(),
                                    stored.row(),
                                    errors,
                                    warnings,
                                    stored.createsAccount(),
                                    stored.createsInstrument(),
                                    stored.duplicate())
                            .status();
            views.add(
                    new RowView(
                            entity.getLineNo(),
                            status,
                            readValues(entity.getRaw()),
                            errors,
                            warnings,
                            stored.createsAccount(),
                            stored.createsInstrument(),
                            stored.duplicate()));
        }
        return new Preview(
                batch.getId(),
                batch.getFilename(),
                batch.getStatus(),
                batch.getCreatedAt(),
                summarize(views),
                fileWarnings,
                views);
    }

    private static Summary summarize(List<RowView> views) {
        Set<String> newAccounts = new HashSet<>();
        Set<String> newInstruments = new HashSet<>();
        int ok = 0;
        int willCreate = 0;
        int warnings = 0;
        int errors = 0;
        int duplicates = 0;
        for (RowView view : views) {
            switch (view.status()) {
                case OK -> ok++;
                case WILL_CREATE -> willCreate++;
                case WARNING -> warnings++;
                case ERROR -> errors++;
            }
            if (view.duplicate()) {
                duplicates++;
            }
            if (view.createsAccount()) {
                newAccounts.add(view.values().getOrDefault("account", ""));
            }
            if (view.createsInstrument()) {
                newInstruments.add(
                        view.values().getOrDefault("symbol", "")
                                + "/"
                                + view.values().getOrDefault("asset_type", "").toUpperCase());
            }
        }
        return new Summary(
                views.size(),
                ok,
                willCreate,
                warnings,
                errors,
                duplicates,
                newAccounts.size(),
                newInstruments.size());
    }
}
