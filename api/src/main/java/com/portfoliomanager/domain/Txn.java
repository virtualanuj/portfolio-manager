package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One transaction of a single position, as input to {@link FifoEngine}.
 *
 * <p>BUY, SELL and REINVEST carry {@code quantity} and {@code unitPrice}; SPLIT carries the ratio
 * {@code splitNum:splitDen} (2:1 doubles the quantity) and no quantity or price.
 */
public record Txn(
        UUID id,
        long seq,
        TxnType type,
        LocalDate date,
        BigDecimal quantity,
        BigDecimal unitPrice,
        int splitNum,
        int splitDen) {

    public Txn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(date, "date");
        if (type == TxnType.SPLIT) {
            if (splitNum <= 0 || splitDen <= 0) {
                throw new IllegalArgumentException("A split needs a positive ratio");
            }
        } else {
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(unitPrice, "unitPrice");
        }
    }
}
