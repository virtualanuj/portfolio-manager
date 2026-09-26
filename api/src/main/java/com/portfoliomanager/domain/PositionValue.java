package com.portfoliomanager.domain;

import java.math.BigDecimal;

/**
 * A valued position. Money is at scale 4.
 *
 * <p>{@code price}, {@code value}, {@code unrealized} are null when the position is unpriced;
 * {@code unrealizedPct} (percent, for example 16.95) is also null when the cost basis is zero;
 * {@code dayChange} is null without a previous price.
 */
public record PositionValue(
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal price,
        BigDecimal value,
        BigDecimal unrealized,
        BigDecimal unrealizedPct,
        BigDecimal dayChange,
        PriceState state) {

    public boolean isPriced() {
        return value != null;
    }
}
