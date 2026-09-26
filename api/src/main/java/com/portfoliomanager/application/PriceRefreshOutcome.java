package com.portfoliomanager.application;

import java.util.UUID;

/** What happened to one instrument during a price refresh; {@code message} is set on failure. */
public record PriceRefreshOutcome(UUID instrumentId, String symbol, boolean ok, String message) {}
