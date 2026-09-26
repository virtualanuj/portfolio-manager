package com.portfoliomanager.application;

import java.util.UUID;

/** Identifies one position: an instrument held in an account. */
public record PositionKey(UUID accountId, UUID instrumentId) {}
