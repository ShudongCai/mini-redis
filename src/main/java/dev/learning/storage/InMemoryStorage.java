package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.persistence.model.SnapshotEntry;
import dev.learning.storage.model.RedisBytes;
import dev.learning.persistence.SnapshotService;
import dev.learning.storage.valueStorage.*;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

/**
 * Delegates type-specific operations over a shared concurrent keyspace.
 */
public class InMemoryStorage {
    private final StringStorage stringStorage;
    private final ListStorage listStorage;
    private final SetStorage setStorage;
    private final KeyStorage keyStorage;
    private final SortedSetStorage sortedSetStorage;
    private final SnapshotService snapshotService;

    public InMemoryStorage() {
        this(System::currentTimeMillis);
    }

    InMemoryStorage(LongSupplier currentTimeMillis) {
        StorageContext context = new StorageContext(currentTimeMillis);
        this.stringStorage = new StringStorage(context);
        this.listStorage = new ListStorage(context);
        this.setStorage = new SetStorage(context);
        this.keyStorage = new KeyStorage(context);
        this.sortedSetStorage = new SortedSetStorage(context);
        this.snapshotService = new SnapshotService(context);
    }

    public Optional<RedisBytes> get(RedisBytes key) {
        return stringStorage.get(key);
    }

    public boolean set(RedisBytes key, RedisBytes value) {
        return stringStorage.set(key, value);
    }

    /**
     * Applies the existence condition and any expiration atomically for the key.
     */
    public boolean set(RedisBytes key, RedisBytes value, SetOptions subOptions) {
        return stringStorage.set(key, value, subOptions);
    }

    public long lpush(RedisBytes key, List<RedisBytes> values) {
        return listStorage.lpush(key, values);
    }

    public long rpush(RedisBytes key, List<RedisBytes> values) {
        return listStorage.rpush(key, values);
    }

    public Optional<RedisBytes> lpop(RedisBytes key) {
        return listStorage.lpop(key);
    }

    public Optional<RedisBytes> rpop(RedisBytes key) {
        return listStorage.rpop(key);
    }

    public int llen(RedisBytes key) {
        return listStorage.llen(key);
    }

    /**
     * Returns a copy of an inclusive list range, interpreting negative indexes from the end.
     */
    public List<RedisBytes> lrange(RedisBytes key, long start, long end) {
        return listStorage.lrange(key, start, end);
    }

    public long sadd(RedisBytes key, List<RedisBytes> values) {
        return setStorage.sadd(key, values);
    }

    public long srem(RedisBytes key, List<RedisBytes> members) {
        return setStorage.srem(key, members);
    }

    public long scard(RedisBytes key) {
        return setStorage.scard(key);
    }

    public Set<RedisBytes> smembers(RedisBytes key) {
        return setStorage.smembers(key);
    }

    public int sismember(RedisBytes key, RedisBytes value) {
        return setStorage.sismember(key, value);
    }

    public int del(RedisBytes key) {
        return keyStorage.del(key);
    }

    public boolean exists(RedisBytes key) {
        return keyStorage.exists(key);
    }

    /**
     * Returns the remaining lifetime rounded to the nearest second, {@code -1} for persistent keys, or {@code -2} for missing or expired keys.
     */
    public long ttl(RedisBytes key) {
        return keyStorage.ttl(key);
    }

    /**
     * Sets a live key's expiration, deleting the key when the duration is zero or negative.
     */
    public int expire(RedisBytes key, long seconds) {
        return keyStorage.expire(key, seconds);
    }

    public int persist(RedisBytes key) {
        return keyStorage.persist(key);
    }

    /**
     * Adds members or updates scores, returning the number of new members.
     */
    public long zadd(RedisBytes key, Map<RedisBytes, Double> values) {
        return sortedSetStorage.zadd(key, values);
    }

    public Optional<Double> zscore(RedisBytes key, RedisBytes member) {
        return sortedSetStorage.zscore(key, member);
    }

    public long zrem(RedisBytes key, List<RedisBytes> values) {
        return sortedSetStorage.zrem(key, values);
    }

    /**
     * Returns an inclusive rank range in score order, breaking ties by unsigned byte order.
     */
    public List<RedisBytes> zrange(RedisBytes key, long start, long end) {
        return sortedSetStorage.zrange(key, start, end);
    }

    /**
     * Captures a stored entry for AOF, including expired entries still in the keyspace.
     * Hold the persistence write lock through capture and serialization of mutable values.
     */
    public Optional<SnapshotEntry> captureSnapshotEntry(RedisBytes key) {
        return snapshotService.captureEntry(key);
    }

    /**
     * Exports live entries, requiring the caller to hold the persistence write lock through serialization during server operation.
     */
    public List<SnapshotEntry> exportSnapshot() {
        return snapshotService.exportSnapshot();
    }

    /**
     * Merges unexpired entries into storage while the caller has exclusive access to the keyspace.
     */
    public void restoreSnapshot(List<SnapshotEntry> snapshotEntries) {
        snapshotService.restoreSnapshot(snapshotEntries);
    }

    int cleanupExpiredEntries() {
        return keyStorage.cleanupExpiredEntries();
    }

    /**
     * Creates a cleanup task that uses the shared read lock to coordinate with snapshot and AOF writes.
     */
    public Runnable expirationCleanupTask(ReentrantReadWriteLock snapshotLock) {
        Lock readLock = snapshotLock.readLock();
        return () -> {
            readLock.lock();
            try {
                cleanupExpiredEntries();
            } finally {
                readLock.unlock();
            }
        };
    }
}
