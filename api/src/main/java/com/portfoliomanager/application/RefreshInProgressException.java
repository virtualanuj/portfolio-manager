package com.portfoliomanager.application;

import java.util.UUID;

/** A refresh is already running (HTTP 409); carries its run id so the caller can follow it. */
public class RefreshInProgressException extends ConflictException {

    private final UUID runId;

    public RefreshInProgressException(UUID runId) {
        super("A refresh is already running");
        this.runId = runId;
    }

    public UUID getRunId() {
        return runId;
    }
}
