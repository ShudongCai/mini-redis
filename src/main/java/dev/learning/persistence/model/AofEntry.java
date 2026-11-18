package dev.learning.persistence.model;

import dev.learning.storage.model.RedisBytes;

/**
 * A complete key replacement or deletion recorded in the append-only log.
 */
public sealed interface AofEntry {
    record Replace(SnapshotEntry entry) implements AofEntry {}

    record Delete(RedisBytes key) implements AofEntry {}
}
