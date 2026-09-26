package com.portfoliomanager.application;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.pricing.PriceSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstrumentView(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        PriceSource priceSource,
        String sourceId,
        BigDecimal manualPrice,
        LocalDate manualAsOf) {}
