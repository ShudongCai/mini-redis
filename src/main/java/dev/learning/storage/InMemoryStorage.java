package dev.learning.storage;

import dev.learning.storage.model.StoredEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class InMemoryStorage {
    private final ConcurrentHashMap<String, StoredEntry> data;

    private final LongSupplier currentTimeMillis;

    public InMemoryStorage() {
        this(System::currentTimeMillis);
    }

    InMemoryStorage(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
        this.data = new ConcurrentHashMap<>();
    }


    public Optional<String> get(String key) {
        StoredEntry result = data.get(key);

        if (result == null) {
            return Optional.empty();
        }

        if (isExpired(result)) {
            data.remove(key, result);
            return Optional.empty();
        }

        return Optional.of(result.value());
    }

    public void set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        data.put(key, new StoredEntry(value, null));
    }

    public void set(String key, String value, long ttlSeconds) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        if (ttlSeconds <= 0L) {
            throw new IllegalArgumentException("TTL should be greater than zero");
        }

        Long expireTime = Math.addExact(
                currentTimeMillis.getAsLong(),
                Math.multiplyExact(ttlSeconds, 1000L)
        );

        data.put(key, new StoredEntry(value, expireTime));
    }

    public int del(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        boolean[] deleted = {false};

        data.computeIfPresent(key, (k, entry) -> {
            deleted[0] = !isExpired(entry);

            return null;
        });

        return deleted[0] ? 1 : 0;
    }

    public boolean exists(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        StoredEntry result = data.get(key);

        if (result == null) {
            return false;
        }

        if (isExpired(result)) {
            data.remove(key, result);
            return false;
        }

        return true;
    }

    private boolean isExpired(StoredEntry result) {
        if (result.ex() != null) {
            return currentTimeMillis.getAsLong() >= result.ex();
        }

        return false;
    }
}
