package com.portfoliomanager.domain;

import java.math.BigDecimal;

/**
 * Totals over priced positions only, so value, basis and gain always describe the same set of
 * positions. {@code unrealizedPct} is null when the basis is zero; {@code dayChange} is null when
 * no position has a previous price.
 */
public record PortfolioTotals(
        BigDecimal totalValue,
        BigDecimal totalCostBasis,
        BigDecimal unrealized,
        BigDecimal unrealizedPct,
        BigDecimal dayChange,
        int pricedPositions,
        int stalePositions,
        int unpricedPositions) {}
