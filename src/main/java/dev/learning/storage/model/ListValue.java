package dev.learning.storage.model;

import java.util.Deque;

public record ListValue(Deque<RedisBytes> values) implements RedisValue {
}
