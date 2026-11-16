package dev.learning.storage.model;

public record SortedSetEntry(
        RedisBytes value,
        Double score
) {
}
