package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Replays the transactions of one position with first-in-first-out lots.
 *
 * <p>Example: BUY 10 @ 100, BUY 10 @ 120, SELL 15, SPLIT 2:1 leaves the lot {@code 5 @ 120}, then
 * {@code 10 @ 60} with a cost basis of 600.
 *
 * <p>Intermediate arithmetic uses scale 8 with HALF_UP; the total basis is summed unrounded and
 * rounded once (see {@link Position#costBasis()}).
 */
public final class FifoEngine {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private FifoEngine() {}

    /** Replays {@code transactions} in {@code (date, seq)} order without modifying the list. */
    public static ReplayResult replay(List<Txn> transactions) {
        List<Txn> ordered = new ArrayList<>(transactions);
        ordered.sort(Comparator.comparing(Txn::date).thenComparingLong(Txn::seq));

        Deque<Lot> lots = new ArrayDeque<>();
        for (Txn txn : ordered) {
            switch (txn.type()) {
                case BUY, REINVEST -> lots.addLast(new Lot(txn.quantity(), txn.unitPrice()));
                case SPLIT -> applySplit(lots, txn.splitNum(), txn.splitDen());
                case SELL -> {
                    Optional<Violation> violation = applySell(lots, txn);
                    if (violation.isPresent()) {
                        return new ReplayResult(new Position(List.copyOf(lots)), violation);
                    }
                }
            }
        }
        return new ReplayResult(new Position(List.copyOf(lots)), Optional.empty());
    }

    private static void applySplit(Deque<Lot> lots, int numerator, int denominator) {
        BigDecimal n = BigDecimal.valueOf(numerator);
        BigDecimal m = BigDecimal.valueOf(denominator);
        List<Lot> scaled = new ArrayList<>(lots.size());
        for (Lot lot : lots) {
            scaled.add(
                    new Lot(
                            lot.quantity().multiply(n).divide(m, SCALE, ROUNDING),
                            lot.unitCost().multiply(m).divide(n, SCALE, ROUNDING)));
        }
        lots.clear();
        lots.addAll(scaled);
    }

    private static Optional<Violation> applySell(Deque<Lot> lots, Txn sell) {
        BigDecimal held = lots.stream().map(Lot::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sell.quantity().compareTo(held) > 0) {
            String message =
                    "Sell of %s exceeds the %s held on %s"
                            .formatted(
                                    sell.quantity().stripTrailingZeros().toPlainString(),
                                    held.stripTrailingZeros().toPlainString(),
                                    sell.date());
            return Optional.of(new Violation(sell.id(), message));
        }
        BigDecimal remaining = sell.quantity();
        while (remaining.signum() > 0) {
            Lot oldest = lots.removeFirst();
            if (oldest.quantity().compareTo(remaining) > 0) {
                lots.addFirst(new Lot(oldest.quantity().subtract(remaining), oldest.unitCost()));
                remaining = BigDecimal.ZERO;
            } else {
                remaining = remaining.subtract(oldest.quantity());
            }
        }
        return Optional.empty();
    }
}
