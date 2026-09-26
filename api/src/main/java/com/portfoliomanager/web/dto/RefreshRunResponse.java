package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.RefreshResultItem;
import com.portfoliomanager.application.RefreshRunView;
import com.portfoliomanager.domain.RefreshStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A refresh run and, once finished, what happened to each instrument. */
public record RefreshRunResponse(
        UUID id,
        RefreshStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<RefreshResultItem> results) {

    public static RefreshRunResponse from(RefreshRunView view) {
        return new RefreshRunResponse(
                view.id(), view.status(), view.startedAt(), view.finishedAt(), view.results());
    }
}
