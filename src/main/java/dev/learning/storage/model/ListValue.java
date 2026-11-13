package dev.learning.storage.model;

import java.util.Deque;

public record ListValue(Deque<String> values) implements RedisValue {
}
