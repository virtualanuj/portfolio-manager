package com.portfoliomanager.pricing;

import java.time.LocalDate;

/**
 * What is known about an instrument's price, as input to {@link StalenessPolicy}.
 *
 * @param manualSource the instrument's price source is MANUAL
 * @param priceDate date of the last good feed price; null if there never was one
 * @param feedError the most recent feed fetch failed
 * @param manualAsOf as-of date of the manual price; null if none is set
 */
public record PriceFacts(
        boolean manualSource, LocalDate priceDate, boolean feedError, LocalDate manualAsOf) {}
