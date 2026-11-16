package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.storage.model.RedisBytes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

public class InMemoryStorage {
    private final StringStorage stringStorage;
    private final ListStorage listStorage;
    private final SetStorage setStorage;
    private final KeyStorage keyStorage;
    private final SortedSetStorage sortedSetStorage;

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
    }

    public Optional<RedisBytes> get(RedisBytes key) {
        return stringStorage.get(key);
    }

    public boolean set(RedisBytes key, RedisBytes value) {
        return stringStorage.set(key, value);
    }

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

    public long ttl(RedisBytes key) {
        return keyStorage.ttl(key);
    }

    public int expire(RedisBytes key, long seconds) {
        return keyStorage.expire(key, seconds);
    }

    public int persist(RedisBytes key) {
        return keyStorage.persist(key);
    }

    public long zadd(RedisBytes key, Map<RedisBytes, Double> values) {
        return sortedSetStorage.zadd(key, values);
    }

    public Optional<Double> zscore(RedisBytes key, RedisBytes member) {
        return sortedSetStorage.zscore(key, member);
    }

    public long zrem(RedisBytes key, List<RedisBytes> values) {
        return sortedSetStorage.zrem(key, values);
    }

    public List<RedisBytes> zrange(RedisBytes key, long start, long end) {
        return sortedSetStorage.zrange(key, start, end);
    }

    int cleanupExpiredEntries() {
        return keyStorage.cleanupExpiredEntries();
    }

    public Runnable expirationCleanupTask() {
        return this::cleanupExpiredEntries;
    }
}
