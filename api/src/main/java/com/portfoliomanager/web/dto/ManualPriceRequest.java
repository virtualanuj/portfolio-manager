package com.portfoliomanager.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualPriceRequest(
        @NotNull @PositiveOrZero BigDecimal price, @NotNull LocalDate asOf) {}
