package com.portfoliomanager.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record SnapshotHoldingId(LocalDate snapDate, UUID accountId, UUID instrumentId)
        implements Serializable {}
