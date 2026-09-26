package com.portfoliomanager.persistence;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnapshotHoldingRepository
        extends JpaRepository<SnapshotHoldingEntity, SnapshotHoldingId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SnapshotHoldingEntity h where h.snapDate = :date")
    void deleteBySnapDate(@Param("date") LocalDate date);
}
