package com.portfoliomanager.web.dto;

import com.portfoliomanager.domain.AssetType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** One target, used for both the request and the response of {@code /api/allocation/targets}. */
public record TargetAllocationDto(@NotNull AssetType assetType, @NotNull BigDecimal targetPct) {}
