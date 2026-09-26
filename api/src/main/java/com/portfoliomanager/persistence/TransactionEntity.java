package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.TxnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A row of the {@code transaction} table. {@code seq} is assigned by the database on insert and
 * gives a stable replay order within a trade date.
 */
@Entity
@Table(name = "transaction")
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxnType type;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 24, scale = 8)
    private BigDecimal unitPrice;

    @Column(name = "split_numerator")
    private Integer splitNumerator;

    @Column(name = "split_denominator")
    private Integer splitDenominator;

    private String note;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TransactionEntity() {}

    public static TransactionEntity trade(
            UUID accountId,
            UUID instrumentId,
            TxnType type,
            LocalDate tradeDate,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String note) {
        TransactionEntity entity = new TransactionEntity();
        entity.accountId = accountId;
        entity.instrumentId = instrumentId;
        entity.type = type;
        entity.tradeDate = tradeDate;
        entity.quantity = quantity;
        entity.unitPrice = unitPrice;
        entity.note = note;
        return entity;
    }

    public static TransactionEntity split(
            UUID accountId,
            UUID instrumentId,
            LocalDate tradeDate,
            int numerator,
            int denominator,
            String note) {
        TransactionEntity entity = new TransactionEntity();
        entity.accountId = accountId;
        entity.instrumentId = instrumentId;
        entity.type = TxnType.SPLIT;
        entity.tradeDate = tradeDate;
        entity.splitNumerator = numerator;
        entity.splitDenominator = denominator;
        entity.note = note;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(UUID instrumentId) {
        this.instrumentId = instrumentId;
    }

    public TxnType getType() {
        return type;
    }

    public void setType(TxnType type) {
        this.type = type;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getSplitNumerator() {
        return splitNumerator;
    }

    public void setSplitNumerator(Integer splitNumerator) {
        this.splitNumerator = splitNumerator;
    }

    public Integer getSplitDenominator() {
        return splitDenominator;
    }

    public void setSplitDenominator(Integer splitDenominator) {
        this.splitDenominator = splitDenominator;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public UUID getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(UUID importBatchId) {
        this.importBatchId = importBatchId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
