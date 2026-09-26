package com.portfoliomanager.application;

import java.util.UUID;

/** A change would sell more than is held at some point in time (HTTP 422). */
public class OversellException extends RuntimeException {

    private final UUID transactionId;

    public OversellException(String message, UUID transactionId) {
        super(message);
        this.transactionId = transactionId;
    }

    /** The first transaction that cannot be satisfied. */
    public UUID getTransactionId() {
        return transactionId;
    }
}
