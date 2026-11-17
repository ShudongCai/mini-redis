
package dev.learning.storage.valueStorage;

import dev.learning.exception.WrongTypeException;
import dev.learning.storage.StorageContext;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.SortedSetEntry;
import dev.learning.storage.model.redisValue.SortedSetValue;
import dev.learning.storage.model.StoredEntry;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SortedSetStorage {
    private final StorageContext context;

    public SortedSetStorage(StorageContext context) {
        this.context = context;
    }

    public long zadd(RedisBytes key, Map<RedisBytes, Double> values) {
        if (key == null || values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Invalid ZADD arguments");
        }

        long[] result = {0};

        context.entries().compute(key, (k, oldEntry) -> {
            if (oldEntry == null || StorageContext.isExpired(oldEntry, context.now())) {
                TreeSet<SortedSetEntry> treeSet = new TreeSet<>((a, b) -> {
                    int c = Double.compare(a.score(), b.score());
                    if (c == 0) {
                        return Arrays.compareUnsigned(a.value().getBytes(), b.value().getBytes());
                    }
                    return c;
                });

                for (Map.Entry<RedisBytes, Double> value : values.entrySet()) {
                    result[0]++;
                    treeSet.add(new SortedSetEntry(value.getKey(), value.getValue()));
                }
                return new StoredEntry(new SortedSetValue(new HashMap<>(values), treeSet), null);
            }

            if (oldEntry.value() instanceof SortedSetValue(Map<RedisBytes, Double> score, TreeSet<SortedSetEntry> sortedEntry)) {
                for (Map.Entry<RedisBytes, Double> value : values.entrySet()) {
                    Double oldScore = score.put(value.getKey(), value.getValue());

                    if (oldScore == null) {
                        result[0]++;
                    } else {
                        sortedEntry.remove(new SortedSetEntry(value.getKey(), oldScore));
                    }

                    sortedEntry.add(new SortedSetEntry(value.getKey(), value.getValue()));
                }
                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    public Optional<Double> zscore(RedisBytes key, RedisBytes member) {
        if (key == null) {
            throw new IllegalArgumentException("Invalid ZSCORE arguments");
        }

        AtomicReference<Optional<Double>> result = new AtomicReference<>(Optional.empty());

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (StorageContext.isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SortedSetValue val) {
                Double temp = val.score().get(member);
                if (temp != null) {
                    result.set(Optional.of(temp));
                }

                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result.get();
    }

    public long zrem(RedisBytes key, List<RedisBytes> values) {
        if (key == null || values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid ZREM arguments");
        }

        long[] result = {0};

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (StorageContext.isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SortedSetValue(Map<RedisBytes, Double> score, TreeSet<SortedSetEntry> sortedEntry)) {
                for (RedisBytes value : values) {
                    Double temp = score.remove(value);
                    if (temp != null) {
                        sortedEntry.remove(new SortedSetEntry(value, temp));
                        result[0]++;
                    }
                }

                return score.isEmpty() ? null : oldEntry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    public List<RedisBytes> zrange(RedisBytes key, long start, long end) {
        if (key == null) {
            throw new IllegalArgumentException("Invalid ZRANGE arguments");
        }

        List<RedisBytes> result = new ArrayList<>();

        context.entries().computeIfPresent(key, (k, oldEntry) -> {
            if (StorageContext.isExpired(oldEntry, context.now())) {
                return null;
            }

            if (oldEntry.value() instanceof SortedSetValue val) {
                long size = val.sortedEntry().size();

                long from = start < 0 ? start + size : start;
                long to = end < 0 ? end + size : end;

                from = Math.max(0, from);
                to = Math.min(size - 1, to);

                if (from > to) {
                    return oldEntry;
                }

                long index = 0;

                for (SortedSetEntry entry : val.sortedEntry()) {
                    if (index > to) {
                        break;
                    }

                    if (index >= from) {
                        result.add(entry.value());
                    }

                    index++;
                }

                return oldEntry;
            }

            throw new WrongTypeException();
        });

        return result;
    }
}
