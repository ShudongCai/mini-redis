package dev.learning.storage.valueStorage;

import dev.learning.storage.StorageContext;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.StoredEntry;

import static dev.learning.storage.StorageContext.isExpired;

public final class KeyStorage {
    private final StorageContext context;

    public KeyStorage(StorageContext context) {
        this.context = context;
    }

    public int del(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        boolean[] deleted = {false};

        context.entries().computeIfPresent(key, (k, entry) -> {
            deleted[0] = !isExpired(entry, context.now());

            return null;
        });

        return deleted[0] ? 1 : 0;
    }

    public boolean exists(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }
        return context.getLiveEntry(key) != null;
    }

    public long ttl(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        long[] result = {-2L};

        context.entries().computeIfPresent(key, (k, value) -> {
            if (value.ex() == null) {
                result[0] = -1;
                return value;
            }

            long now = context.now();

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

    public int expire(RedisBytes key, long seconds) {
        if (key == null) {
            throw new IllegalArgumentException("Key or value is null");
        }

        int[] result = {0};

        context.entries().computeIfPresent(key, (k, entry) -> {
            long now = context.now();

            if (entry.ex() != null && entry.ex() <= now) {
                return null;
            }

            if (seconds <= 0) {
                result[0] = 1;
                return null;
            }

            long ex = Math.addExact(
                    now,
                    Math.multiplyExact(seconds, 1000L)
            );

            result[0] = 1;
            return new StoredEntry(entry.value(), ex);
        });

        return result[0];
    }

    public int persist(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("The key is null");
        }

        int[] result = {0};

        context.entries().computeIfPresent(key, (k, value) -> {
            if (value.ex() == null) {
                return value;
            }

            if (value.ex() <= context.now()) {
                return null;
            }

            result[0] = 1;
            return new StoredEntry(value.value(), null);
        });

        return result[0];
    }

    public int cleanupExpiredEntries() {
        int[] cleanupEntries = {0};
        for (RedisBytes key : context.entries().keySet()) {
            context.entries().computeIfPresent(key, (k, entry) -> {
                if (entry.ex() != null && entry.ex() <= context.now()) {
                    cleanupEntries[0] = cleanupEntries[0] + 1;
                    return null;
                }

                return entry;
            });
        }

        return cleanupEntries[0];
    }
}
