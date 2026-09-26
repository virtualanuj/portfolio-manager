package com.portfoliomanager.importing;

/** How a staged row will be treated; the most severe applicable status is reported. */
public enum RowStatus {
    OK,
    WILL_CREATE,
    WARNING,
    ERROR
}
