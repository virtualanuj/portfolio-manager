package com.portfoliomanager.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.domain.PriceState;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StalenessPolicyTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-26");

    private static PriceState feed(LocalDate priceDate, boolean feedError) {
        return StalenessPolicy.stateOf(new PriceFacts(false, priceDate, feedError, null), TODAY);
    }

    private static PriceState manual(LocalDate manualAsOf) {
        return StalenessPolicy.stateOf(new PriceFacts(true, null, false, manualAsOf), TODAY);
    }

    @Test
    void freshFeedPriceIsOk() {
        assertThat(feed(TODAY, false)).isEqualTo(PriceState.OK);
    }

    @Test
    void feedErrorMakesTheLastKnownPriceStale() {
        assertThat(feed(TODAY, true)).isEqualTo(PriceState.STALE);
    }

    @Test
    void feedPriceFourDaysOldIsStillOk() {
        assertThat(feed(TODAY.minusDays(4), false)).isEqualTo(PriceState.OK);
    }

    @Test
    void feedPriceFiveDaysOldIsStale() {
        assertThat(feed(TODAY.minusDays(5), false)).isEqualTo(PriceState.STALE);
    }

    @Test
    void feedWithoutAnyPriceIsUnpriced() {
        assertThat(feed(null, false)).isEqualTo(PriceState.UNPRICED);
        assertThat(feed(null, true)).isEqualTo(PriceState.UNPRICED);
    }

    @Test
    void manualPriceFortyFiveDaysOldIsStillManual() {
        assertThat(manual(TODAY.minusDays(45))).isEqualTo(PriceState.MANUAL);
    }

    @Test
    void manualPriceFortySixDaysOldIsManualStale() {
        assertThat(manual(TODAY.minusDays(46))).isEqualTo(PriceState.MANUAL_STALE);
    }

    @Test
    void manualSourceWithoutManualPriceIsUnpriced() {
        assertThat(manual(null)).isEqualTo(PriceState.UNPRICED);
    }

    @Test
    void manualSourceIgnoresFeedFacts() {
        PriceFacts facts = new PriceFacts(true, TODAY.minusDays(30), true, TODAY.minusDays(1));

        assertThat(StalenessPolicy.stateOf(facts, TODAY)).isEqualTo(PriceState.MANUAL);
    }

    @Test
    void priceDatedInTheFutureIsNotStale() {
        assertThat(feed(TODAY.plusDays(1), false)).isEqualTo(PriceState.OK);
    }
}
