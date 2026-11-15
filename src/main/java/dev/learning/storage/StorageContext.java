package dev.learning.storage;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.StoredEntry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class StorageContext {
    private final ConcurrentHashMap<RedisBytes, StoredEntry> entries = new ConcurrentHashMap<>();
    private final LongSupplier currentTimeMillis;

    StorageContext(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    // Access mutable list and set contents only inside a per-key compute callback.
    ConcurrentHashMap<RedisBytes, StoredEntry> entries() {
        return entries;
    }

    long now() {
        return currentTimeMillis.getAsLong();
    }

    StoredEntry getLiveEntry(RedisBytes key) {
        return entries.computeIfPresent(key, (k, entry) -> isExpired(entry, now()) ? null : entry);
    }

    static boolean isExpired(StoredEntry entry, long now) {
        return entry.ex() != null && now >= entry.ex();
    }
}
