package dev.learning.storage.inmemory;

import dev.learning.command.SetOptions;
import dev.learning.exception.WrongTypeException;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySortedSetStorageTest {
    @Test
    void shouldCreateSortedSetWhenZaddReplacesExpiredString() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        RedisBytes key = bytes("players");
        RedisBytes member = bytes("alice");
        storage.set(key, bytes("expired"), SetOptions.builder().ttlSeconds(1L).build());
        clock.advanceMillis(1_000);

        assertEquals(1L, storage.zadd(key, Map.of(member, 2.5)));

        assertEquals(Optional.of(2.5), storage.zscore(key, member));
        assertEquals(-1L, storage.ttl(key));
    }

    @Test
    void shouldReturnEmptyScoreWhenSortedSetHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        RedisBytes key = bytes("players");
        RedisBytes member = bytes("alice");
        storage.zadd(key, Map.of(member, 2.5));
        assertEquals(1, storage.expire(key, 1));
        clock.advanceMillis(1_000);

        assertEquals(Optional.empty(), storage.zscore(key, member));
    }

    @Test
    void shouldRemoveKeyWhenZremRemovesLastMember() {
        InMemoryStorage storage = new InMemoryStorage();
        RedisBytes key = bytes("players");
        RedisBytes member = bytes("alice");
        storage.zadd(key, Map.of(member, 2.5));

        assertEquals(1L, storage.zrem(key, List.of(member)));

        assertFalse(storage.exists(key));
    }

    @Test
    void shouldRejectZrangeWhenKeyContainsStringValue() {
        InMemoryStorage storage = new InMemoryStorage();
        RedisBytes key = bytes("players");
        storage.set(key, bytes("original"));

        assertThrows(WrongTypeException.class, () -> storage.zrange(key, 0, -1));
        assertEquals(Optional.of(bytes("original")), storage.get(key));
    }
}
