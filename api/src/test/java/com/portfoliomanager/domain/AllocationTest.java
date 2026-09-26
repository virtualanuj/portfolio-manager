package com.portfoliomanager.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllocationTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static PositionValue priced(AssetType type, String value) {
        Position position = new Position(List.of(new Lot(BigDecimal.ONE, d(value))));
        return Valuation.value(type, position, new PriceInput(d(value), null, PriceState.OK));
    }

    private static PositionValue unpriced(AssetType type) {
        Position position = new Position(List.of(new Lot(BigDecimal.ONE, d("100"))));
        return Valuation.value(type, position, new PriceInput(null, null, PriceState.UNPRICED));
    }

    private static Map<AssetType, BigDecimal> targets(Object... pairs) {
        Map<AssetType, BigDecimal> result = new EnumMap<>(AssetType.class);
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((AssetType) pairs[i], d((String) pairs[i + 1]));
        }
        return result;
    }

    private static AllocationRow row(List<AllocationRow> rows, AssetType type) {
        return rows.stream().filter(r -> r.assetType() == type).findFirst().orElseThrow();
    }

    @Test
    void actualPercentagesAreValueShares() {
        List<AllocationRow> rows =
                Allocation.compute(
                        List.of(priced(AssetType.ETF, "750"), priced(AssetType.CRYPTO, "250")),
                        Map.of());

        assertThat(row(rows, AssetType.ETF).actualPct()).isEqualByComparingTo("75");
        assertThat(row(rows, AssetType.CRYPTO).actualPct()).isEqualByComparingTo("25");
        assertThat(row(rows, AssetType.ETF).value()).isEqualByComparingTo("750");
    }

    @Test
    void positionsOfTheSameAssetTypeAreSummed() {
        List<AllocationRow> rows =
                Allocation.compute(
                        List.of(
                                priced(AssetType.ETF, "300"),
                                priced(AssetType.ETF, "300"),
                                priced(AssetType.STOCK, "400")),
                        Map.of());

        assertThat(row(rows, AssetType.ETF).value()).isEqualByComparingTo("600");
        assertThat(row(rows, AssetType.ETF).actualPct()).isEqualByComparingTo("60");
    }

    @Test
    void actualPercentagesSumToExactlyOneHundredWithRemainderOnLargestBucket() {
        List<AllocationRow> rows =
                Allocation.compute(
                        List.of(
                                priced(AssetType.STOCK, "100"),
                                priced(AssetType.ETF, "100"),
                                priced(AssetType.CRYPTO, "100")),
                        Map.of());

        BigDecimal sum =
                rows.stream()
                        .map(AllocationRow::actualPct)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    void remainderGoesToTheLargestBucket() {
        List<AllocationRow> uneven =
                Allocation.compute(
                        List.of(
                                priced(AssetType.STOCK, "3"),
                                priced(AssetType.ETF, "3"),
                                priced(AssetType.CRYPTO, "1")),
                        Map.of());
        BigDecimal sum =
                uneven.stream()
                        .map(AllocationRow::actualPct)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100");
        // 3/7 = 42.8571 twice, 1/7 = 14.2857: total 99.9999, so the largest (first tie) gets
        // +0.0001
        assertThat(row(uneven, AssetType.STOCK).actualPct()).isEqualByComparingTo("42.8572");
        assertThat(row(uneven, AssetType.ETF).actualPct()).isEqualByComparingTo("42.8571");
        assertThat(row(uneven, AssetType.CRYPTO).actualPct()).isEqualByComparingTo("14.2857");
    }

    @Test
    void driftIsActualMinusTarget() {
        List<AllocationRow> rows =
                Allocation.compute(
                        List.of(priced(AssetType.ETF, "700"), priced(AssetType.CRYPTO, "300")),
                        targets(AssetType.ETF, "60", AssetType.CRYPTO, "40"));

        assertThat(row(rows, AssetType.ETF).targetPct()).isEqualByComparingTo("60");
        assertThat(row(rows, AssetType.ETF).driftPct()).isEqualByComparingTo("10");
        assertThat(row(rows, AssetType.CRYPTO).driftPct()).isEqualByComparingTo("-10");
    }

    @Test
    void targetWithoutHoldingsShowsAsZeroActual() {
        List<AllocationRow> rows =
                Allocation.compute(
                        List.of(priced(AssetType.ETF, "100")),
                        targets(AssetType.ETF, "80", AssetType.MUTUAL_FUND, "20"));

        AllocationRow fund = row(rows, AssetType.MUTUAL_FUND);
        assertThat(fund.value()).isEqualByComparingTo("0");
        assertThat(fund.actualPct()).isEqualByComparingTo("0");
        assertThat(fund.driftPct()).isEqualByComparingTo("-20");
    }

    @Test
    void withoutTargetsOnlyActualsAreReported() {
        List<AllocationRow> rows =
                Allocation.compute(List.of(priced(AssetType.ETF, "100")), Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).targetPct()).isNull();
        assertThat(rows.get(0).driftPct()).isNull();
    }

    @Test
    void unpricedPositionsAreExcluded() {
        List<AllocationRow> rows =
                Allocation.compute(
                        List.of(priced(AssetType.ETF, "100"), unpriced(AssetType.CRYPTO)),
                        Map.of());

        assertThat(rows).extracting(AllocationRow::assetType).containsExactly(AssetType.ETF);
        assertThat(rows.get(0).actualPct()).isEqualByComparingTo("100");
    }

    @Test
    void zeroTotalValueGivesZeroPercentagesWithoutDivisionErrors() {
        List<AllocationRow> rows =
                Allocation.compute(List.of(), targets(AssetType.ETF, "60", AssetType.CRYPTO, "40"));

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.actualPct()).isEqualByComparingTo("0"));
        assertThat(row(rows, AssetType.ETF).driftPct()).isEqualByComparingTo("-60");
    }

    @Test
    void emptyPortfolioWithoutTargetsHasNoRows() {
        assertThat(Allocation.compute(List.of(), Map.of())).isEmpty();
    }

    @Test
    void targetsNotSummingToOneHundredAreRejected() {
        assertThatThrownBy(
                        () ->
                                Allocation.validateTargets(
                                        targets(AssetType.ETF, "60", AssetType.CRYPTO, "30")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
        assertThatThrownBy(
                        () ->
                                Allocation.compute(
                                        List.of(priced(AssetType.ETF, "100")),
                                        targets(AssetType.ETF, "99.99")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeOrOversizedTargetsAreRejected() {
        assertThatThrownBy(
                        () ->
                                Allocation.validateTargets(
                                        targets(AssetType.ETF, "110", AssetType.CRYPTO, "-10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validTargetsAndEmptyTargetsPass() {
        assertThatCode(
                        () ->
                                Allocation.validateTargets(
                                        targets(AssetType.ETF, "60.50", AssetType.CRYPTO, "39.50")))
                .doesNotThrowAnyException();
        assertThatCode(() -> Allocation.validateTargets(Map.of())).doesNotThrowAnyException();
    }
}
