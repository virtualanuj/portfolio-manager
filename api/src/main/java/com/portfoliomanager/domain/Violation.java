package com.portfoliomanager.domain;

import java.util.UUID;

/** A transaction that cannot be applied, for example a sell of more than is held. */
public record Violation(UUID transactionId, String message) {}
