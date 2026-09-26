package com.portfoliomanager.domain;

/** How trustworthy the effective price of a position is; values match the API's priceStatus. */
public enum PriceState {
    OK,
    STALE,
    MANUAL,
    MANUAL_STALE,
    UNPRICED;

    public boolean isStale() {
        return this == STALE || this == MANUAL_STALE;
    }
}
