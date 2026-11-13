package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.scheduler.BackgroundScheduler;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveExpirationIntegrationTest {
    @Test
    void shouldRemoveExpiredEntriesWhenSchedulerRuns() throws InterruptedException {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("expires-first", "first", SetOptions.builder().ttlSeconds(1L).build()));
        assertTrue(storage.set("expires-second", "second", SetOptions.builder().ttlSeconds(2L).build()));
        assertTrue(storage.set("still-live", "live", SetOptions.builder().ttlSeconds(10L).build()));
        assertTrue(storage.set("persistent", "kept"));
        clock.advanceMillis(2_000);

        Runnable cleanupTask = storage.expirationCleanupTask();
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
        assertEquals(Optional.empty(), storage.get("expires-first"));
        assertEquals(Optional.empty(), storage.get("expires-second"));
        assertEquals(Optional.of("live"), storage.get("still-live"));
        assertEquals(Optional.of("kept"), storage.get("persistent"));
    }
}
