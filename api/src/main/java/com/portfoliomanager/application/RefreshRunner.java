package com.portfoliomanager.application;

/** Runs a refresh in the background. Tests replace it to control when the worker runs. */
@FunctionalInterface
public interface RefreshRunner {

    void run(Runnable task);
}
