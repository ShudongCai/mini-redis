package dev.learning.storage.inmemory;

import dev.learning.exception.WrongTypeException;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySetStorageTest {
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
}
