package com.portfoliomanager.domain;

/** Lifecycle of a price refresh run; only one run may be RUNNING at a time. */
public enum RefreshStatus {
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED
}
