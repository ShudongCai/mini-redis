package dev.learning.storage.model.redisValue;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.SortedSetEntry;

import java.util.Map;
import java.util.TreeSet;

/**
 * Holds mutable score and rank indexes that must remain consistent.
 */
public record SortedSetValue(Map<RedisBytes, Double> score, TreeSet<SortedSetEntry> sortedEntry) implements RedisValue {

}
