package com.portfoliomanager.importing;

/** A problem or note about one column of a staged row. */
public record ImportIssue(String field, String message) {}
