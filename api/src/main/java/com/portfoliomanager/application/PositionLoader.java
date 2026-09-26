package com.portfoliomanager.application;

import com.portfoliomanager.domain.Txn;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import java.util.List;
import java.util.UUID;
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
