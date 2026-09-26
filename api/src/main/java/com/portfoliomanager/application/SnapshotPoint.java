package com.portfoliomanager.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One point of the value-over-time history. */
public record SnapshotPoint(LocalDate date, BigDecimal totalValue, BigDecimal totalCostBasis) {}
