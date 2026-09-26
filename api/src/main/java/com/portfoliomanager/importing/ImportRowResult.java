package com.portfoliomanager.importing;

import java.util.List;

/**
 * The verdict on one CSV row. {@code parsed} is null when the row has field errors. A duplicate is
 * skipped on commit unless the user opts to include it.
 */
public record ImportRowResult(
        int lineNo,
        ParsedRow parsed,
        List<ImportIssue> errors,
        List<ImportIssue> warnings,
        boolean createsAccount,
        boolean createsInstrument,
        boolean duplicate) {

    public ImportRowResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public RowStatus status() {
        if (!errors.isEmpty()) {
            return RowStatus.ERROR;
        }
        if (!warnings.isEmpty()) {
            return RowStatus.WARNING;
        }
        return createsAccount || createsInstrument ? RowStatus.WILL_CREATE : RowStatus.OK;
    }

    ImportRowResult withError(ImportIssue issue) {
        List<ImportIssue> all = new java.util.ArrayList<>(errors);
        all.add(issue);
        return new ImportRowResult(
                lineNo, parsed, all, warnings, createsAccount, createsInstrument, duplicate);
    }
}
