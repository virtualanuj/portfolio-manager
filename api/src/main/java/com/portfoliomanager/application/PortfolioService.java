package com.portfoliomanager.application;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.FifoEngine;
import com.portfoliomanager.domain.PortfolioTotals;
import com.portfoliomanager.domain.Position;
import com.portfoliomanager.domain.PositionValue;
import com.portfoliomanager.domain.PriceInput;
import com.portfoliomanager.domain.PriceState;
import com.portfoliomanager.domain.Txn;
import com.portfoliomanager.domain.Valuation;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.PriceEntity;
import com.portfoliomanager.persistence.PriceRepository;
import com.portfoliomanager.pricing.PriceFacts;
import com.portfoliomanager.pricing.PriceFetchStatus;
import com.portfoliomanager.pricing.PriceSource;
import com.portfoliomanager.pricing.StalenessPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Derives holdings from transactions and prices; nothing here is stored. */
@Service
public class PortfolioService {

    private static final Map<String, SortColumn> SORTABLE = sortableColumns();

    private final PositionLoader positionLoader;
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final PriceRepository prices;
    private final Clock clock;

    public PortfolioService(
            PositionLoader positionLoader,
            AccountRepository accounts,
            InstrumentRepository instruments,
            PriceRepository prices,
            Clock clock) {
        this.positionLoader = positionLoader;
        this.accounts = accounts;
        this.instruments = instruments;
        this.prices = prices;
        this.clock = clock;
    }

    /**
     * Open positions, optionally filtered, sorted by {@code sort}: a column name, prefixed with
     * {@code -} for descending. Positions without a value sort last in either direction.
     */
    @Transactional(readOnly = true)
    public List<HoldingView> holdings(UUID accountId, AssetType assetType, String sort) {
        Comparator<HoldingView> comparator = comparatorFor(sort);
        return allHoldings().stream()
                .filter(h -> accountId == null || h.accountId().equals(accountId))
                .filter(h -> assetType == null || h.position().assetType() == assetType)
                .sorted(comparator)
                .toList();
    }

    /** Totals over the priced open positions, with counts of stale and unpriced ones. */
    @Transactional(readOnly = true)
    public PortfolioTotals totals() {
        return Valuation.totals(allHoldings().stream().map(HoldingView::position).toList());
    }

    /** Every open position (quantity above zero), unsorted. */
    @Transactional(readOnly = true)
    public List<HoldingView> allHoldings() {
        Map<UUID, AccountEntity> accountById = indexById(accounts.findAll(), AccountEntity::getId);
        Map<UUID, InstrumentEntity> instrumentById =
                indexById(instruments.findAll(), InstrumentEntity::getId);
        Map<UUID, PriceEntity> priceById =
                indexById(prices.findAll(), PriceEntity::getInstrumentId);
        LocalDate today = LocalDate.now(clock);

        List<HoldingView> result = new ArrayList<>();
        for (Map.Entry<PositionKey, List<Txn>> entry : positionLoader.loadAll().entrySet()) {
            Position position = FifoEngine.replay(entry.getValue()).position();
            if (position.quantity().signum() == 0) {
                continue;
            }
            AccountEntity account = accountById.get(entry.getKey().accountId());
            InstrumentEntity instrument = instrumentById.get(entry.getKey().instrumentId());
            PriceEntity price = priceById.get(instrument.getId());
            result.add(holding(account, instrument, price, position, today));
        }
        return result;
    }

    private static HoldingView holding(
            AccountEntity account,
            InstrumentEntity instrument,
            PriceEntity price,
            Position position,
            LocalDate today) {
        boolean manualSource = instrument.getPriceSource() == PriceSource.MANUAL;
        PriceFacts facts =
                new PriceFacts(
                        manualSource,
                        price == null ? null : price.getPriceDate(),
                        price != null && price.getStatus() == PriceFetchStatus.ERROR,
                        price == null ? null : price.getManualAsOf());
        BigDecimal effectivePrice =
                price == null ? null : (manualSource ? price.getManualPrice() : price.getPrice());
        PriceState state =
                effectivePrice == null
                        ? PriceState.UNPRICED
                        : StalenessPolicy.stateOf(facts, today);
        BigDecimal previous = manualSource || price == null ? null : price.getPrevPrice();
        PositionValue value =
                Valuation.value(
                        instrument.getAssetType(),
                        position,
                        new PriceInput(effectivePrice, previous, state));
        LocalDate asOf =
                state == PriceState.UNPRICED
                        ? null
                        : (manualSource ? price.getManualAsOf() : price.getPriceDate());
        return new HoldingView(
                account.getId(),
                account.getName(),
                instrument.getId(),
                instrument.getSymbol(),
                position.avgCost().orElse(null),
                asOf,
                value);
    }

    private static <T> Map<UUID, T> indexById(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static Comparator<HoldingView> comparatorFor(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORTABLE.get("symbol").ascending();
        }
        boolean descending = sort.startsWith("-");
        String name = descending ? sort.substring(1) : sort;
        SortColumn column = SORTABLE.get(name);
        if (column == null) {
            throw new ValidationException(
                    "sort",
                    "Unknown sort column %s; use one of %s".formatted(name, SORTABLE.keySet()));
        }
        return descending ? column.descending() : column.ascending();
    }

    /** Ascending and descending orders that both put missing values last. */
    private record SortColumn(
            Comparator<HoldingView> ascending, Comparator<HoldingView> descending) {}

    private static <T extends Comparable<? super T>> SortColumn column(
            Function<HoldingView, T> extractor) {
        return new SortColumn(
                Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder())),
                Comparator.comparing(extractor, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private static Map<String, SortColumn> sortableColumns() {
        Map<String, SortColumn> columns = new LinkedHashMap<>();
        columns.put("symbol", column(h -> h.symbol().toLowerCase()));
        columns.put("account", column(h -> h.accountName().toLowerCase()));
        columns.put("assetType", column(h -> h.position().assetType()));
        columns.put("quantity", column(h -> h.position().quantity()));
        columns.put("avgCost", column(HoldingView::avgCost));
        columns.put("costBasis", column(h -> h.position().costBasis()));
        columns.put("price", column(h -> h.position().price()));
        columns.put("value", column(h -> h.position().value()));
        columns.put("unrealized", column(h -> h.position().unrealized()));
        columns.put("unrealizedPct", column(h -> h.position().unrealizedPct()));
        return columns;
    }
}
