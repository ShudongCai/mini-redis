package dev.learning.storage;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.SortedSetEntry;
import dev.learning.storage.model.StoredEntry;
import dev.learning.storage.model.redisValue.SortedSetValue;
import dev.learning.storage.valueStorage.SortedSetStorage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SortedSetStorageTest {
    private static final RedisBytes KEY = bytes("players");

    private final StorageContext context = new StorageContext(() -> 0L);
    private final SortedSetStorage storage = new SortedSetStorage(context);

    @Test
    void shouldAddNewMembersWhenKeyDoesNotExist() {
        RedisBytes alice = bytes("alice");
        RedisBytes bob = bytes("bob");

        assertEquals(2L, storage.zadd(KEY, Map.of(alice, 2.5, bob, 1.0)));

        assertSortedSet(
                Map.of(alice, 2.5, bob, 1.0),
                List.of(new SortedSetEntry(bob, 1.0), new SortedSetEntry(alice, 2.5)));
    }

    @Test
    void shouldAddNewMembersWhenSortedSetAlreadyExists() {
        RedisBytes alice = bytes("alice");
        RedisBytes bob = bytes("bob");
        RedisBytes carol = bytes("carol");
        storage.zadd(KEY, Map.of(alice, 2.0));

        assertEquals(2L, storage.zadd(KEY, Map.of(bob, 1.0, carol, 3.0)));

        assertSortedSet(
                Map.of(alice, 2.0, bob, 1.0, carol, 3.0),
                List.of(new SortedSetEntry(bob, 1.0), new SortedSetEntry(alice, 2.0),
                        new SortedSetEntry(carol, 3.0)));
    }

    @Test
    void shouldUpdateScoreWhenMemberAlreadyExists() {
        RedisBytes alice = bytes("alice");
        RedisBytes bob = bytes("bob");
        storage.zadd(KEY, Map.of(alice, 1.0, bob, 2.0));

        assertEquals(0L, storage.zadd(KEY, Map.of(alice, 3.0)));

        assertSortedSet(
                Map.of(alice, 3.0, bob, 2.0),
                List.of(new SortedSetEntry(bob, 2.0), new SortedSetEntry(alice, 3.0)));
    }

    @Test
    void shouldPreserveMembersWhenScoresAreEqual() {
        RedisBytes alice = bytes("alice");
        RedisBytes bob = bytes("bob");
        RedisBytes carol = bytes("carol");

        assertEquals(2L, storage.zadd(KEY, Map.of(alice, 1.0, carol, 1.0)));
        assertEquals(1L, storage.zadd(KEY, Map.of(bob, 1.0)));

        assertSortedSet(
                Map.of(alice, 1.0, bob, 1.0, carol, 1.0),
                List.of(new SortedSetEntry(alice, 1.0), new SortedSetEntry(bob, 1.0),
                        new SortedSetEntry(carol, 1.0)));
    }

    @Test
    void shouldFindScoreUsingEquivalentMemberBytes() {
        RedisBytes member = new RedisBytes(new byte[]{0, (byte) 0xff});
        storage.zadd(KEY, Map.of(member, -2.5));

        assertEquals(Optional.of(-2.5), storage.zscore(KEY, new RedisBytes(member.getBytes())));
    }

    @Test
    void shouldRemoveMembersFromBothSortedSetIndexes() {
        RedisBytes alice = bytes("alice");
        RedisBytes bob = bytes("bob");
        RedisBytes carol = bytes("carol");
        storage.zadd(KEY, Map.of(alice, 1.0, bob, 1.0, carol, 3.0));

        assertEquals(1L, storage.zrem(KEY, List.of(alice, alice, bytes("missing"))));

        assertSortedSet(
                Map.of(bob, 1.0, carol, 3.0),
                List.of(new SortedSetEntry(bob, 1.0), new SortedSetEntry(carol, 3.0)));
    }

    @Test
    void shouldResolveNegativeZrangeIndexesAndClampToBounds() {
        RedisBytes alice = bytes("alice");
        RedisBytes bob = bytes("bob");
        RedisBytes carol = bytes("carol");
        storage.zadd(KEY, Map.of(alice, 2.0, bob, 2.0, carol, 1.0));

        assertEquals(List.of(alice, bob), storage.zrange(KEY, -2, -1));
        assertEquals(List.of(carol, alice, bob), storage.zrange(KEY, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(List.of(), storage.zrange(KEY, 3, 8));
    }

    private void assertSortedSet(Map<RedisBytes, Double> expectedScores, List<SortedSetEntry> expectedOrder) {
        StoredEntry entry = context.entries().get(KEY);
        assertNotNull(entry, "Expected ZADD to store the key");
        SortedSetValue value = assertInstanceOf(SortedSetValue.class, entry.value());
        assertEquals(expectedScores, value.score());
        assertEquals(expectedOrder, List.copyOf(value.sortedEntry()));
    }

    private static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
