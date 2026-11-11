package dev.learning.storage;

import dev.learning.command.SetOptions;
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
        StoredEntry entry = getLiveEntry(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    public boolean set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        data.put(key, new StoredEntry(value, null));
        return true;
    }

    public boolean set(String key, String value, SetOptions subOptions) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        Long ttlSeconds = subOptions.ttlSeconds();
        if (ttlSeconds != null && ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL should be greater than zero");
        }
        Long ttlMillis = ttlSeconds == null ? null : Math.multiplyExact(ttlSeconds, 1000L);
        boolean[] written = {false};

        data.compute(key, (k, oldEntry) -> {
            long now = currentTimeMillis.getAsLong();
            Long newExpireTime = ttlMillis == null ? null : Math.addExact(now, ttlMillis);
            boolean exists = oldEntry != null && !isExpired(oldEntry, now);

            if (subOptions.nx() && exists) {
                return oldEntry;
            }

            if (subOptions.xx() && !exists) {
                return null;
            }

            written[0] = true;
            return new StoredEntry(value, newExpireTime);
        });

        return written[0];
    }

    public int del(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        boolean[] deleted = {false};

        data.computeIfPresent(key, (k, entry) -> {
            deleted[0] = !isExpired(entry, currentTimeMillis.getAsLong());

            return null;
        });

        return deleted[0] ? 1 : 0;
    }

    public boolean exists(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }
        return getLiveEntry(key) != null;
    }

    private StoredEntry getLiveEntry(String key) {
        return data.computeIfPresent(key, (k, entry) ->
                isExpired(entry, currentTimeMillis.getAsLong()) ? null : entry);
    }

    private static boolean isExpired(StoredEntry entry, long now) {
        return entry.ex() != null && now >= entry.ex();
    }

    public long ttl(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        long[] result = {-2L};

        data.computeIfPresent(key, (k, value) -> {
            if (value.ex() == null) {
                result[0] = -1;
                return value;
            }

            long now = currentTimeMillis.getAsLong();

            if (value.ex() <= now) {
                return null;
            }

            long remainingMillis = value.ex() - now;
            result[0] = remainingMillis / 1000
                    + (remainingMillis % 1000 >= 500 ? 1 : 0);
            return value;
        });

        return result[0];
    }
}
