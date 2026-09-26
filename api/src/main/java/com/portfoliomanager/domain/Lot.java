package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Shares (or coins) bought together, with the per-unit cost they were bought at. */
public record Lot(BigDecimal quantity, BigDecimal unitCost) {

    public Lot {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitCost, "unitCost");
    }

    /** Unrounded cost of this lot; callers round once after summing. */
    BigDecimal cost() {
        return quantity.multiply(unitCost);
    }
}
