package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The effective price of an instrument, already chosen by the caller (manual price for MANUAL
 * sources, otherwise the last good feed price).
 *
 * @param price null when the instrument has no usable price
 * @param prevPrice previous close; null when unknown
 */
public record PriceInput(BigDecimal price, BigDecimal prevPrice, PriceState state) {

    public PriceInput {
        Objects.requireNonNull(state, "state");
    }

    boolean isPriced() {
        return price != null && state != PriceState.UNPRICED;
    }
}
