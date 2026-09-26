package com.portfoliomanager.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/** Runs each refresh on its own virtual thread; the request that started it returns at once. */
@Component
public class VirtualThreadRefreshRunner implements RefreshRunner {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void run(Runnable task) {
        executor.execute(task);
    }
}
