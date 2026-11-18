package dev.learning.persistence.model;

import dev.learning.storage.model.RedisBytes;

public sealed interface AofEntry {
    record Replace(SnapshotEntry entry) implements AofEntry {}

    record Delete(RedisBytes key) implements AofEntry {}
}
