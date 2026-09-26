package com.portfoliomanager.web.dto;

import com.portfoliomanager.domain.PortfolioTotals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Dashboard totals. {@code dayChange} is null when no position has a previous price; {@code
 * lastRefresh} is null until a refresh has run.
 */
public record DashboardResponse(
        BigDecimal totalValue,
        BigDecimal totalCostBasis,
        BigDecimal unrealized,
        BigDecimal unrealizedPct,
        BigDecimal dayChange,
        int pricedPositions,
        int stalePositions,
        int unpricedPositions,
        LastRefresh lastRefresh) {

    public record LastRefresh(UUID runId, String status, Instant finishedAt) {}

    public static DashboardResponse from(PortfolioTotals totals, LastRefresh lastRefresh) {
        return new DashboardResponse(
                totals.totalValue(),
                totals.totalCostBasis(),
                totals.unrealized(),
                totals.unrealizedPct(),
                totals.dayChange(),
                totals.pricedPositions(),
                totals.stalePositions(),
                totals.unpricedPositions(),
                lastRefresh);
    }
}
