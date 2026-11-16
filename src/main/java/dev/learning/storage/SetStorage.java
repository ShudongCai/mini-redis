package dev.learning.storage;

import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.SetValue;
import dev.learning.storage.model.StoredEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.learning.storage.StorageContext.isExpired;

final class SetStorage {
    private final StorageContext context;

    SetStorage(StorageContext context) {
        this.context = context;
    }

    long sadd(RedisBytes key, List<RedisBytes> values) {
        if (key == null || values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid SADD arguments");
        }

        long[] result = {0};

        context.entries().compute(key, (k, oldEntry) -> {
            if (oldEntry == null || isExpired(oldEntry, context.now())) {
                Set<RedisBytes> set = new HashSet<>();

                for (RedisBytes value : values) {
                    if (set.add(value)) {
                        result[0]++;
                    }
                }
                return new StoredEntry(new SetValue(set), null);
            }

            if (oldEntry.value() instanceof SetValue(Set<RedisBytes> set)) {
                for (RedisBytes value : values) {
                    if (set.add(value)) {
                        result[0]++;
                    }
                }
                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    long srem(RedisBytes key, List<RedisBytes> members) {
        if (key == null || members == null || members.isEmpty() || members.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid SREM arguments");
        }

        long[] result = {0};

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SetValue(Set<RedisBytes> values)) {
                for (RedisBytes member : members) {
                    if (values.remove(member)) {
                        result[0]++;
                    }
                }
                return values.isEmpty() ? null : oldEntry;
            }
            throw new WrongTypeException();
        });

        return result[0];
    }

    long scard(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("Invalid SCARD arguments");
        }

        long[] result = {0};

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SetValue(Set<RedisBytes> values)) {
                result[0] = values.size();
                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    Set<RedisBytes> smembers(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("Invalid SMEMBERS arguments");
        }

        Set<RedisBytes> result = new HashSet<>();

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SetValue(Set<RedisBytes> values)) {
                result.addAll(values);
                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result;
    }

    int sismember(RedisBytes key, RedisBytes value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Invalid SISMEMBER arguments");
        }

        int[] result = {0};

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SetValue(Set<RedisBytes> values)) {
                if (values.contains(value)) {
                    result[0] = 1;
                }
                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }
}
