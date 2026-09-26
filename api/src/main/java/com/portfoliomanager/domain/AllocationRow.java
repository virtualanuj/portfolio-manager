package com.portfoliomanager.domain;

import java.math.BigDecimal;

/**
 * One asset type's share of the portfolio. Percentages are scale 4. {@code targetPct} and {@code
 * driftPct} are null when no targets are set.
 */
public record AllocationRow(
        AssetType assetType,
        BigDecimal value,
        BigDecimal actualPct,
        BigDecimal targetPct,
        BigDecimal driftPct) {}
