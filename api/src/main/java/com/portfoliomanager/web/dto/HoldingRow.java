package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.HoldingView;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.PositionValue;
import com.portfoliomanager.domain.PriceState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of {@code GET /api/holdings}; see spec section 9. Unpriced rows have null value fields.
 */
public record HoldingRow(
        UUID accountId,
        String accountName,
        UUID instrumentId,
        String symbol,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal costBasis,
        BigDecimal price,
        BigDecimal value,
        BigDecimal unrealized,
        BigDecimal unrealizedPct,
        BigDecimal dayChange,
        PriceState priceStatus,
        LocalDate priceAsOf) {

    public static HoldingRow from(HoldingView view) {
        PositionValue p = view.position();
        return new HoldingRow(
                view.accountId(),
                view.accountName(),
                view.instrumentId(),
                view.symbol(),
                p.assetType(),
                p.quantity(),
                view.avgCost(),
                p.costBasis(),
                p.price(),
                p.value(),
                p.unrealized(),
                p.unrealizedPct(),
                p.dayChange(),
                p.state(),
                view.priceAsOf());
    }
}
