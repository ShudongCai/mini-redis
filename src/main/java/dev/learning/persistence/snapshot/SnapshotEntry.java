package dev.learning.persistence.snapshot;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.RedisValue;

public record SnapshotEntry(
        RedisBytes key,
        RedisValue value,
        Long expireAt
) {}