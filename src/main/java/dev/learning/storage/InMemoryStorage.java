package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.exception.WrongTypeException;
import dev.learning.storage.model.ListValue;
import dev.learning.storage.model.StoredEntry;
import dev.learning.storage.model.StringValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public class InMemoryStorage {
    private final ConcurrentHashMap<String, StoredEntry> data;

    private final LongSupplier currentTimeMillis;

    public InMemoryStorage() {
        this(System::currentTimeMillis);
    }

    InMemoryStorage(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
        this.data = new ConcurrentHashMap<>();
    }

    public Optional<String> get(String key) {
        StoredEntry entry = getLiveEntry(key);

        if (entry == null) {
            return Optional.empty();
        }

        if (entry.value() instanceof StringValue(String value)) {
            return Optional.of(value);
        }

        throw new IllegalStateException("Wrong Redis value type");
    }

    public boolean set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        data.put(key, new StoredEntry(new StringValue(value), null));
        return true;
    }

    public boolean set(String key, String value, SetOptions subOptions) {
        if (key == null || value == null || subOptions == null) {
            throw new IllegalArgumentException("Key, Value or SubOption should not be null");
        }

        Long ttlSeconds = subOptions.ttlSeconds();
        if (ttlSeconds != null && ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL should be greater than zero");
        }
        Long ttlMillis = ttlSeconds == null ? null : Math.multiplyExact(ttlSeconds, 1000L);
        boolean[] written = {false};

        data.compute(key, (k, oldEntry) -> {
            long now = currentTimeMillis.getAsLong();
            Long newExpireTime = ttlMillis == null ? null : Math.addExact(now, ttlMillis);
            boolean exists = oldEntry != null && !isExpired(oldEntry, now);

            if (subOptions.nx() && exists) {
                return oldEntry;
            }

            if (subOptions.xx() && !exists) {
                return null;
            }

            written[0] = true;
            return new StoredEntry(new StringValue(value), newExpireTime);
        });

        return written[0];
    }

    public long lpush(String key, List<String> values) {
        return push(key, values, true);
    }

    public long rpush(String key, List<String> values) {
        return push(key, values, false);
    }


    private long push(String key, List<String> values, boolean isLPush) {
        if (key == null || values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Key or Value should not be null");
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("List values must not be null");
        }

        long[] result = {0};

        data.compute(key, (k, oldEntry) -> {
            if (oldEntry == null || isExpired(oldEntry, currentTimeMillis.getAsLong())) {
                Deque<String> temp = new ArrayDeque<>();

                for (String value : values) {
                    if (isLPush) {
                        temp.push(value);
                    } else {
                        temp.offer(value);
                    }
                }

                result[0] = temp.size();
                return new StoredEntry(new ListValue(temp), null);
            }

            if (oldEntry.value() instanceof ListValue(Deque<String> oldDeque)) {
                for (String value : values) {
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

    public Optional<String> lpop(String key) {
        return pop(key, true);
    }

    public Optional<String> rpop(String key) {
        return pop(key, false);
    }

    private Optional<String> pop(String key, boolean isLPop) {
        if (key == null) {
            throw new IllegalArgumentException("The key should not be null");
        }

        String[] result = {null};

        data.computeIfPresent(key, (k, entry) -> {
           if (isExpired(entry, currentTimeMillis.getAsLong())) {
               return null;
           }

           if (entry.value() instanceof ListValue(Deque<String> temp)) {
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

    public int del(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        boolean[] deleted = {false};

        data.computeIfPresent(key, (k, entry) -> {
            deleted[0] = !isExpired(entry, currentTimeMillis.getAsLong());

            return null;
        });

        return deleted[0] ? 1 : 0;
    }

    public boolean exists(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }
        return getLiveEntry(key) != null;
    }

    public int llen(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        int[] result = {0};

        data.computeIfPresent(key, (k, entry) -> {
            if (isExpired(entry, currentTimeMillis.getAsLong())) {
                return null;
            }

            if (entry.value() instanceof ListValue(Deque<String> values)) {
                result[0] = values.size();
                return entry;
            }

            throw new WrongTypeException();
        });

        return result[0];
    }

    public List<String> lrange(String key, long start, long end) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        List<String> result = new ArrayList<>();
        long[] temp = {start, end};

        data.computeIfPresent(key, (k, entry) -> {
           if (isExpired(entry, currentTimeMillis.getAsLong())) {
               return null;
           }

            if (entry.value() instanceof ListValue(Deque<String> values)) {
                long size = values.size();

                long from = start < 0 ? start + size : start;
                long to = end < 0 ? end + size : end;

                from = Math.max(0, from);
                to = Math.min(size - 1, to);

                if (from > to) {
                    return entry;
                }

                long index = 0;

                for (String value : values) {
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

    private StoredEntry getLiveEntry(String key) {
        return data.computeIfPresent(key, (k, entry) ->
                isExpired(entry, currentTimeMillis.getAsLong()) ? null : entry);
    }

    private static boolean isExpired(StoredEntry entry, long now) {
        return entry.ex() != null && now >= entry.ex();
    }

    public long ttl(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key should not be null");
        }

        long[] result = {-2L};

        data.computeIfPresent(key, (k, value) -> {
            if (value.ex() == null) {
                result[0] = -1;
                return value;
            }

            long now = currentTimeMillis.getAsLong();

            if (value.ex() <= now) {
                return null;
            }

            long remainingMillis = value.ex() - now;
            result[0] = remainingMillis / 1000
                    + (remainingMillis % 1000 >= 500 ? 1 : 0);
            return value;
        });

        return result[0];
    }

    public int expire(String key, long seconds) {
        if (key == null) {
            throw new IllegalArgumentException("Key or value is null");
        }

        int[] result = {0};

        data.computeIfPresent(key, (k, entry) -> {
            long now = currentTimeMillis.getAsLong();

            if (entry.ex() != null && entry.ex() <= now) {
                return null;
            }

            if (seconds <= 0) {
                result[0] = 1;
                return null;
            }

            long ex = Math.addExact(
                    now,
                    Math.multiplyExact(seconds, 1000L)
            );

            result[0] = 1;
            return new StoredEntry(entry.value(), ex);
        });

        return result[0];
    }

    public int persist(String key) {
        if (key == null) {
            throw new IllegalArgumentException("The key is null");
        }

        int[] result = {0};

        data.computeIfPresent(key, (k, value) -> {
           if (value.ex() == null) {
                return value;
           }

           if (value.ex() <= currentTimeMillis.getAsLong()) {
               return null;
           }

           result[0] = 1;
           return new StoredEntry(value.value(), null);
        });

        return result[0];
    }

    int cleanupExpiredEntries() {
        int[] cleanupEntries = {0};
        for (String key : data.keySet()) {
            data.computeIfPresent(key, (k, entry) -> {
               if (entry.ex() != null && entry.ex() <= currentTimeMillis.getAsLong()) {
                   cleanupEntries[0] = cleanupEntries[0] + 1;
                   return null;
               }

                return entry;
            });
        }

        return cleanupEntries[0];
    }

    public Runnable expirationCleanupTask() {
        return this::cleanupExpiredEntries;
    }
}
