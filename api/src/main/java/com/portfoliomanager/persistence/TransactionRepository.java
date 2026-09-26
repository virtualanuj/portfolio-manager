package com.portfoliomanager.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    boolean existsByAccountId(UUID accountId);

    boolean existsByInstrumentId(UUID instrumentId);
}
