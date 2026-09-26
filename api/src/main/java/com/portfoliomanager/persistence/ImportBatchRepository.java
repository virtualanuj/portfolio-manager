package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.ImportBatchStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, UUID> {

    List<ImportBatchEntity> findByStatusAndCreatedAtBefore(
            ImportBatchStatus status, Instant cutoff);
}
