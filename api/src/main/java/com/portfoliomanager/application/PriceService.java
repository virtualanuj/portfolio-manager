package com.portfoliomanager.application;

import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.PriceEntity;
import com.portfoliomanager.persistence.PriceRepository;
import com.portfoliomanager.pricing.PriceProvider;
import com.portfoliomanager.pricing.PriceResult;
import com.portfoliomanager.pricing.PriceSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fetches prices from the providers and stores them. Each instrument's result is written in its own
 * small transaction so progress survives a crash, and a failure only flags the instrument: its last
 * good price stays. Deliberately not {@code @Transactional}, so the slow network calls never hold a
 * database transaction open.
 */
@Service
public class PriceService {

    private final Map<PriceSource, PriceProvider> providers = new EnumMap<>(PriceSource.class);
    private final InstrumentRepository instruments;
    private final PriceRepository prices;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PriceService(
            List<PriceProvider> providers,
            InstrumentRepository instruments,
            PriceRepository prices,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        providers.forEach(provider -> this.providers.put(provider.source(), provider));
        this.instruments = instruments;
        this.prices = prices;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Refreshes the given instruments; MANUAL ones are never sent to a provider. */
    public List<PriceRefreshOutcome> refresh(Collection<UUID> instrumentIds) {
        Map<PriceSource, List<InstrumentEntity>> bySource =
                instruments.findAllById(instrumentIds).stream()
                        .filter(i -> i.getPriceSource() != PriceSource.MANUAL)
                        .collect(Collectors.groupingBy(InstrumentEntity::getPriceSource));

        List<PriceRefreshOutcome> outcomes = new ArrayList<>();
        bySource.forEach(
                (source, group) -> outcomes.addAll(refreshFrom(providerFor(source), group)));
        return outcomes;
    }

    private PriceProvider providerFor(PriceSource source) {
        PriceProvider provider = providers.get(source);
        if (provider == null) {
            throw new IllegalStateException("No price provider is configured for " + source);
        }
        return provider;
    }

    private List<PriceRefreshOutcome> refreshFrom(
            PriceProvider provider, List<InstrumentEntity> group) {
        Map<String, InstrumentEntity> bySourceId = new LinkedHashMap<>();
        for (InstrumentEntity instrument : group) {
            bySourceId.put(sourceIdOf(instrument), instrument);
        }
        Map<String, PriceResult> results = provider.fetch(bySourceId.keySet());

        List<PriceRefreshOutcome> outcomes = new ArrayList<>();
        bySourceId.forEach(
                (sourceId, instrument) -> {
                    PriceResult result =
                            results.getOrDefault(
                                    sourceId,
                                    PriceResult.failure("The provider returned no result"));
                    store(instrument.getId(), result);
                    outcomes.add(
                            new PriceRefreshOutcome(
                                    instrument.getId(),
                                    instrument.getSymbol(),
                                    result.isOk(),
                                    result.error()));
                });
        return outcomes;
    }

    private static String sourceIdOf(InstrumentEntity instrument) {
        return instrument.getSourceId() != null ? instrument.getSourceId() : instrument.getSymbol();
    }

    private void store(UUID instrumentId, PriceResult result) {
        transactions.executeWithoutResult(
                status -> {
                    PriceEntity row =
                            prices.findById(instrumentId)
                                    .orElseGet(() -> new PriceEntity(instrumentId));
                    if (result.isOk()) {
                        row.recordFeedPrice(
                                result.price(),
                                result.prevPrice(),
                                result.priceDate(),
                                Instant.now(clock));
                    } else {
                        row.recordFeedError(result.error());
                    }
                    prices.save(row);
                });
    }
}
