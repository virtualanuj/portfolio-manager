package com.portfoliomanager.application;

import com.portfoliomanager.domain.TxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Fields for creating or replacing a transaction; which of them apply depends on {@code type}. */
public record TransactionCommand(
        UUID accountId,
        UUID instrumentId,
        TxnType type,
        LocalDate tradeDate,
        BigDecimal quantity,
        BigDecimal unitPrice,
        Integer splitNumerator,
        Integer splitDenominator,
        String note) {}
