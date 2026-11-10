package dev.learning.storage;

import dev.learning.command.SetOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class InMemoryStorageTest {
    @Test
    void shouldStoreValueWhenKeyIsNew() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Alice", storage.get("name").get());
    }

    @Test
    void shouldOverwriteValueWhenKeyAlreadyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        storage.set("name", "Ben");

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Ben", storage.get("name").get());
    }

    @Test
    void shouldPreserveSpacesInValue() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice And Ben");

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Alice And Ben", storage.get("name").get());
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act

        //Assert
        assertTrue(storage.get("name").isEmpty());
    }

    @Test
    void shouldReturnValueWhenKeyHasNotExpired() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));

        now.addAndGet(999);

        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldReturnEmptyWhenKeyHasExpired() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));

        now.addAndGet(1_000);

        assertEquals(Optional.empty(), storage.get("name"));
    }

    @Test
    void shouldReturnValueWhenKeyHasNoExpiration() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set("name", "Alice");

        now.addAndGet(DAYS.toMillis(1));

        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldClearExpirationWhenExistingKeyIsOverwritten() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        now.addAndGet(500);

        storage.set("name", "Ben");
        now.addAndGet(1_000);

        assertEquals(Optional.of("Ben"), storage.get("name"));
    }

    @Test
    void shouldReturnZeroWhenExpiredKeyIsDeleted() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        now.addAndGet(1_000);

        assertEquals(0, storage.del("name"));
    }

    @Test
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("name", "Alice");

        // Act
        int deletedCount = storage.del("name");

        // Assert
        assertEquals(1, deletedCount);
        assertFalse(storage.exists("name"));
    }

    @Test
    void shouldReturnZeroWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        int value = storage.del("hello");

        //Assert
        assertEquals(0, value);
    }

    @Test
    void shouldReturnTrueWhenKeyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        boolean value = storage.exists("name");

        //Assert
        assertTrue(value);
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        boolean value = storage.exists("hello");

        //Assert
        assertFalse(value);
    }

    @Test
    void shouldStoreEmptyString() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();

        // Act
        storage.set("name", "");

        // Assert
        assertEquals(Optional.of(""), storage.get("name"));
    }

    @Test
    void shouldSetValueWhenKeyDoesNotExistWithNx() {
        InMemoryStorage storage = new InMemoryStorage();

        boolean written = storage.set("name", "Alice", SetOptions.builder().nx().build());

        assertTrue(written);
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldNotOverwriteValueWhenKeyExistsWithNx() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("name", "Alice");

        boolean written = storage.set("name", "Bob", SetOptions.builder().nx().build());

        assertFalse(written);
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldOverwriteValueWhenKeyExistsWithXx() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("name", "Alice");

        boolean written = storage.set("name", "Bob", SetOptions.builder().xx().build());

        assertTrue(written);
        assertEquals(Optional.of("Bob"), storage.get("name"));
    }

    @Test
    void shouldNotSetValueWhenKeyDoesNotExistWithXx() {
        InMemoryStorage storage = new InMemoryStorage();

        boolean written = storage.set("name", "Alice", SetOptions.builder().xx().build());

        assertFalse(written);
        assertEquals(Optional.empty(), storage.get("name"));
    }

    @Test
    void shouldSetValueWhenKeyHasExpiredWithNx() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        boolean written = storage.set("name", "Bob", SetOptions.builder().nx().build());

        assertTrue(written);
        assertEquals(Optional.of("Bob"), storage.get("name"));
    }

    @Test
    void shouldNotSetValueWhenKeyHasExpiredWithXx() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        boolean written = storage.set("name", "Bob", SetOptions.builder().xx().build());

        assertFalse(written);
        assertEquals(Optional.empty(), storage.get("name"));
    }

    @Test
    void shouldNotMissKeyWhenGetOverlapsOverwriteAtExpiration() throws Exception {
        assertReadSurvivesOverwrite(storage -> storage.get("key").isPresent());
    }

    @Test
    void shouldNotMissKeyWhenExistsOverlapsOverwriteAtExpiration() throws Exception {
        assertReadSurvivesOverwrite(storage -> storage.exists("key"));
    }

    @Test
    void shouldStartSetExpirationAfterWaitingForConcurrentDelete() throws Exception {
        PausedClock clock = new PausedClock();
        InMemoryStorage storage = new InMemoryStorage(clock);
        assertTrue(storage.set("key", "old", SetOptions.builder().ttlSeconds(10L).build()));

        FutureTask<Integer> delete = new FutureTask<>(() -> storage.del("key"));
        clock.pauseOn(delete);
        FutureTask<Boolean> write = new FutureTask<>(
                () -> storage.set("key", "new", SetOptions.builder().nx().ttlSeconds(1L).build()));
        try {
            clock.awaitPause();
            Thread writer = Thread.ofPlatform().daemon().start(write);
            awaitBlockedOrDone(writer, write);
            assertFalse(write.isDone(), "SET should wait for DEL on the same key");
            clock.now.set(1_000);
        } finally {
            clock.resume.countDown();
        }

        assertEquals(1, delete.get(5, SECONDS));
        assertTrue(write.get(5, SECONDS));
        clock.now.set(1_999);
        assertEquals(Optional.of("new"), storage.get("key"));
        clock.now.set(2_000);
        assertEquals(Optional.empty(), storage.get("key"));
    }

    private static void assertReadSurvivesOverwrite(Function<InMemoryStorage, Boolean> readKey)
            throws Exception {
        PausedClock clock = new PausedClock();
        InMemoryStorage storage = new InMemoryStorage(clock);
        assertTrue(storage.set("key", "old", SetOptions.builder().ttlSeconds(1L).build()));
        clock.now.set(999);

        FutureTask<Boolean> read = new FutureTask<>(() -> readKey.apply(storage));
        clock.pauseOn(read);
        FutureTask<Boolean> overwrite = new FutureTask<>(() -> storage.set("key", "new"));
        try {
            clock.awaitPause();
            Thread writer = Thread.ofPlatform().daemon().start(overwrite);
            awaitBlockedOrDone(writer, overwrite);
            if (overwrite.isDone()) {
                // An unlocked read now holds a snapshot that has expired since replacement.
                clock.now.set(1_000);
            }
        } finally {
            clock.resume.countDown();
        }

        assertTrue(read.get(5, SECONDS), "The key was live throughout the read");
        assertTrue(overwrite.get(5, SECONDS));
        clock.now.set(1_000);
        assertEquals(Optional.of("new"), storage.get("key"));
    }

    private static void awaitBlockedOrDone(Thread thread, FutureTask<?> task) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && !task.isDone()) {
            if (System.nanoTime() - deadline >= 0) {
                fail("Writer did not reach the map operation");
            }
            Thread.sleep(1);
        }
    }

    private static final class PausedClock implements LongSupplier {
        private final AtomicLong now = new AtomicLong();
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private Thread pausedThread;

        private void pauseOn(FutureTask<?> task) {
            pausedThread = Thread.ofPlatform().daemon().unstarted(task);
            pausedThread.start();
        }

        private void awaitPause() throws InterruptedException {
            assertTrue(paused.await(5, SECONDS), "Worker did not reach the expiration check");
        }

        @Override
        public long getAsLong() {
            if (Thread.currentThread() == pausedThread) {
                paused.countDown();
                try {
                    assertTrue(resume.await(10, SECONDS), "Test did not resume the clock");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return now.get();
        }
    }

    @Test
    void shouldStoreAllValuesWhenDifferentKeysAreSetConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int keysPerWorker = 250;
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<Void>> writes = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                int firstKey = worker * keysPerWorker;
                writes.add(executor.submit(() -> {
                    start.await(10, SECONDS);
                    for (int key = firstKey; key < firstKey + keysPerWorker; key++) {
                        storage.set("key-" + key, "value-" + key);
                    }
                    return null;
                }));
            }
            for (Future<Void> write : writes) {
                write.get(10, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        for (int key = 0; key < workers * keysPerWorker; key++) {
            assertEquals(Optional.of("value-" + key), storage.get("key-" + key), "key-" + key);
        }
    }

    @Test
    void shouldNotFailWhenGetAndDelRunConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 2_000;
        for (int round = 0; round < rounds; round++) {
            storage.set("key-" + round, "value");
        }
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> reads = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    String key = "key-" + round;
                    startRound.await(10, SECONDS);
                    storage.get(key).ifPresent(value -> assertEquals("value", value));
                }
                return null;
            });
            Future<Void> deletes = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    String key = "key-" + round;
                    startRound.await(10, SECONDS);
                    assertEquals(1, storage.del(key), key);
                }
                return null;
            });

            reads.get(10, SECONDS);
            deletes.get(10, SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
