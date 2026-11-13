package dev.learning.storage.model;

public record StoredEntry (
    RedisValue value,
    Long ex
) implements RedisValue{
}
