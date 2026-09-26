package com.portfoliomanager.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One position's state on a snapshot date. */
@Entity
@Table(name = "snapshot_holding")
@IdClass(SnapshotHoldingId.class)
public class SnapshotHoldingEntity {

    @Id
    @Column(name = "snap_date")
    private LocalDate snapDate;

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Id
    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(precision = 24, scale = 8)
    private BigDecimal price;

    @Column(precision = 20, scale = 4)
    private BigDecimal value;

    @Column(name = "cost_basis", nullable = false, precision = 20, scale = 4)
    private BigDecimal costBasis;

    @Column(name = "is_stale", nullable = false)
    private boolean stale;

    protected SnapshotHoldingEntity() {}

    public SnapshotHoldingEntity(
            LocalDate snapDate,
            UUID accountId,
            UUID instrumentId,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal value,
            BigDecimal costBasis,
            boolean stale) {
        this.snapDate = snapDate;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.value = value;
        this.costBasis = costBasis;
        this.stale = stale;
    }

    public LocalDate getSnapDate() {
        return snapDate;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }

    public boolean isStale() {
        return stale;
    }
}
