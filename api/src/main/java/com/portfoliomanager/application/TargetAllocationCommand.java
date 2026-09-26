package com.portfoliomanager.application;

import com.portfoliomanager.domain.AssetType;
import java.math.BigDecimal;

public record TargetAllocationCommand(AssetType assetType, BigDecimal targetPct) {}
