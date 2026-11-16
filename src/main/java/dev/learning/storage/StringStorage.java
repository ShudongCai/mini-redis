package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.StoredEntry;
import dev.learning.storage.model.redisValue.StringValue;

import java.util.Optional;

import static dev.learning.storage.StorageContext.isExpired;

final class StringStorage {
    private final StorageContext context;

    StringStorage(StorageContext context) {
        this.context = context;
    }

    Optional<RedisBytes> get(RedisBytes key) {
        StoredEntry entry = context.getLiveEntry(key);

        if (entry == null) {
            return Optional.empty();
        }

        if (entry.value() instanceof StringValue(RedisBytes value)) {
            return Optional.of(value);
        }

        throw new WrongTypeException();
    }

    boolean set(RedisBytes key, RedisBytes value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        context.entries().put(key, new StoredEntry(new StringValue(value), null));
        return true;
    }

    boolean set(RedisBytes key, RedisBytes value, SetOptions subOptions) {
        if (key == null || value == null || subOptions == null) {
            throw new IllegalArgumentException("Key, Value or SubOption should not be null");
        }

        Long ttlSeconds = subOptions.ttlSeconds();
        if (ttlSeconds != null && ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL should be greater than zero");
        }
        Long ttlMillis = ttlSeconds == null ? null : Math.multiplyExact(ttlSeconds, 1000L);
        boolean[] written = {false};

        context.entries().compute(key, (k, oldEntry) -> {
            long now = context.now();
            Long newExpireTime = ttlMillis == null ? null : Math.addExact(now, ttlMillis);
            boolean exists = oldEntry != null && !isExpired(oldEntry, now);

            if (subOptions.nx() && exists) {
                return oldEntry;
            }

            if (subOptions.xx() && !exists) {
                return null;
            }

            written[0] = true;
            return new StoredEntry(new StringValue(value), newExpireTime);
        });

        return written[0];
    }
}
