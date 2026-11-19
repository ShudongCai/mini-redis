package dev.learning.persistence.model;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.RedisValue;

/**
 * A key and value with an optional expiration deadline in epoch milliseconds.
 */
public record SnapshotEntry(
        RedisBytes key,
        RedisValue value,
        Long expireAt
) {}