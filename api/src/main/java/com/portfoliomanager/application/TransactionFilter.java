package com.portfoliomanager.application;

import com.portfoliomanager.domain.TxnType;
import java.time.LocalDate;
import java.util.UUID;

/** Optional list filters; a null field does not filter. */
public record TransactionFilter(
        UUID accountId, UUID instrumentId, TxnType type, LocalDate from, LocalDate to) {}
