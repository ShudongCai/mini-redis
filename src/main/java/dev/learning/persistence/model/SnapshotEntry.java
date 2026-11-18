package dev.learning.persistence.model;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.RedisValue;

public record SnapshotEntry(
        RedisBytes key,
        RedisValue value,
        Long expireAt
) {}