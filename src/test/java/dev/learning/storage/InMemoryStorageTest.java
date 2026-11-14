package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.ListValue;
import dev.learning.storage.model.StoredEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import java.util.stream.IntStream;

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
    void shouldCreateListWhenKeyDoesNotExist() throws ReflectiveOperationException {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(2L, storage.rpush("items", List.of("A", "B")));
        assertEquals(List.of("A", "B"), readListValues(storage, "items"));
    }

    @Test
    void shouldPrependElementsWhenLpushIsCalled() throws ReflectiveOperationException {
        InMemoryStorage storage = new InMemoryStorage();
        assertEquals(1L, storage.rpush("items", List.of("tail")));

        assertEquals(4L, storage.lpush("items", List.of("A", "B", "C")));
        assertEquals(List.of("C", "B", "A", "tail"), readListValues(storage, "items"));
    }

    @Test
    void shouldAppendElementsWhenRpushIsCalled() throws ReflectiveOperationException {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(3L, storage.rpush("items", List.of("A", "B", "C")));
        assertEquals(List.of("A", "B", "C"), readListValues(storage, "items"));

        assertEquals(4L, storage.rpush("items", List.of("D")));
        assertEquals(List.of("A", "B", "C", "D"), readListValues(storage, "items"));
    }

    @Test
    void shouldPreserveExpirationWhenPushingToExistingList() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertEquals(1L, storage.rpush("items", List.of("B")));
        assertEquals(1, storage.expire("items", 10));
        clock.advanceMillis(4_000);

        assertEquals(2L, storage.lpush("items", List.of("A")));
        assertEquals(6L, storage.ttl("items"));
        assertEquals(3L, storage.rpush("items", List.of("C")));
        assertEquals(6L, storage.ttl("items"));

        clock.advanceMillis(6_000);
        assertFalse(storage.exists("items"));
    }

    @Test
    void shouldRejectPushWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        assertTrue(storage.set("items", "original"));

        assertThrows(WrongTypeException.class, () -> storage.lpush("items", List.of("A")));
        assertEquals(Optional.of("original"), storage.get("items"));
        assertThrows(WrongTypeException.class, () -> storage.rpush("items", List.of("B")));
        assertEquals(Optional.of("original"), storage.get("items"));
    }

    @Test
    void shouldCreateNewListWhenPreviousValueHasExpired() throws ReflectiveOperationException {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("items", "expired string", SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        assertEquals(3L, storage.lpush("items", List.of("A", "B", "C")));
        assertEquals(List.of("C", "B", "A"), readListValues(storage, "items"));
        assertEquals(-1L, storage.ttl("items"));
    }

    @Test
    void shouldReturnFirstElementWhenLpopIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("items", List.of("A", "B", "C"));

        assertEquals(Optional.of("A"), storage.lpop("items"));
        assertEquals(Optional.of("B"), storage.lpop("items"));
    }

    @Test
    void shouldReturnLastElementWhenRpopIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("items", List.of("A", "B", "C"));

        assertEquals(Optional.of("C"), storage.rpop("items"));
        assertEquals(Optional.of("B"), storage.rpop("items"));
    }

    @Test
    void shouldRemoveKeyWhenLastElementIsPopped() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("left", List.of("A"));
        storage.rpush("right", List.of("B"));

        assertEquals(Optional.of("A"), storage.lpop("left"));
        assertFalse(storage.exists("left"));
        assertEquals(Optional.of("B"), storage.rpop("right"));
        assertFalse(storage.exists("right"));
    }

    @Test
    void shouldReturnEmptyWhenPoppingMissingKey() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(Optional.empty(), storage.lpop("missing"));
        assertEquals(Optional.empty(), storage.rpop("missing"));
        assertFalse(storage.exists("missing"));
    }

    @Test
    void shouldReturnWrongTypeWhenPoppingStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("items", "original");

        assertThrows(WrongTypeException.class, () -> storage.lpop("items"));
        assertThrows(WrongTypeException.class, () -> storage.rpop("items"));
        assertEquals(Optional.of("original"), storage.get("items"));
    }

    @Test
    void shouldReturnCurrentListLengthWhenLlenIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("items", List.of("A", "B", "C"));

        assertEquals(3, storage.llen("items"));
        assertEquals(Optional.of("A"), storage.lpop("items"));
        assertEquals(2, storage.llen("items"));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyDoesNotExist() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(0, storage.llen("missing"));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        storage.rpush("items", List.of("A", "B"));
        assertEquals(1, storage.expire("items", 1));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.llen("items"));
    }

    @Test
    void shouldRejectLlenWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("items", "original");

        assertThrows(WrongTypeException.class, () -> storage.llen("items"));
        assertEquals(Optional.of("original"), storage.get("items"));
    }

    @Test
    void shouldReturnElementsIncludingBothLrangeEndpoints() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("items", List.of("A", "B", "C", "D"));

        assertEquals(List.of("B", "C"), storage.lrange("items", 1, 2));
        assertEquals(List.of("A", "B", "C", "D"), storage.lrange("items", 0, 3));
    }

    @Test
    void shouldResolveNegativeLrangeIndexesAndClampToListBounds() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("items", List.of("A", "B", "C", "D"));

        assertEquals(List.of("B", "C"), storage.lrange("items", -3, -2));
        assertEquals(List.of("C", "D"), storage.lrange("items", -2, -1));
        assertEquals(List.of("A", "B", "C", "D"),
                storage.lrange("items", Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    void shouldReturnEmptyWhenLrangeSelectsNoElements() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush("items", List.of("A", "B", "C"));

        assertEquals(List.of(), storage.lrange("missing", 0, -1));
        assertEquals(List.of(), storage.lrange("items", 3, 10));
        assertEquals(List.of(), storage.lrange("items", 2, 1));
        assertEquals(List.of(), storage.lrange("items", 0, -4));
    }

    @Test
    void shouldReturnEmptyWhenLrangeKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        storage.rpush("items", List.of("A", "B"));
        assertEquals(1, storage.expire("items", 1));
        clock.advanceMillis(1_000);

        assertEquals(List.of(), storage.lrange("items", 0, -1));
    }

    @Test
    void shouldRejectLrangeWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("items", "original");

        assertThrows(WrongTypeException.class, () -> storage.lrange("items", 0, -1));
        assertEquals(Optional.of("original"), storage.get("items"));
    }

    private static List<String> readListValues(InMemoryStorage storage, String key)
            throws ReflectiveOperationException {
        // Storage has no list read operation yet. Snapshot the deque for order assertions.
        Field dataField = InMemoryStorage.class.getDeclaredField("data");
        dataField.setAccessible(true);
        Map<?, ?> entries = (Map<?, ?>) dataField.get(storage);
        StoredEntry entry = assertInstanceOf(StoredEntry.class, entries.get(key));
        ListValue list = assertInstanceOf(ListValue.class, entry.value());
        return List.copyOf(list.values());
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
    void shouldReturnMinusTwoForMissingTtlKey() {
        InMemoryStorage storage = new StorageTestClock().newStorage();

        assertEquals(-2L, storage.ttl("missing"));
    }

    @Test
    void shouldReturnMinusOneForPersistentTtlKey() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice"));
        clock.advanceMillis(DAYS.toMillis(1));

        assertEquals(-1L, storage.ttl("name"));
    }

    @ParameterizedTest(name = "{0} ms remaining returns {1}")
    @CsvSource({"10000, 10", "9600, 10", "9400, 9", "400, 0", "0, -2"})
    void shouldReturnTtlForExpiringKey(long remainingMillis, long expectedSeconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(10L).build()));
        clock.advanceMillis(10_000 - remainingMillis);

        assertEquals(expectedSeconds, storage.ttl("name"));
    }

    @Test
    void shouldReturnZeroWhenExpireKeyIsMissing() {
        InMemoryStorage storage = new StorageTestClock().newStorage();

        assertEquals(0, storage.expire("missing", 10));
        assertEquals(-2L, storage.ttl("missing"));
    }

    @ParameterizedTest(name = "EXPIRE {0} on an expired key returns zero")
    @ValueSource(longs = {10, 0})
    void shouldReturnZeroWhenExpireKeyIsAlreadyExpired(long seconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.expire("name", seconds));
        assertEquals(-2L, storage.ttl("name"));
    }

    @Test
    void shouldSetExpirationOnPersistentKeyWithoutChangingItsValue() {
        InMemoryStorage storage = new StorageTestClock().newStorage();
        assertTrue(storage.set("name", "Alice"));

        assertEquals(1, storage.expire("name", 10));
        assertEquals(10L, storage.ttl("name"));
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldReplaceExistingExpirationRelativeToCurrentTime() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(10L).build()));
        clock.advanceMillis(4_000);

        assertEquals(1, storage.expire("name", 20));
        assertEquals(20L, storage.ttl("name"));
        clock.advanceMillis(6_000);
        assertEquals(Optional.of("Alice"), storage.get("name"));
        clock.advanceMillis(14_000);
        assertEquals(-2L, storage.ttl("name"));
    }

    @ParameterizedTest(name = "EXPIRE {0} deletes a live key")
    @ValueSource(longs = {0, -5})
    void shouldDeleteLiveKeyWhenExpireTimeIsNonPositive(long seconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(10L).build()));

        assertEquals(1, storage.expire("name", seconds));
        assertEquals(Optional.empty(), storage.get("name"));
    }

    @ParameterizedTest(name = "EXPIRE {0} preserves the entry on overflow")
    @ValueSource(longs = {Long.MAX_VALUE, Long.MAX_VALUE / 1_000})
    void shouldPreserveValueAndDeadlineWhenExpireOverflows(long seconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        clock.advanceMillis(1_000);
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(10L).build()));

        assertThrows(ArithmeticException.class, () -> storage.expire("name", seconds));
        assertEquals(10L, storage.ttl("name"));
        clock.advanceMillis(9_999);
        assertEquals(Optional.of("Alice"), storage.get("name"));
        clock.advanceMillis(1);
        assertEquals(-2L, storage.ttl("name"));
    }

    @Test
    void shouldRemoveExpirationWhenKeyHasTtl() {
        InMemoryStorage storage = new StorageTestClock().newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(10L).build()));
        assertEquals(10L, storage.ttl("name"));

        assertEquals(1, storage.persist("name"));
        assertEquals(-1L, storage.ttl("name"));
    }

    @Test
    void shouldReturnZeroWhenKeyHasNoExpiration() {
        InMemoryStorage storage = new StorageTestClock().newStorage();
        assertTrue(storage.set("name", "Alice"));

        assertEquals(0, storage.persist("name"));
        assertEquals(-1L, storage.ttl("name"));
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldReturnZeroWhenKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.persist("name"));
        assertEquals(-2L, storage.ttl("name"));
    }

    @Test
    void shouldPreserveValueAfterExpirationIsRemoved() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set("name", "Alice", SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(500);

        assertEquals(1, storage.persist("name"));
        clock.advanceMillis(501);

        assertEquals(Optional.of("Alice"), storage.get("name"));
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
    void shouldPreserveAllElementsWhenPushingConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int elementsPerWorker = 64;
        List<String> expected = IntStream.range(0, workers * elementsPerWorker)
                .mapToObj(Integer::toString)
                .toList();
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<Void>> pushes = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                int first = worker * elementsPerWorker;
                boolean pushLeft = worker % 2 == 0;
                pushes.add(executor.submit(() -> {
                    start.await(5, SECONDS);
                    for (String value : expected.subList(first, first + elementsPerWorker)) {
                        if (pushLeft) {
                            storage.lpush("items", List.of(value));
                        } else {
                            storage.rpush("items", List.of(value));
                        }
                    }
                    return null;
                }));
            }
            for (Future<Void> push : pushes) {
                push.get(10, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<String> actual = storage.lrange("items", 0, -1);
        assertEquals(expected.size(), actual.size());
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    void shouldPopEachElementAtMostOnceWhenPoppingConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int popsPerWorker = 64;
        List<String> expected = IntStream.range(0, workers * popsPerWorker)
                .mapToObj(Integer::toString)
                .toList();
        storage.rpush("items", expected);
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<List<String>>> pops = new ArrayList<>();
        List<String> popped = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                boolean popLeft = worker % 2 == 0;
                pops.add(executor.submit(() -> {
                    start.await(5, SECONDS);
                    List<String> values = new ArrayList<>();
                    for (int attempt = 0; attempt < popsPerWorker; attempt++) {
                        Optional<String> value = popLeft ? storage.lpop("items") : storage.rpop("items");
                        value.ifPresent(values::add);
                    }
                    return values;
                }));
            }
            for (Future<List<String>> pop : pops) {
                popped.addAll(pop.get(10, SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(expected.size(), popped.size());
        assertEquals(new HashSet<>(expected), new HashSet<>(popped));
        assertFalse(storage.exists("items"));
    }

    @Test
    void shouldReturnConsistentSnapshotWhenLrangeRunsConcurrentlyWithPush() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 128;
        int batchSize = 8;
        List<String> expected = IntStream.range(0, (rounds + 1) * batchSize)
                .mapToObj(Integer::toString)
                .toList();
        storage.rpush("items", expected.subList(0, batchSize));
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> pushes = executor.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    int first = round * batchSize;
                    startRound.await(5, SECONDS);
                    storage.rpush("items", expected.subList(first, first + batchSize));
                }
                return null;
            });
            Future<Void> reads = executor.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    int beforeSize = round * batchSize;
                    int afterSize = beforeSize + batchSize;
                    startRound.await(5, SECONDS);
                    List<String> snapshot = storage.lrange("items", 0, -1);

                    // The next batch waits at the barrier until this snapshot is checked.
                    assertTrue(snapshot.size() == beforeSize || snapshot.size() == afterSize,
                            "Round " + round + " returned a partial batch");
                    assertEquals(expected.subList(0, snapshot.size()), snapshot, "Round " + round);
                }
                return null;
            });

            reads.get(10, SECONDS);
            pushes.get(10, SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(expected, storage.lrange("items", 0, -1));
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
