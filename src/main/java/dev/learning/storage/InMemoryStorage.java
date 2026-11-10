package dev.learning.storage;

import dev.learning.storage.model.StoredEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorage {
    private final ConcurrentHashMap<String, StoredEntry> data;

    public InMemoryStorage() {
        this.data = new ConcurrentHashMap<>();
    }


    public Optional<String> get(String key) {
        StoredEntry result = data.get(key);

        if (result == null) {
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
                System.currentTimeMillis(),
                Math.multiplyExact(ttlSeconds, 1000L)
        );

        data.put(key, new StoredEntry(value, expireTime));
    }

    public int del(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        if (data.remove(key) != null) {
            return 1;
        }

        return 0;
    }

    public boolean exists(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be blank");
        }

        return data.containsKey(key);
    }
}
