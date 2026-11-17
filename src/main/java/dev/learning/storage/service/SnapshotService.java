package dev.learning.storage.service;

import dev.learning.persistence.SnapshotEntry;
import dev.learning.storage.StorageContext;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.StoredEntry;
import dev.learning.storage.model.redisValue.RedisValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SnapshotService {
    private final StorageContext storageContext;

    public SnapshotService(StorageContext storageContext) {
        this.storageContext = storageContext;
    }

    public List<SnapshotEntry> exportSnapshot() {
        List<SnapshotEntry> result = new ArrayList<>();
        long now = storageContext.now();

        for (Map.Entry<RedisBytes, StoredEntry> entry : storageContext.entries().entrySet()) {
            RedisBytes key= entry.getKey();
            StoredEntry storedEntry = entry.getValue();

            if (StorageContext.isExpired(storedEntry, now)) {
                continue;
            }

            RedisValue value = storedEntry.value();
            Long expireAt = storedEntry.ex();

            result.add(new SnapshotEntry(key, value, expireAt));
        }

        return result;
    }

    public void restoreSnapshot(List<SnapshotEntry> snapshotEntries) {
        long now = storageContext.now();

        for (SnapshotEntry entry : snapshotEntries) {
            Long expireAt = entry.expireAt();

            if (expireAt != null && expireAt <= now) {
                continue;
            }

            StoredEntry sortedEntry = new StoredEntry(entry.value(), entry.expireAt());
            storageContext.entries().put(entry.key(), sortedEntry);
        }
    }
}
