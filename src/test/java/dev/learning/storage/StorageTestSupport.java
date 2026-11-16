package dev.learning.storage;

import dev.learning.storage.model.RedisBytes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class StorageTestSupport {
    private StorageTestSupport() {
    }

    public static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public static List<RedisBytes> byteList(String... values) {
        return Arrays.stream(values).map(StorageTestSupport::bytes).toList();
    }
}
