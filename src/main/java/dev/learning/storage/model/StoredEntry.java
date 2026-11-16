package dev.learning.storage.model;

import dev.learning.storage.model.redisValue.RedisValue;

public record StoredEntry (
    RedisValue value,
    Long ex
) implements RedisValue{
}
