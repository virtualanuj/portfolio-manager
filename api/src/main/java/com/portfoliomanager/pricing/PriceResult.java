package com.portfoliomanager.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The outcome of fetching one instrument's price. On success {@code error} is null and {@code
 * price} is set; on failure {@code error} says why and the other fields are null. {@code prevPrice}
 * (the previous close) may be null even on success.
 */
public record PriceResult(
        BigDecimal price, BigDecimal prevPrice, LocalDate priceDate, String error) {

    public static PriceResult ok(BigDecimal price, BigDecimal prevPrice, LocalDate priceDate) {
        return new PriceResult(price, prevPrice, priceDate, null);
    }

    public static PriceResult failure(String error) {
        return new PriceResult(null, null, null, error);
    }

    public boolean isOk() {
        return error == null;
    }
}
