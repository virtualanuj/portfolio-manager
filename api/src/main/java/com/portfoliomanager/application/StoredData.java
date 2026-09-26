package com.portfoliomanager.application;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.Txn;
import com.portfoliomanager.importing.ExistingData;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A snapshot of stored accounts, instruments and transactions, keyed by name for import checks. */
final class StoredData implements ExistingData {

    private final Set<String> accountNames = new HashSet<>();
    private final Set<String> instrumentKeys = new HashSet<>();
    private final Map<String, List<Txn>> transactionsByPosition = new HashMap<>();

    static StoredData load(
            AccountRepository accounts,
            InstrumentRepository instruments,
            PositionLoader positions) {
        StoredData data = new StoredData();
        Map<UUID, String> accountNameById = new HashMap<>();
        for (AccountEntity account : accounts.findAll()) {
            data.accountNames.add(account.getName());
            accountNameById.put(account.getId(), account.getName());
        }
        Map<UUID, InstrumentEntity> instrumentById = new HashMap<>();
        for (InstrumentEntity instrument : instruments.findAll()) {
            data.instrumentKeys.add(
                    instrumentKey(instrument.getSymbol(), instrument.getAssetType()));
            instrumentById.put(instrument.getId(), instrument);
        }
        positions
                .loadAll()
                .forEach(
                        (key, txns) -> {
                            InstrumentEntity instrument = instrumentById.get(key.instrumentId());
                            data.transactionsByPosition.put(
                                    positionKey(
                                            accountNameById.get(key.accountId()),
                                            instrument.getSymbol(),
                                            instrument.getAssetType()),
                                    txns);
                        });
        return data;
    }

    @Override
    public boolean accountExists(String name) {
        return accountNames.contains(name);
    }

    @Override
    public boolean instrumentExists(String symbol, AssetType assetType) {
        return instrumentKeys.contains(instrumentKey(symbol, assetType));
    }

    @Override
    public List<Txn> transactionsOf(String account, String symbol, AssetType assetType) {
        return transactionsByPosition.getOrDefault(
                positionKey(account, symbol, assetType), List.of());
    }

    private static String instrumentKey(String symbol, AssetType type) {
        return symbol + "/" + type;
    }

    private static String positionKey(String account, String symbol, AssetType type) {
        return account + "|" + symbol + "|" + type;
    }
}
