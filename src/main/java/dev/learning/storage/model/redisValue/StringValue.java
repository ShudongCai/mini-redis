package dev.learning.storage.model.redisValue;

import dev.learning.storage.model.RedisBytes;

public record StringValue(RedisBytes value) implements RedisValue{
}
