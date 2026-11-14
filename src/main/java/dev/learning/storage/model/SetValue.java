package dev.learning.storage.model;

import java.util.Set;

public record SetValue(Set<String> values) implements RedisValue {
}
