package dev.learning.storage.inmemory;

import dev.learning.command.SetOptions;
import dev.learning.exception.WrongTypeException;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.learning.storage.StorageTestSupport.byteList;
import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryListStorageTest {
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
}
