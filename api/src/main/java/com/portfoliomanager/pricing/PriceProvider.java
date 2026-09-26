package com.portfoliomanager.pricing;

import java.util.Collection;
import java.util.Map;

/**
 * A source of prices. Implementations never throw for a failed fetch: every requested id gets a
 * {@link PriceResult}, so one bad instrument cannot fail a refresh.
 */
public interface PriceProvider {

    PriceSource source();

    /** Fetches prices; the result has one entry per requested source id. */
    Map<String, PriceResult> fetch(Collection<String> sourceIds);
}
