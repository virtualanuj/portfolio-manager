package com.portfoliomanager.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Portfolio totals for one date. Read-only from Java; rows are written by the upsert query. */
@Entity
@Table(name = "snapshot")
public class SnapshotEntity {

    @Id
    @Column(name = "snap_date")
    private LocalDate snapDate;

    @Column(name = "total_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "total_cost_basis", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalCostBasis;

    @Column(name = "priced_positions", nullable = false)
    private int pricedPositions;

    @Column(name = "stale_positions", nullable = false)
    private int stalePositions;

    @Column(name = "unpriced_positions", nullable = false)
    private int unpricedPositions;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SnapshotEntity() {}

    public LocalDate getSnapDate() {
        return snapDate;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getTotalCostBasis() {
        return totalCostBasis;
    }

    public int getPricedPositions() {
        return pricedPositions;
    }

    public int getStalePositions() {
        return stalePositions;
    }

    public int getUnpricedPositions() {
        return unpricedPositions;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
