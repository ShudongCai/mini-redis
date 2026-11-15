package dev.learning.storage;

import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.ListValue;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.StoredEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static dev.learning.storage.StorageContext.isExpired;

final class ListStorage {
    private final StorageContext context;

    ListStorage(StorageContext context) {
        this.context = context;
    }

    long lpush(RedisBytes key, List<RedisBytes> values) {
        return push(key, values, true);
    }

    long rpush(RedisBytes key, List<RedisBytes> values) {
        return push(key, values, false);
    }

    private long push(RedisBytes key, List<RedisBytes> values, boolean isLPush) {
        if (key == null || values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("List values must not be null");
        }

        long[] result = {0};

        context.entries().compute(key, (k, oldEntry) -> {
            if (oldEntry == null || isExpired(oldEntry, context.now())) {
                Deque<RedisBytes> temp = new ArrayDeque<>();

                for (RedisBytes value : values) {
                    if (isLPush) {
                        temp.push(value);
                    } else {
                        temp.offer(value);
                    }
                }

                result[0] = temp.size();
                return new StoredEntry(new ListValue(temp), null);
            }

            if (oldEntry.value() instanceof ListValue(Deque<RedisBytes> oldDeque)) {
                for (RedisBytes value : values) {
                    if (isLPush) {
                        oldDeque.push(value);
                    } else {
                        oldDeque.offer(value);
                    }
                }

                result[0] = oldDeque.size();
                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    Optional<RedisBytes> lpop(RedisBytes key) {
        return pop(key, true);
    }

    Optional<RedisBytes> rpop(RedisBytes key) {
        return pop(key, false);
    }

    private Optional<RedisBytes> pop(RedisBytes key, boolean isLPop) {
        if (key == null) {
            throw new IllegalArgumentException("The key should not be null");
        }

        RedisBytes[] result = {null};

        context.entries().computeIfPresent(key, (k, entry) -> {
            if (isExpired(entry, context.now())) {
                return null;
            }

            if (entry.value() instanceof ListValue(Deque<RedisBytes> temp)) {
                if (temp == null || temp.isEmpty()) {
                    return null;
                }
                if (isLPop) {
                    result[0] = temp.pollFirst();
                } else {
                    result[0] = temp.pollLast();
                }

                if (temp.isEmpty()) {
                    return null;
                }

                return entry;
            }

            throw new WrongTypeException();
        });

        return result[0] == null ? Optional.empty() : Optional.of(result[0]);
    }

    int llen(RedisBytes key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        int[] result = {0};

        context.entries().computeIfPresent(key, (k, entry) -> {
            if (isExpired(entry, context.now())) {
                return null;
            }

            if (entry.value() instanceof ListValue(Deque<RedisBytes> values)) {
                result[0] = values.size();
                return entry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    List<RedisBytes> lrange(RedisBytes key, long start, long end) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        List<RedisBytes> result = new ArrayList<>();

        context.entries().computeIfPresent(key, (k, entry) -> {
            if (isExpired(entry, context.now())) {
                return null;
            }

            if (entry.value() instanceof ListValue(Deque<RedisBytes> values)) {
                long size = values.size();

                long from = start < 0 ? start + size : start;
                long to = end < 0 ? end + size : end;

                from = Math.max(0, from);
                to = Math.min(size - 1, to);

                if (from > to) {
                    return entry;
                }

                long index = 0;

                for (RedisBytes value : values) {
                    if (index > to) {
                        break;
                    }

                    if (index >= from) {
                        result.add(value);
                    }

                    index++;
                }

                return entry;
            }

            throw new WrongTypeException();
        });

        return result;
    }
}
