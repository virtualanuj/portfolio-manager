package com.portfoliomanager.application;

import com.portfoliomanager.domain.TxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionView(
        UUID id,
        long seq,
        UUID accountId,
        String accountName,
        UUID instrumentId,
        String symbol,
        TxnType type,
        LocalDate tradeDate,
        BigDecimal quantity,
        BigDecimal unitPrice,
        Integer splitNumerator,
        Integer splitDenominator,
        String note) {}
