package dev.learning.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs fixed-delay maintenance tasks on one worker and waits for it to stop on close.
 */
public class BackgroundScheduler implements AutoCloseable{
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public ScheduledFuture<?> scheduledWithFixedDelay(
            Runnable task,
            long initialDelay,
            long delay,
            TimeUnit unit
    ) {
        return executor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    @Override
    public void close() {
        executor.close();
    }
}
