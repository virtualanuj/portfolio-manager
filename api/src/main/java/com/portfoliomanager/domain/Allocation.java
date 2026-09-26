package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Actual-versus-target allocation by asset type; see spec section 5.4. */
public final class Allocation {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Allocation() {}

    /**
     * Requires every target to be within 0..100 and, unless the map is empty (no targets), the sum
     * to be exactly 100.00.
     */
    public static void validateTargets(Map<AssetType, BigDecimal> targets) {
        if (targets.isEmpty()) {
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<AssetType, BigDecimal> entry : targets.entrySet()) {
            BigDecimal pct = entry.getValue();
            if (pct.signum() < 0 || pct.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException(
                        "Target for %s must be between 0 and 100".formatted(entry.getKey()));
            }
            sum = sum.add(pct);
        }
        if (sum.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException(
                    "Targets must add up to 100.00 but add up to %s"
                            .formatted(sum.toPlainString()));
        }
    }

    /**
     * Computes one row per asset type that has value or a target, in enum order. Unpriced positions
     * are ignored. Actual percentages add up to exactly 100: the rounding remainder goes to the
     * largest bucket (the first one on a tie).
     */
    public static List<AllocationRow> compute(
            List<PositionValue> positions, Map<AssetType, BigDecimal> targets) {
        validateTargets(targets);

        Map<AssetType, BigDecimal> valueByType = new EnumMap<>(AssetType.class);
        BigDecimal total = BigDecimal.ZERO;
        for (PositionValue position : positions) {
            if (position.isPriced()) {
                valueByType.merge(position.assetType(), position.value(), BigDecimal::add);
                total = total.add(position.value());
            }
        }

        List<AssetType> included = new ArrayList<>();
        for (AssetType type : AssetType.values()) {
            BigDecimal value = valueByType.getOrDefault(type, BigDecimal.ZERO);
            if (value.signum() > 0 || targets.containsKey(type)) {
                included.add(type);
            }
        }

        Map<AssetType, BigDecimal> actual = new EnumMap<>(AssetType.class);
        for (AssetType type : included) {
            actual.put(type, share(valueByType.getOrDefault(type, BigDecimal.ZERO), total));
        }
        assignRemainderToLargest(actual, valueByType, total);

        List<AllocationRow> rows = new ArrayList<>();
        for (AssetType type : included) {
            BigDecimal target = targets.get(type);
            BigDecimal actualPct = actual.get(type);
            rows.add(
                    new AllocationRow(
                            type,
                            valueByType.getOrDefault(type, BigDecimal.ZERO).setScale(SCALE),
                            actualPct,
                            target == null ? null : target.setScale(SCALE),
                            target == null ? null : actualPct.subtract(target).setScale(SCALE)));
        }
        return rows;
    }

    private static BigDecimal share(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return value.multiply(HUNDRED).divide(total, SCALE, ROUNDING);
    }

    private static void assignRemainderToLargest(
            Map<AssetType, BigDecimal> actual,
            Map<AssetType, BigDecimal> values,
            BigDecimal total) {
        if (total.signum() == 0 || actual.isEmpty()) {
            return;
        }
        BigDecimal remainder =
                HUNDRED.setScale(SCALE)
                        .subtract(
                                actual.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        if (remainder.signum() == 0) {
            return;
        }
        AssetType largest = null;
        for (AssetType type : actual.keySet()) {
            if (largest == null
                    || values.getOrDefault(type, BigDecimal.ZERO)
                                    .compareTo(values.getOrDefault(largest, BigDecimal.ZERO))
                            > 0) {
                largest = type;
            }
        }
        actual.merge(largest, remainder, BigDecimal::add);
    }
}
