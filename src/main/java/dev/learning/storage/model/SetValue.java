package dev.learning.storage.model;

import java.util.Set;

public record SetValue(Set<RedisBytes> values) implements RedisValue {
}
