package com.portfoliomanager.persistence;

import com.portfoliomanager.pricing.PriceFetchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Latest price data for one instrument. A failed fetch changes only {@code status} and {@code
 * lastError}; {@code price}, {@code priceDate} and {@code fetchedAt} keep the last good values.
 */
@Entity
@Table(name = "price")
public class PriceEntity {

    private static final int PRICE_SCALE = 8;

    @Id
    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(precision = 24, scale = 8)
    private BigDecimal price;

    @Column(name = "prev_price", precision = 24, scale = 8)
    private BigDecimal prevPrice;

    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceFetchStatus status = PriceFetchStatus.OK;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "manual_price", precision = 24, scale = 8)
    private BigDecimal manualPrice;

    @Column(name = "manual_as_of")
    private LocalDate manualAsOf;

    protected PriceEntity() {}

    public PriceEntity(UUID instrumentId) {
        this.instrumentId = instrumentId;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getPrevPrice() {
        return prevPrice;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public PriceFetchStatus getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public BigDecimal getManualPrice() {
        return manualPrice;
    }

    public LocalDate getManualAsOf() {
        return manualAsOf;
    }

    public void setManualPrice(BigDecimal manualPrice, LocalDate asOf) {
        this.manualPrice = manualPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        this.manualAsOf = asOf;
    }

    public void clearManualPrice() {
        this.manualPrice = null;
        this.manualAsOf = null;
    }

    public void recordFeedPrice(
            BigDecimal price, BigDecimal prevPrice, LocalDate priceDate, Instant fetchedAt) {
        this.price = price;
        this.prevPrice = prevPrice;
        this.priceDate = priceDate;
        this.fetchedAt = fetchedAt;
        this.status = PriceFetchStatus.OK;
        this.lastError = null;
    }

    public void recordFeedError(String message) {
        this.status = PriceFetchStatus.ERROR;
        this.lastError = message;
    }
}
