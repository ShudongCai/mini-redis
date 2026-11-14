package dev.learning.storage.model;

public record StringValue(RedisBytes value) implements RedisValue{
}
