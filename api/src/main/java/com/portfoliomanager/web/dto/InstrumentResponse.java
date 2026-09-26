package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.InstrumentView;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.pricing.PriceSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstrumentResponse(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        PriceSource priceSource,
        String sourceId,
        BigDecimal manualPrice,
        LocalDate manualAsOf) {

    public static InstrumentResponse from(InstrumentView view) {
        return new InstrumentResponse(
                view.id(),
                view.symbol(),
                view.name(),
                view.assetType(),
                view.priceSource(),
                view.sourceId(),
                view.manualPrice(),
                view.manualAsOf());
    }
}
