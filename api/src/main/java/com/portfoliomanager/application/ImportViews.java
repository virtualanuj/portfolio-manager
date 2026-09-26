package com.portfoliomanager.application;

import com.portfoliomanager.domain.ImportBatchStatus;
import com.portfoliomanager.importing.ImportIssue;
import com.portfoliomanager.importing.RowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read models for the import screens. */
public final class ImportViews {

    private ImportViews() {}

    public record RowView(
            int lineNo,
            RowStatus status,
            Map<String, String> values,
            List<ImportIssue> errors,
            List<ImportIssue> warnings,
            boolean createsAccount,
            boolean createsInstrument,
            boolean duplicate) {}

    public record Summary(
            int totalRows,
            int ok,
            int willCreate,
            int warnings,
            int errors,
            int duplicates,
            int newAccounts,
            int newInstruments) {}

    /** {@code fileWarnings} (such as ignored columns) are only known when the file is uploaded. */
    public record Preview(
            UUID id,
            String filename,
            ImportBatchStatus status,
            Instant createdAt,
            Summary summary,
            List<String> fileWarnings,
            List<RowView> rows) {}

    public record CommitResult(
            int transactionsCreated,
            int accountsCreated,
            int instrumentsCreated,
            int duplicatesSkipped) {}
}
