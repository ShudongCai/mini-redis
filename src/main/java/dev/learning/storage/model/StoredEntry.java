package dev.learning.storage.model;

import dev.learning.storage.model.redisValue.RedisValue;

/**
 * An in-memory value with an optional expiration deadline in epoch milliseconds.
 */
public record StoredEntry (
    RedisValue value,
    Long ex
) implements RedisValue{
}
