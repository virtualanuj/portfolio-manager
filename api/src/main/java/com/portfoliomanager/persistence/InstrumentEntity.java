package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.pricing.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "instrument",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "asset_type"}))
public class InstrumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", nullable = false)
    private PriceSource priceSource;

    @Column(name = "source_id")
    private String sourceId;

    protected InstrumentEntity() {}

    public InstrumentEntity(
            String symbol,
            String name,
            AssetType assetType,
            PriceSource priceSource,
            String sourceId) {
        this.symbol = symbol;
        this.name = name;
        this.assetType = assetType;
        this.priceSource = priceSource;
        this.sourceId = sourceId;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }

    public void setPriceSource(PriceSource priceSource) {
        this.priceSource = priceSource;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
}
