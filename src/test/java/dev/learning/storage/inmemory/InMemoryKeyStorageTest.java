package dev.learning.storage.inmemory;

import dev.learning.command.SetOptions;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static dev.learning.storage.StorageTestSupport.bytes;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKeyStorageTest {
    @Test
    void shouldReturnZeroWhenExpiredKeyIsDeleted() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

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
}
