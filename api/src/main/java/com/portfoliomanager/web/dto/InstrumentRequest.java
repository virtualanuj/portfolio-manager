package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.InstrumentCommand;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.pricing.PriceSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InstrumentRequest(
        @NotBlank @Size(max = 30) String symbol,
        @Size(max = 200) String name,
        @NotNull AssetType assetType,
        PriceSource priceSource,
        @Size(max = 100) String sourceId) {

    public InstrumentCommand toCommand() {
        return new InstrumentCommand(symbol, name, assetType, priceSource, sourceId);
    }
}
