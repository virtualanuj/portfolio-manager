package com.portfoliomanager.importing;

import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TxnType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A validated row in typed form. Trades carry quantity and price, splits carry the ratio; the
 * unused fields are null (numerator and denominator are 0).
 */
public record ParsedRow(
        LocalDate date,
        String accountName,
        AccountType accountType,
        String symbol,
        AssetType assetType,
        TxnType type,
        BigDecimal quantity,
        BigDecimal unitPrice,
        int splitNumerator,
        int splitDenominator,
        String sourceId,
        String note) {}
