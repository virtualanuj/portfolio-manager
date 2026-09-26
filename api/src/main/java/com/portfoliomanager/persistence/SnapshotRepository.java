package com.portfoliomanager.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnapshotRepository extends JpaRepository<SnapshotEntity, LocalDate> {

    List<SnapshotEntity> findBySnapDateBetweenOrderBySnapDateAsc(LocalDate from, LocalDate to);

    /** Inserts the day's snapshot or overwrites it, so repeated refreshes converge on one row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "INSERT INTO snapshot (snap_date, total_value, total_cost_basis, priced_positions,"
                            + " stale_positions, unpriced_positions, updated_at)"
                            + " VALUES (:date, :totalValue, :totalCostBasis, :priced, :stale, :unpriced,"
                            + " :updatedAt)"
                            + " ON CONFLICT (snap_date) DO UPDATE SET total_value = EXCLUDED.total_value,"
                            + " total_cost_basis = EXCLUDED.total_cost_basis,"
                            + " priced_positions = EXCLUDED.priced_positions,"
                            + " stale_positions = EXCLUDED.stale_positions,"
                            + " unpriced_positions = EXCLUDED.unpriced_positions,"
                            + " updated_at = EXCLUDED.updated_at",
            nativeQuery = true)
    void upsert(
            @Param("date") LocalDate date,
            @Param("totalValue") BigDecimal totalValue,
            @Param("totalCostBasis") BigDecimal totalCostBasis,
            @Param("priced") int priced,
            @Param("stale") int stale,
            @Param("unpriced") int unpriced,
            @Param("updatedAt") Instant updatedAt);
}
