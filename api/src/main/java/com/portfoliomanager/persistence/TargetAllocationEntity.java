package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "target_allocation")
public class TargetAllocationEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetType assetType;

    @Column(name = "target_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetPct;

    protected TargetAllocationEntity() {}

    public TargetAllocationEntity(AssetType assetType, BigDecimal targetPct) {
        this.assetType = assetType;
        this.targetPct = targetPct;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public BigDecimal getTargetPct() {
        return targetPct;
    }
}
