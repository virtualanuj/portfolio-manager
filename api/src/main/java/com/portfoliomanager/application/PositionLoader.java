package com.portfoliomanager.application;

import com.portfoliomanager.domain.Txn;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Loads a position's transactions from the database as domain {@link Txn}s. */
@Component
public class PositionLoader {

    private final TransactionRepository transactions;

    public PositionLoader(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    public List<Txn> load(UUID accountId, UUID instrumentId) {
        return transactions.findByAccountIdAndInstrumentId(accountId, instrumentId).stream()
                .map(PositionLoader::toTxn)
                .toList();
    }

    /** Loads every transaction once, grouped by position. */
    public Map<PositionKey, List<Txn>> loadAll() {
        return transactions.findAll().stream()
                .collect(
                        Collectors.groupingBy(
                                t -> new PositionKey(t.getAccountId(), t.getInstrumentId()),
                                Collectors.mapping(PositionLoader::toTxn, Collectors.toList())));
    }

    static Txn toTxn(TransactionEntity entity) {
        return new Txn(
                entity.getId(),
                entity.getSeq(),
                entity.getType(),
                entity.getTradeDate(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getSplitNumerator() == null ? 0 : entity.getSplitNumerator(),
                entity.getSplitDenominator() == null ? 0 : entity.getSplitDenominator());
    }
}
