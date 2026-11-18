package dev.learning.storage.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable binary value with content-based equality.
 */
public final class RedisBytes {
    private final byte[] bytes;

    public RedisBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof RedisBytes redisBytes)) {
            return false;
        }

        return Arrays.equals(this.bytes, redisBytes.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }
}
