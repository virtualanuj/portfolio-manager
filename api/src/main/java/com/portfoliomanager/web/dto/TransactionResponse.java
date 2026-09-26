package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.TransactionView;
import com.portfoliomanager.domain.TxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
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
        String note) {

    public static TransactionResponse from(TransactionView view) {
        return new TransactionResponse(
                view.id(),
                view.accountId(),
                view.accountName(),
                view.instrumentId(),
                view.symbol(),
                view.type(),
                view.tradeDate(),
                view.quantity(),
                view.unitPrice(),
                view.splitNumerator(),
                view.splitDenominator(),
                view.note());
    }
}
