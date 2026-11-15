package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
        storage.set(bytes("name"), bytes("Alice"));

        //Assert
        assertTrue(storage.get(bytes("name")).isPresent());
        assertEquals(bytes("Alice"), storage.get(bytes("name")).get());
    }

    @Test
    void shouldOverwriteValueWhenKeyAlreadyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice"));
        storage.set(bytes("name"), bytes("Ben"));

        //Assert
        assertTrue(storage.get(bytes("name")).isPresent());
        assertEquals(bytes("Ben"), storage.get(bytes("name")).get());
    }

    @Test
    void shouldPreserveSpacesInValue() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice And Ben"));

        //Assert
        assertTrue(storage.get(bytes("name")).isPresent());
        assertEquals(bytes("Alice And Ben"), storage.get(bytes("name")).get());
    }

    @Test
    void shouldCreateListWhenKeyDoesNotExist() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(2L, storage.rpush(bytes("items"), byteList("A", "B")));
        assertEquals(byteList("A", "B"), storage.lrange(bytes("items"), 0, -1));
    }

    @Test
    void shouldPrependElementsWhenLpushIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        assertEquals(1L, storage.rpush(bytes("items"), byteList("tail")));

        assertEquals(4L, storage.lpush(bytes("items"), byteList("A", "B", "C")));
        assertEquals(byteList("C", "B", "A", "tail"), storage.lrange(bytes("items"), 0, -1));
    }

    @Test
    void shouldAppendElementsWhenRpushIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(3L, storage.rpush(bytes("items"), byteList("A", "B", "C")));
        assertEquals(byteList("A", "B", "C"), storage.lrange(bytes("items"), 0, -1));

        assertEquals(4L, storage.rpush(bytes("items"), byteList("D")));
        assertEquals(byteList("A", "B", "C", "D"), storage.lrange(bytes("items"), 0, -1));
    }

    @Test
    void shouldPreserveExpirationWhenPushingToExistingList() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertEquals(1L, storage.rpush(bytes("items"), byteList("B")));
        assertEquals(1, storage.expire(bytes("items"), 10));
        clock.advanceMillis(4_000);

        assertEquals(2L, storage.lpush(bytes("items"), byteList("A")));
        assertEquals(6L, storage.ttl(bytes("items")));
        assertEquals(3L, storage.rpush(bytes("items"), byteList("C")));
        assertEquals(6L, storage.ttl(bytes("items")));

        clock.advanceMillis(6_000);
        assertFalse(storage.exists(bytes("items")));
    }

    @Test
    void shouldRejectPushWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        assertTrue(storage.set(bytes("items"), bytes("original")));

        assertThrows(WrongTypeException.class, () -> storage.lpush(bytes("items"), byteList("A")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
        assertThrows(WrongTypeException.class, () -> storage.rpush(bytes("items"), byteList("B")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldCreateNewListWhenPreviousValueHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("items"), bytes("expired string"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        assertEquals(3L, storage.lpush(bytes("items"), byteList("A", "B", "C")));
        assertEquals(byteList("C", "B", "A"), storage.lrange(bytes("items"), 0, -1));
        assertEquals(-1L, storage.ttl(bytes("items")));
    }

    @Test
    void shouldReturnFirstElementWhenLpopIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertEquals(Optional.of(bytes("A")), storage.lpop(bytes("items")));
        assertEquals(Optional.of(bytes("B")), storage.lpop(bytes("items")));
    }

    @Test
    void shouldReturnLastElementWhenRpopIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertEquals(Optional.of(bytes("C")), storage.rpop(bytes("items")));
        assertEquals(Optional.of(bytes("B")), storage.rpop(bytes("items")));
    }

    @Test
    void shouldRemoveKeyWhenLastElementIsPopped() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("left"), byteList("A"));
        storage.rpush(bytes("right"), byteList("B"));

        assertEquals(Optional.of(bytes("A")), storage.lpop(bytes("left")));
        assertFalse(storage.exists(bytes("left")));
        assertEquals(Optional.of(bytes("B")), storage.rpop(bytes("right")));
        assertFalse(storage.exists(bytes("right")));
    }

    @Test
    void shouldReturnEmptyWhenPoppingMissingKey() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(Optional.empty(), storage.lpop(bytes("missing")));
        assertEquals(Optional.empty(), storage.rpop(bytes("missing")));
        assertFalse(storage.exists(bytes("missing")));
    }

    @Test
    void shouldReturnWrongTypeWhenPoppingStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("items"), bytes("original"));

        assertThrows(WrongTypeException.class, () -> storage.lpop(bytes("items")));
        assertThrows(WrongTypeException.class, () -> storage.rpop(bytes("items")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldReturnCurrentListLengthWhenLlenIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertEquals(3, storage.llen(bytes("items")));
        assertEquals(Optional.of(bytes("A")), storage.lpop(bytes("items")));
        assertEquals(2, storage.llen(bytes("items")));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyDoesNotExist() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(0, storage.llen(bytes("missing")));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        storage.rpush(bytes("items"), byteList("A", "B"));
        assertEquals(1, storage.expire(bytes("items"), 1));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.llen(bytes("items")));
    }

    @Test
    void shouldRejectLlenWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("items"), bytes("original"));

        assertThrows(WrongTypeException.class, () -> storage.llen(bytes("items")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldReturnElementsIncludingBothLrangeEndpoints() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("items"), byteList("A", "B", "C", "D"));

        assertEquals(byteList("B", "C"), storage.lrange(bytes("items"), 1, 2));
        assertEquals(byteList("A", "B", "C", "D"), storage.lrange(bytes("items"), 0, 3));
    }

    @Test
    void shouldResolveNegativeLrangeIndexesAndClampToListBounds() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("items"), byteList("A", "B", "C", "D"));

        assertEquals(byteList("B", "C"), storage.lrange(bytes("items"), -3, -2));
        assertEquals(byteList("C", "D"), storage.lrange(bytes("items"), -2, -1));
        assertEquals(byteList("A", "B", "C", "D"),
                storage.lrange(bytes("items"), Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    void shouldReturnEmptyWhenLrangeSelectsNoElements() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertEquals(List.of(), storage.lrange(bytes("missing"), 0, -1));
        assertEquals(List.of(), storage.lrange(bytes("items"), 3, 10));
        assertEquals(List.of(), storage.lrange(bytes("items"), 2, 1));
        assertEquals(List.of(), storage.lrange(bytes("items"), 0, -4));
    }

    @Test
    void shouldReturnEmptyWhenLrangeKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        storage.rpush(bytes("items"), byteList("A", "B"));
        assertEquals(1, storage.expire(bytes("items"), 1));
        clock.advanceMillis(1_000);

        assertEquals(List.of(), storage.lrange(bytes("items"), 0, -1));
    }

    @Test
    void shouldRejectLrangeWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("items"), bytes("original"));

        assertThrows(WrongTypeException.class, () -> storage.lrange(bytes("items"), 0, -1));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act

        //Assert
        assertTrue(storage.get(bytes("name")).isEmpty());
    }

    @Test
    void shouldReturnValueWhenKeyHasNotExpired() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));

        now.addAndGet(999);

        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnEmptyWhenKeyHasExpired() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));

        now.addAndGet(1_000);

        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnValueWhenKeyHasNoExpiration() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set(bytes("name"), bytes("Alice"));

        now.addAndGet(DAYS.toMillis(1));

        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldClearExpirationWhenExistingKeyIsOverwritten() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        now.addAndGet(500);

        storage.set(bytes("name"), bytes("Ben"));
        now.addAndGet(1_000);

        assertEquals(Optional.of(bytes("Ben")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenExpiredKeyIsDeleted() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        now.addAndGet(1_000);

        assertEquals(0, storage.del(bytes("name")));
    }

    @Test
    void shouldReturnMinusTwoForMissingTtlKey() {
        InMemoryStorage storage = new StorageTestClock().newStorage();

        assertEquals(-2L, storage.ttl(bytes("missing")));
    }

    @Test
    void shouldReturnMinusOneForPersistentTtlKey() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice")));
        clock.advanceMillis(DAYS.toMillis(1));

        assertEquals(-1L, storage.ttl(bytes("name")));
    }

    @ParameterizedTest(name = "{0} ms remaining returns {1}")
    @CsvSource({"10000, 10", "9600, 10", "9400, 9", "400, 0", "0, -2"})
    void shouldReturnTtlForExpiringKey(long remainingMillis, long expectedSeconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build()));
        clock.advanceMillis(10_000 - remainingMillis);

        assertEquals(expectedSeconds, storage.ttl(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenExpireKeyIsMissing() {
        InMemoryStorage storage = new StorageTestClock().newStorage();

        assertEquals(0, storage.expire(bytes("missing"), 10));
        assertEquals(-2L, storage.ttl(bytes("missing")));
    }

    @ParameterizedTest(name = "EXPIRE {0} on an expired key returns zero")
    @ValueSource(longs = {10, 0})
    void shouldReturnZeroWhenExpireKeyIsAlreadyExpired(long seconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.expire(bytes("name"), seconds));
        assertEquals(-2L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldSetExpirationOnPersistentKeyWithoutChangingItsValue() {
        InMemoryStorage storage = new StorageTestClock().newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice")));

        assertEquals(1, storage.expire(bytes("name"), 10));
        assertEquals(10L, storage.ttl(bytes("name")));
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldReplaceExistingExpirationRelativeToCurrentTime() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build()));
        clock.advanceMillis(4_000);

        assertEquals(1, storage.expire(bytes("name"), 20));
        assertEquals(20L, storage.ttl(bytes("name")));
        clock.advanceMillis(6_000);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
        clock.advanceMillis(14_000);
        assertEquals(-2L, storage.ttl(bytes("name")));
    }

    @ParameterizedTest(name = "EXPIRE {0} deletes a live key")
    @ValueSource(longs = {0, -5})
    void shouldDeleteLiveKeyWhenExpireTimeIsNonPositive(long seconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build()));

        assertEquals(1, storage.expire(bytes("name"), seconds));
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @ParameterizedTest(name = "EXPIRE {0} preserves the entry on overflow")
    @ValueSource(longs = {Long.MAX_VALUE, Long.MAX_VALUE / 1_000})
    void shouldPreserveValueAndDeadlineWhenExpireOverflows(long seconds) {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        clock.advanceMillis(1_000);
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build()));

        assertThrows(ArithmeticException.class, () -> storage.expire(bytes("name"), seconds));
        assertEquals(10L, storage.ttl(bytes("name")));
        clock.advanceMillis(9_999);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
        clock.advanceMillis(1);
        assertEquals(-2L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldRemoveExpirationWhenKeyHasTtl() {
        InMemoryStorage storage = new StorageTestClock().newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build()));
        assertEquals(10L, storage.ttl(bytes("name")));

        assertEquals(1, storage.persist(bytes("name")));
        assertEquals(-1L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenKeyHasNoExpiration() {
        InMemoryStorage storage = new StorageTestClock().newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice")));

        assertEquals(0, storage.persist(bytes("name")));
        assertEquals(-1L, storage.ttl(bytes("name")));
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.persist(bytes("name")));
        assertEquals(-2L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldPreserveValueAfterExpirationIsRemoved() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(500);

        assertEquals(1, storage.persist(bytes("name")));
        clock.advanceMillis(501);

        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("name"), bytes("Alice"));

        // Act
        int deletedCount = storage.del(bytes("name"));

        // Assert
        assertEquals(1, deletedCount);
        assertFalse(storage.exists(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice"));
        int value = storage.del(bytes("hello"));

        //Assert
        assertEquals(0, value);
    }

    @Test
    void shouldReturnTrueWhenKeyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice"));
        boolean value = storage.exists(bytes("name"));

        //Assert
        assertTrue(value);
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice"));
        boolean value = storage.exists(bytes("hello"));

        //Assert
        assertFalse(value);
    }

    @Test
    void shouldStoreEmptyString() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();

        // Act
        storage.set(bytes("name"), bytes(""));

        // Assert
        assertEquals(Optional.of(bytes("")), storage.get(bytes("name")));
    }

    @Test
    void shouldSetValueWhenKeyDoesNotExistWithNx() {
        InMemoryStorage storage = new InMemoryStorage();

        boolean written = storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().nx().build());

        assertTrue(written);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldNotOverwriteValueWhenKeyExistsWithNx() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("name"), bytes("Alice"));

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().nx().build());

        assertFalse(written);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldOverwriteValueWhenKeyExistsWithXx() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("name"), bytes("Alice"));

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().xx().build());

        assertTrue(written);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldNotSetValueWhenKeyDoesNotExistWithXx() {
        InMemoryStorage storage = new InMemoryStorage();

        boolean written = storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().xx().build());

        assertFalse(written);
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldSetValueWhenKeyHasExpiredWithNx() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().nx().build());

        assertTrue(written);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldNotSetValueWhenKeyHasExpiredWithXx() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().xx().build());

        assertFalse(written);
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldNotMissKeyWhenGetOverlapsOverwriteAtExpiration() throws Exception {
        assertReadSurvivesOverwrite(storage -> storage.get(bytes("key")).isPresent());
    }

    @Test
    void shouldNotMissKeyWhenExistsOverlapsOverwriteAtExpiration() throws Exception {
        assertReadSurvivesOverwrite(storage -> storage.exists(bytes("key")));
    }

    @Test
    void shouldStartSetExpirationAfterWaitingForConcurrentDelete() throws Exception {
        PausedClock clock = new PausedClock();
        InMemoryStorage storage = new InMemoryStorage(clock);
        assertTrue(storage.set(bytes("key"), bytes("old"), SetOptions.builder().ttlSeconds(10L).build()));

        FutureTask<Integer> delete = new FutureTask<>(() -> storage.del(bytes("key")));
        clock.pauseOn(delete);
        FutureTask<Boolean> write = new FutureTask<>(
                () -> storage.set(bytes("key"), bytes("new"), SetOptions.builder().nx().ttlSeconds(1L).build()));
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
        assertEquals(Optional.of(bytes("new")), storage.get(bytes("key")));
        clock.now.set(2_000);
        assertEquals(Optional.empty(), storage.get(bytes("key")));
    }

    private static void assertReadSurvivesOverwrite(Function<InMemoryStorage, Boolean> readKey)
            throws Exception {
        PausedClock clock = new PausedClock();
        InMemoryStorage storage = new InMemoryStorage(clock);
        assertTrue(storage.set(bytes("key"), bytes("old"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.now.set(999);

        FutureTask<Boolean> read = new FutureTask<>(() -> readKey.apply(storage));
        clock.pauseOn(read);
        FutureTask<Boolean> overwrite = new FutureTask<>(() -> storage.set(bytes("key"), bytes("new")));
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
        assertEquals(Optional.of(bytes("new")), storage.get(bytes("key")));
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
        List<RedisBytes> expected = IntStream.range(0, workers * elementsPerWorker)
                .mapToObj(index -> bytes(Integer.toString(index)))
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
                    for (RedisBytes value : expected.subList(first, first + elementsPerWorker)) {
                        if (pushLeft) {
                            storage.lpush(bytes("items"), List.of(value));
                        } else {
                            storage.rpush(bytes("items"), List.of(value));
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

        List<RedisBytes> actual = storage.lrange(bytes("items"), 0, -1);
        assertEquals(expected.size(), actual.size());
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    void shouldPopEachElementAtMostOnceWhenPoppingConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int popsPerWorker = 64;
        List<RedisBytes> expected = IntStream.range(0, workers * popsPerWorker)
                .mapToObj(index -> bytes(Integer.toString(index)))
                .toList();
        storage.rpush(bytes("items"), expected);
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<List<RedisBytes>>> pops = new ArrayList<>();
        List<RedisBytes> popped = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                boolean popLeft = worker % 2 == 0;
                pops.add(executor.submit(() -> {
                    start.await(5, SECONDS);
                    List<RedisBytes> values = new ArrayList<>();
                    for (int attempt = 0; attempt < popsPerWorker; attempt++) {
                        Optional<RedisBytes> value = popLeft ? storage.lpop(bytes("items")) : storage.rpop(bytes("items"));
                        value.ifPresent(values::add);
                    }
                    return values;
                }));
            }
            for (Future<List<RedisBytes>> pop : pops) {
                popped.addAll(pop.get(10, SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(expected.size(), popped.size());
        assertEquals(new HashSet<>(expected), new HashSet<>(popped));
        assertFalse(storage.exists(bytes("items")));
    }

    @Test
    void shouldReturnConsistentSnapshotWhenLrangeRunsConcurrentlyWithPush() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 128;
        int batchSize = 8;
        List<RedisBytes> expected = IntStream.range(0, (rounds + 1) * batchSize)
                .mapToObj(index -> bytes(Integer.toString(index)))
                .toList();
        storage.rpush(bytes("items"), expected.subList(0, batchSize));
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> pushes = executor.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    int first = round * batchSize;
                    startRound.await(5, SECONDS);
                    storage.rpush(bytes("items"), expected.subList(first, first + batchSize));
                }
                return null;
            });
            Future<Void> reads = executor.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    int beforeSize = round * batchSize;
                    int afterSize = beforeSize + batchSize;
                    startRound.await(5, SECONDS);
                    List<RedisBytes> snapshot = storage.lrange(bytes("items"), 0, -1);

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

        assertEquals(expected, storage.lrange(bytes("items"), 0, -1));
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
                        storage.set(bytes("key-" + key), bytes("value-" + key));
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
            assertEquals(Optional.of(bytes("value-" + key)), storage.get(bytes("key-" + key)), "key-" + key);
        }
    }

    @Test
    void shouldNotFailWhenGetAndDelRunConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 2_000;
        for (int round = 0; round < rounds; round++) {
            storage.set(bytes("key-" + round), bytes("value"));
        }
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> reads = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    RedisBytes key = bytes("key-" + round);
                    startRound.await(10, SECONDS);
                    storage.get(key).ifPresent(value -> assertEquals(bytes("value"), value));
                }
                return null;
            });
            Future<Void> deletes = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    RedisBytes key = bytes("key-" + round);
                    startRound.await(10, SECONDS);
                    assertEquals(1, storage.del(key), "key-" + round);
                }
                return null;
            });

            reads.get(10, SECONDS);
            deletes.get(10, SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldFindBinaryMemberWhenSismemberIsCalled() {
        InMemoryStorage storage = new InMemoryStorage();
        RedisBytes member = new RedisBytes(new byte[]{0, (byte) 0xff});
        storage.sadd(bytes("members"), List.of(member));

        assertEquals(1, storage.sismember(bytes("members"), new RedisBytes(member.getBytes())));
        assertEquals(0, storage.sismember(bytes("members"), new RedisBytes(new byte[]{0, (byte) 0xfe})));
    }

    @Test
    void shouldReturnZeroWhenSismemberKeyDoesNotExist() {
        InMemoryStorage storage = new InMemoryStorage();

        assertEquals(0, storage.sismember(bytes("missing"), bytes("A")));
        assertFalse(storage.exists(bytes("missing")));
    }

    @Test
    void shouldReturnZeroWhenSismemberKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        RedisBytes key = bytes("members");
        RedisBytes member = bytes("A");
        storage.sadd(key, List.of(member));
        assertEquals(1, storage.expire(key, 1));
        clock.advanceMillis(1_000);

        assertEquals(0, storage.sismember(key, member));
    }

    @Test
    void shouldRejectSismemberWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        RedisBytes key = bytes("members");
        storage.set(key, bytes("original"));

        assertThrows(WrongTypeException.class, () -> storage.sismember(key, bytes("A")));
        assertEquals(Optional.of(bytes("original")), storage.get(key));
    }

    private static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<RedisBytes> byteList(String... values) {
        return Arrays.stream(values).map(InMemoryStorageTest::bytes).toList();
    }
}
