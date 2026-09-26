package com.portfoliomanager.testsupport;

import com.portfoliomanager.pricing.PriceProvider;
import com.portfoliomanager.pricing.PriceResult;
import com.portfoliomanager.pricing.PriceSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A provider whose answers a test controls and whose calls it can inspect. */
public final class FakePriceProvider implements PriceProvider {

    private final PriceSource source;
    public final Map<String, PriceResult> answers = new LinkedHashMap<>();
    public final List<Collection<String>> calls = new ArrayList<>();

    /** When set, fetch throws it, simulating a provider bug. */
    public volatile RuntimeException failure;

    public FakePriceProvider(PriceSource source) {
        this.source = source;
    }

    @Override
    public PriceSource source() {
        return source;
    }

    @Override
    public synchronized Map<String, PriceResult> fetch(Collection<String> sourceIds) {
        calls.add(List.copyOf(sourceIds));
        if (failure != null) {
            throw failure;
        }
        Map<String, PriceResult> result = new LinkedHashMap<>();
        for (String id : sourceIds) {
            if (answers.containsKey(id)) {
                result.put(id, answers.get(id));
            }
        }
        return result;
    }
}
