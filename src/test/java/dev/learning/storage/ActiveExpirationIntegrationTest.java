package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.scheduler.BackgroundScheduler;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveExpirationIntegrationTest {
    @Test
    void shouldRemoveExpiredEntriesWhenSchedulerRuns() throws InterruptedException {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("expires-first"), bytes("first"), SetOptions.builder().ttlSeconds(1L).build()));
        assertTrue(storage.set(bytes("expires-second"), bytes("second"), SetOptions.builder().ttlSeconds(2L).build()));
        assertTrue(storage.set(bytes("still-live"), bytes("live"), SetOptions.builder().ttlSeconds(10L).build()));
        assertTrue(storage.set(bytes("persistent"), bytes("kept")));
        clock.advanceMillis(2_000);

        ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock(true);
        Runnable cleanupTask = storage.expirationCleanupTask(snapshotLock);
        CountDownLatch cleanupCompleted = new CountDownLatch(1);
        try (BackgroundScheduler scheduler = new BackgroundScheduler()) {
            scheduler.scheduledWithFixedDelay(() -> {
                cleanupTask.run();
                cleanupCompleted.countDown();
            }, 0, 10, MILLISECONDS);

            assertTrue(cleanupCompleted.await(5, SECONDS), "Expected the scheduled cleanup to finish");
        }

        // Rewind so get() would return the expired entries if they were still stored.
        clock.advanceMillis(-2_000);
        assertEquals(Optional.empty(), storage.get(bytes("expires-first")));
        assertEquals(Optional.empty(), storage.get(bytes("expires-second")));
        assertEquals(Optional.of(bytes("live")), storage.get(bytes("still-live")));
        assertEquals(Optional.of(bytes("kept")), storage.get(bytes("persistent")));
    }

    @Test
    void shouldWaitForSnapshotWriteLockBeforeCleaningExpiredEntries() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock(true);
        FutureTask<Void> cleanup = new FutureTask<>(storage.expirationCleanupTask(snapshotLock), null);
        Thread worker = Thread.ofVirtual().unstarted(cleanup);

        snapshotLock.writeLock().lock();
        try {
            worker.start();
            long deadline = System.nanoTime() + SECONDS.toNanos(5);
            while (!snapshotLock.hasQueuedThread(worker) && !cleanup.isDone() && System.nanoTime() - deadline < 0) {
                Thread.sleep(1);
            }
            assertTrue(snapshotLock.hasQueuedThread(worker), "Cleanup should wait for SAVE to release the lock");
        } finally {
            snapshotLock.writeLock().unlock();
        }

        cleanup.get(5, SECONDS);
    }

    private static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
