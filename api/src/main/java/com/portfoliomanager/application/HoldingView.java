package com.portfoliomanager.application;

import com.portfoliomanager.domain.PositionValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One open position with its valuation. {@code avgCost} is null when nothing is held; {@code
 * priceAsOf} is the date of the price used (manual as-of or feed date), null when unpriced.
 */
public record HoldingView(
        UUID accountId,
        String accountName,
        UUID instrumentId,
        String symbol,
        BigDecimal avgCost,
        LocalDate priceAsOf,
        PositionValue position) {}
