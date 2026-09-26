package com.portfoliomanager.e2e;

import com.portfoliomanager.pricing.PriceProvider;
import com.portfoliomanager.pricing.PriceResult;
import com.portfoliomanager.pricing.PriceSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A price provider for end-to-end tests: answers come from what the test last configured. */
public final class StubPriceProvider implements PriceProvider {

    private final PriceSource source;
    private final Map<String, PriceResult> answers = new ConcurrentHashMap<>();
    private volatile boolean unreachable;

    StubPriceProvider(PriceSource source) {
        this.source = source;
    }

    @Override
    public PriceSource source() {
        return source;
    }

    @Override
    public Map<String, PriceResult> fetch(Collection<String> sourceIds) {
        Map<String, PriceResult> result = new LinkedHashMap<>();
        for (String id : sourceIds) {
            if (unreachable) {
                result.put(id, PriceResult.failure("Stub provider is unreachable"));
            } else if (answers.containsKey(id)) {
                result.put(id, answers.get(id));
            }
        }
        return result;
    }

    void configure(boolean unreachable, Map<String, PriceResult> answers) {
        this.unreachable = unreachable;
        this.answers.clear();
        this.answers.putAll(answers);
    }
}
