package com.portfoliomanager.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
        extends JpaRepository<TransactionEntity, UUID>,
                JpaSpecificationExecutor<TransactionEntity> {

    boolean existsByAccountId(UUID accountId);

    boolean existsByInstrumentId(UUID instrumentId);

    List<TransactionEntity> findByAccountIdAndInstrumentId(UUID accountId, UUID instrumentId);
}
