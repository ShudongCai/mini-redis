package dev.learning.storage.model.redisValue;

import dev.learning.storage.model.RedisBytes;

import java.util.Deque;

public record ListValue(Deque<RedisBytes> values) implements RedisValue {
}
