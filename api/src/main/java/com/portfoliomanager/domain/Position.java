package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** The open lots of one (account, instrument) pair, oldest first. */
public record Position(List<Lot> lots) {

    public static final int MONEY_SCALE = 4;

    public Position {
        lots = List.copyOf(lots);
    }

    public BigDecimal quantity() {
        return lots.stream().map(Lot::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of lot costs, rounded once to money scale so per-lot rounding never accumulates. */
    public BigDecimal costBasis() {
        return unroundedCostBasis().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Cost basis per unit; empty when nothing is held. */
    public Optional<BigDecimal> avgCost() {
        BigDecimal quantity = quantity();
        if (quantity.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(
                unroundedCostBasis().divide(quantity, MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal unroundedCostBasis() {
        return lots.stream().map(Lot::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
