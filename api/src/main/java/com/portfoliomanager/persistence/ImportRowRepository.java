package com.portfoliomanager.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowRepository extends JpaRepository<ImportRowEntity, ImportRowId> {

    List<ImportRowEntity> findByBatchIdOrderByLineNo(UUID batchId);
}
