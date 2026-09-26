package com.portfoliomanager.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValuationTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Position position(String quantity, String unitCost) {
        return new Position(List.of(new Lot(d(quantity), d(unitCost))));
    }

    private static PriceInput price(String price, String previous, PriceState state) {
        return new PriceInput(d(price), previous == null ? null : d(previous), state);
    }

    private static PositionValue value(AssetType type, Position position, PriceInput input) {
        return Valuation.value(type, position, input);
    }

    @Test
    void valueIsQuantityTimesPriceWithGainAndPercent() {
        PositionValue result =
                value(AssetType.ETF, position("10", "100"), price("118.4", null, PriceState.OK));

        assertThat(result.value()).isEqualByComparingTo("1184.0000");
        assertThat(result.costBasis()).isEqualByComparingTo("1000");
        assertThat(result.unrealized()).isEqualByComparingTo("184.0000");
        assertThat(result.unrealizedPct()).isEqualByComparingTo("18.4000");
        assertThat(result.value().scale()).isEqualTo(4);
        assertThat(result.unrealizedPct().scale()).isEqualTo(4);
    }

    @Test
    void lossIsNegative() {
        PositionValue result =
                value(AssetType.STOCK, position("10", "100"), price("90", null, PriceState.OK));

        assertThat(result.unrealized()).isEqualByComparingTo("-100");
        assertThat(result.unrealizedPct()).isEqualByComparingTo("-10");
    }

    @Test
    void percentIsAbsentWhenCostBasisIsZero() {
        PositionValue result =
                value(AssetType.CRYPTO, position("2", "0"), price("50", null, PriceState.OK));

        assertThat(result.value()).isEqualByComparingTo("100");
        assertThat(result.unrealized()).isEqualByComparingTo("100");
        assertThat(result.unrealizedPct()).isNull();
    }

    @Test
    void unpricedPositionHasNoValueOrGain() {
        PositionValue result =
                value(
                        AssetType.MUTUAL_FUND,
                        position("10", "100"),
                        new PriceInput(null, null, PriceState.UNPRICED));

        assertThat(result.state()).isEqualTo(PriceState.UNPRICED);
        assertThat(result.value()).isNull();
        assertThat(result.unrealized()).isNull();
        assertThat(result.unrealizedPct()).isNull();
        assertThat(result.costBasis()).isEqualByComparingTo("1000");
    }

    @Test
    void dayChangeUsesPreviousPriceWhenKnown() {
        PositionValue result =
                value(AssetType.ETF, position("10", "100"), price("110", "108", PriceState.OK));

        assertThat(result.dayChange()).isEqualByComparingTo("20");
    }

    @Test
    void dayChangeIsAbsentWithoutPreviousPrice() {
        PositionValue result =
                value(AssetType.ETF, position("10", "100"), price("110", null, PriceState.OK));

        assertThat(result.dayChange()).isNull();
    }

    @Test
    void totalsSumPricedPositionsOnlyAndCountUnpriced() {
        PositionValue etf =
                value(AssetType.ETF, position("10", "100"), price("120", "118", PriceState.OK));
        PositionValue unpriced =
                value(
                        AssetType.MUTUAL_FUND,
                        position("5", "200"),
                        new PriceInput(null, null, PriceState.UNPRICED));

        PortfolioTotals totals = Valuation.totals(List.of(etf, unpriced));

        assertThat(totals.totalValue()).isEqualByComparingTo("1200");
        assertThat(totals.totalCostBasis()).isEqualByComparingTo("1000");
        assertThat(totals.unrealized()).isEqualByComparingTo("200");
        assertThat(totals.unrealizedPct()).isEqualByComparingTo("20");
        assertThat(totals.pricedPositions()).isEqualTo(1);
        assertThat(totals.unpricedPositions()).isEqualTo(1);
        assertThat(totals.stalePositions()).isZero();
    }

    @Test
    void stalePositionsAreIncludedAndCounted() {
        PositionValue stale =
                value(AssetType.STOCK, position("10", "100"), price("120", null, PriceState.STALE));
        PositionValue manualStale =
                value(
                        AssetType.MUTUAL_FUND,
                        position("1", "50"),
                        price("60", null, PriceState.MANUAL_STALE));
        PositionValue manualFresh =
                value(
                        AssetType.MUTUAL_FUND,
                        position("1", "50"),
                        price("60", null, PriceState.MANUAL));

        PortfolioTotals totals = Valuation.totals(List.of(stale, manualStale, manualFresh));

        assertThat(totals.pricedPositions()).isEqualTo(3);
        assertThat(totals.stalePositions()).isEqualTo(2);
        assertThat(totals.totalValue()).isEqualByComparingTo("1320");
    }

    @Test
    void dayChangeTotalSumsOnlyPositionsThatHaveIt() {
        PositionValue withPrevious =
                value(AssetType.ETF, position("10", "100"), price("110", "108", PriceState.OK));
        PositionValue without =
                value(AssetType.STOCK, position("10", "100"), price("110", null, PriceState.OK));

        PortfolioTotals totals = Valuation.totals(List.of(withPrevious, without));

        assertThat(totals.dayChange()).isEqualByComparingTo("20");
    }

    @Test
    void dayChangeTotalIsAbsentWhenNoPositionHasIt() {
        PositionValue only =
                value(AssetType.STOCK, position("10", "100"), price("110", null, PriceState.OK));

        assertThat(Valuation.totals(List.of(only)).dayChange()).isNull();
    }

    @Test
    void emptyPortfolioHasZeroTotals() {
        PortfolioTotals totals = Valuation.totals(List.of());

        assertThat(totals.totalValue()).isEqualByComparingTo("0");
        assertThat(totals.totalCostBasis()).isEqualByComparingTo("0");
        assertThat(totals.unrealized()).isEqualByComparingTo("0");
        assertThat(totals.unrealizedPct()).isNull();
        assertThat(totals.dayChange()).isNull();
        assertThat(totals.pricedPositions()).isZero();
    }
}
