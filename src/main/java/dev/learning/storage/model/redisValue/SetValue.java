package dev.learning.storage.model.redisValue;

import dev.learning.storage.model.RedisBytes;

import java.util.Set;

public record SetValue(Set<RedisBytes> values) implements RedisValue {
}
