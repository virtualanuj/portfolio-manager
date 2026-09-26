package com.portfoliomanager.importing;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.Txn;
import java.util.List;

/** What the validator needs to know about data already stored; keeps validation testable. */
public interface ExistingData {

    boolean accountExists(String name);

    boolean instrumentExists(String symbol, AssetType assetType);

    /** Existing transactions of one position, in any order; empty if there are none. */
    List<Txn> transactionsOf(String accountName, String symbol, AssetType assetType);
}
