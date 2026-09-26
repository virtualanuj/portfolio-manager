package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.TransactionCommand;
import com.portfoliomanager.domain.TxnType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionRequest(
        @NotNull UUID accountId,
        @NotNull UUID instrumentId,
        @NotNull TxnType type,
        @NotNull LocalDate tradeDate,
        BigDecimal quantity,
        BigDecimal unitPrice,
        Integer splitNumerator,
        Integer splitDenominator,
        @Size(max = 500) String note) {

    public TransactionCommand toCommand() {
        return new TransactionCommand(
                accountId,
                instrumentId,
                type,
                tradeDate,
                quantity,
                unitPrice,
                splitNumerator,
                splitDenominator,
                note);
    }
}
