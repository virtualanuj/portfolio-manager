package com.portfoliomanager.application;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.PriceEntity;
import com.portfoliomanager.persistence.PriceRepository;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstrumentService {

    private final InstrumentRepository instruments;
    private final PriceRepository prices;
    private final TransactionRepository transactions;

    public InstrumentService(
            InstrumentRepository instruments,
            PriceRepository prices,
            TransactionRepository transactions) {
        this.instruments = instruments;
        this.prices = prices;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<InstrumentView> list() {
        Map<UUID, PriceEntity> priceById =
                prices.findAll().stream()
                        .collect(
                                Collectors.toMap(
                                        PriceEntity::getInstrumentId, Function.identity()));
        return instruments.findAllByOrderBySymbolAsc().stream()
                .map(i -> view(i, priceById.get(i.getId())))
                .toList();
    }

    @Transactional
    public InstrumentView create(InstrumentCommand command) {
        ResolvedInstrument resolved = resolve(command);
        if (instruments.existsBySymbolAndAssetType(resolved.symbol(), resolved.assetType())) {
            throw duplicate(resolved);
        }
        InstrumentEntity saved =
                instruments.saveAndFlush(
                        new InstrumentEntity(
                                resolved.symbol(),
                                resolved.name(),
                                resolved.assetType(),
                                resolved.priceSource(),
                                resolved.sourceId()));
        return view(saved, null);
    }

    @Transactional
    public InstrumentView update(UUID id, InstrumentCommand command) {
        InstrumentEntity instrument = find(id);
        ResolvedInstrument resolved = resolve(command);
        if (instruments.existsBySymbolAndAssetTypeAndIdNot(
                resolved.symbol(), resolved.assetType(), id)) {
            throw duplicate(resolved);
        }
        instrument.setSymbol(resolved.symbol());
        instrument.setName(resolved.name());
        instrument.setAssetType(resolved.assetType());
        instrument.setPriceSource(resolved.priceSource());
        instrument.setSourceId(resolved.sourceId());
        return view(instrument, prices.findById(id).orElse(null));
    }

    @Transactional
    public void delete(UUID id) {
        InstrumentEntity instrument = find(id);
        if (transactions.existsByInstrumentId(id)) {
            throw new ConflictException(
                    "Instrument %s has transactions and cannot be deleted"
                            .formatted(instrument.getSymbol()));
        }
        try {
            instruments.delete(instrument);
            instruments.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Instrument %s has history and cannot be deleted"
                            .formatted(instrument.getSymbol()));
        }
    }

    @Transactional
    public InstrumentView setManualPrice(UUID id, BigDecimal price, LocalDate asOf) {
        InstrumentEntity instrument = find(id);
        PriceEntity priceRow = prices.findById(id).orElseGet(() -> new PriceEntity(id));
        priceRow.setManualPrice(price, asOf);
        return view(instrument, prices.saveAndFlush(priceRow));
    }

    @Transactional
    public void clearManualPrice(UUID id) {
        find(id);
        prices.findById(id)
                .ifPresent(
                        priceRow -> {
                            priceRow.clearManualPrice();
                            prices.save(priceRow);
                        });
    }

    private InstrumentEntity find(UUID id) {
        return instruments
                .findById(id)
                .orElseThrow(
                        () -> new NotFoundException("Instrument %s does not exist".formatted(id)));
    }

    private static ConflictException duplicate(ResolvedInstrument resolved) {
        return new ConflictException(
                "An instrument %s of type %s already exists"
                        .formatted(resolved.symbol(), resolved.assetType()));
    }

    private record ResolvedInstrument(
            String symbol,
            String name,
            AssetType assetType,
            PriceSource priceSource,
            String sourceId) {}

    private static ResolvedInstrument resolve(InstrumentCommand command) {
        AssetType assetType = command.assetType();
        boolean crypto = assetType == AssetType.CRYPTO;
        PriceSource source =
                command.priceSource() != null
                        ? command.priceSource()
                        : (crypto ? PriceSource.COINGECKO : PriceSource.YAHOO);
        if (crypto && source == PriceSource.YAHOO) {
            throw new ValidationException("priceSource", "Crypto is priced by COINGECKO or MANUAL");
        }
        if (!crypto && source == PriceSource.COINGECKO) {
            throw new ValidationException("priceSource", "Only crypto can be priced by COINGECKO");
        }

        String symbol = command.symbol().trim();
        String sourceId = blankToNull(command.sourceId());
        if (crypto && source == PriceSource.COINGECKO && sourceId == null) {
            throw new ValidationException(
                    "sourceId", "A crypto needs its CoinGecko coin id, for example bitcoin");
        }
        if (source == PriceSource.YAHOO && sourceId == null) {
            sourceId = symbol;
        }
        return new ResolvedInstrument(
                symbol, blankToNull(command.name()), assetType, source, sourceId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static InstrumentView view(InstrumentEntity instrument, PriceEntity price) {
        return new InstrumentView(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getAssetType(),
                instrument.getPriceSource(),
                instrument.getSourceId(),
                price == null ? null : price.getManualPrice(),
                price == null ? null : price.getManualAsOf());
    }
}
