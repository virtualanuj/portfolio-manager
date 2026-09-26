package com.portfoliomanager.pricing;

import com.portfoliomanager.domain.PriceState;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Decides how trustworthy a price is; thresholds are from spec section 6. */
public final class StalenessPolicy {

    /** Covers weekends and holidays without false alarms. */
    static final int FEED_MAX_AGE_DAYS = 4;

    /** Retirement-fund NAVs update slowly. */
    static final int MANUAL_MAX_AGE_DAYS = 45;

    private StalenessPolicy() {}

    public static PriceState stateOf(PriceFacts facts, LocalDate today) {
        if (facts.manualSource()) {
            return manualState(facts.manualAsOf(), today);
        }
        return feedState(facts.priceDate(), facts.feedError(), today);
    }

    private static PriceState manualState(LocalDate manualAsOf, LocalDate today) {
        if (manualAsOf == null) {
            return PriceState.UNPRICED;
        }
        return ageInDays(manualAsOf, today) > MANUAL_MAX_AGE_DAYS
                ? PriceState.MANUAL_STALE
                : PriceState.MANUAL;
    }

    private static PriceState feedState(LocalDate priceDate, boolean feedError, LocalDate today) {
        if (priceDate == null) {
            return PriceState.UNPRICED;
        }
        if (feedError || ageInDays(priceDate, today) > FEED_MAX_AGE_DAYS) {
            return PriceState.STALE;
        }
        return PriceState.OK;
    }

    private static long ageInDays(LocalDate date, LocalDate today) {
        return ChronoUnit.DAYS.between(date, today);
    }
}
