package com.portfoliomanager.importing;

import java.util.List;

/** A parsed upload: normalised header names, data rows, and warnings such as ignored columns. */
public record CsvDocument(List<String> headers, List<CsvRow> rows, List<String> warnings) {}
