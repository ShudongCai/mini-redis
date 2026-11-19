package dev.learning.storage;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.StoredEntry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Shares the keyspace and expiration clock across the storage implementations.
 */
public final class StorageContext {
    private final ConcurrentHashMap<RedisBytes, StoredEntry> entries = new ConcurrentHashMap<>();
    private final LongSupplier currentTimeMillis;

    StorageContext(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    /**
     * Returns the shared keyspace, whose mutable values require a per-key compute operation or the persistence write lock during server operation.
     */
    public ConcurrentHashMap<RedisBytes, StoredEntry> entries() {
        return entries;
    }

    public long now() {
        return currentTimeMillis.getAsLong();
    }

    /**
     * Returns the live entry or {@code null}, atomically removing any expired entry for the key.
     */
    public StoredEntry getLiveEntry(RedisBytes key) {
        return entries.computeIfPresent(key, (k, entry) -> isExpired(entry, now()) ? null : entry);
    }

    public static boolean isExpired(StoredEntry entry, long now) {
        return entry.ex() != null && now >= entry.ex();
    }
}
