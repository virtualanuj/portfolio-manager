package com.portfoliomanager.application;

/** The outcome for one instrument in a refresh run; {@code message} is set on failure. */
public record RefreshResultItem(String symbol, boolean ok, String message) {}
