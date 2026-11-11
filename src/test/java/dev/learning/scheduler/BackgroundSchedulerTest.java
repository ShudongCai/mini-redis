package dev.learning.scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundSchedulerTest {
    @Test
    void shouldExecuteTaskRepeatedlyWhenScheduledWithFixedDelay() throws Exception {
        CountDownLatch executions = new CountDownLatch(3);

        try (BackgroundScheduler scheduler = new BackgroundScheduler()) {
            scheduler.scheduledWithFixedDelay(executions::countDown, 0, 10, MILLISECONDS);

            assertTrue(executions.await(5, SECONDS), "Expected the task to run at least three times");
        }
    }

    @Test
    void shouldKeepOtherTasksRunningWhenOneTaskIsCancelled() throws InterruptedException {
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch secondTaskStarted = new CountDownLatch(1);
        CountDownLatch executionsAfterCancellation = new CountDownLatch(2);

        try (BackgroundScheduler scheduler = new BackgroundScheduler()) {
            ScheduledFuture<?> firstTask = scheduler.scheduledWithFixedDelay(
                    firstTaskStarted::countDown, 0, 10, MILLISECONDS);
            scheduler.scheduledWithFixedDelay(() -> {
                if (firstTask.isCancelled()) {
                    executionsAfterCancellation.countDown();
                } else {
                    secondTaskStarted.countDown();
                }
            }, 0, 10, MILLISECONDS);

            assertTrue(firstTaskStarted.await(5, SECONDS), "Expected the first task to start");
            assertTrue(secondTaskStarted.await(5, SECONDS), "Expected the second task to start");
            assertTrue(firstTask.cancel(false), "Expected the first task to be cancelled");
            assertTrue(executionsAfterCancellation.await(5, SECONDS),
                    "Expected the second task to run at least twice after cancellation");
        }
    }

    @Test
    void shouldStopExecutingTasksWhenSchedulerIsClosed() throws Exception {
        CountDownLatch firstExecution = new CountDownLatch(1);
        BackgroundScheduler scheduler = new BackgroundScheduler();
        ScheduledFuture<?> task;

        try (scheduler) {
            task = scheduler.scheduledWithFixedDelay(firstExecution::countDown, 0, 10, MILLISECONDS);
            assertTrue(firstExecution.await(5, SECONDS), "Expected the task to run before closing the scheduler");
        }

        assertTrue(task.isCancelled(), "Closing the scheduler should cancel the recurring task");
        assertThrows(RejectedExecutionException.class,
                () -> scheduler.scheduledWithFixedDelay(firstExecution::countDown, 0, 10, MILLISECONDS));
    }
}
