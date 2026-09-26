package com.portfoliomanager.application;

import com.portfoliomanager.domain.RefreshStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefreshRunView(
        UUID id,
        RefreshStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<RefreshResultItem> results) {}
