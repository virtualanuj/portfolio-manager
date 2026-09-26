package com.portfoliomanager.application;

import com.portfoliomanager.domain.AllocationRow;
import java.math.BigDecimal;
import java.util.List;

/** The allocation screen's data. There are deliberately no trade suggestions (NG-2). */
public record AllocationView(BigDecimal totalValue, boolean targetsSet, List<AllocationRow> rows) {}
