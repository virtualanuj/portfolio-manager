package com.portfoliomanager.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Values positions and portfolios from an effective price; see spec section 5.3. */
public final class Valuation {

    private static final int SCALE = Position.MONEY_SCALE;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Valuation() {}

    public static PositionValue value(AssetType assetType, Position position, PriceInput input) {
        BigDecimal quantity = position.quantity();
        BigDecimal costBasis = position.costBasis();
        if (!input.isPriced()) {
            return new PositionValue(
                    assetType,
                    quantity,
                    costBasis,
                    null,
                    null,
                    null,
                    null,
                    null,
                    PriceState.UNPRICED);
        }
        BigDecimal value = quantity.multiply(input.price()).setScale(SCALE, ROUNDING);
        BigDecimal unrealized = value.subtract(costBasis);
        BigDecimal dayChange =
                input.prevPrice() == null
                        ? null
                        : quantity.multiply(input.price().subtract(input.prevPrice()))
                                .setScale(SCALE, ROUNDING);
        return new PositionValue(
                assetType,
                quantity,
                costBasis,
                input.price(),
                value,
                unrealized,
                percent(unrealized, costBasis),
                dayChange,
                input.state());
    }

    public static PortfolioTotals totals(List<PositionValue> positions) {
        BigDecimal value = zero();
        BigDecimal costBasis = zero();
        BigDecimal dayChange = null;
        int priced = 0;
        int stale = 0;
        int unpriced = 0;
        for (PositionValue position : positions) {
            if (!position.isPriced()) {
                unpriced++;
                continue;
            }
            priced++;
            if (position.state().isStale()) {
                stale++;
            }
            value = value.add(position.value());
            costBasis = costBasis.add(position.costBasis());
            if (position.dayChange() != null) {
                dayChange = (dayChange == null ? zero() : dayChange).add(position.dayChange());
            }
        }
        BigDecimal unrealized = value.subtract(costBasis);
        return new PortfolioTotals(
                value,
                costBasis,
                unrealized,
                percent(unrealized, costBasis),
                dayChange,
                priced,
                stale,
                unpriced);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal basis) {
        if (basis.signum() == 0) {
            return null;
        }
        return amount.multiply(HUNDRED).divide(basis, SCALE, ROUNDING);
    }
}
