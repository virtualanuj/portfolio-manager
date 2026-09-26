package com.portfoliomanager.application;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.pricing.PriceSource;

/** Fields for creating or replacing an instrument; {@code priceSource} defaults by asset type. */
public record InstrumentCommand(
        String symbol,
        String name,
        AssetType assetType,
        PriceSource priceSource,
        String sourceId) {}
