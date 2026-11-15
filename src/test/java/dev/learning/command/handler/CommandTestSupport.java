package dev.learning.command.handler;

import dev.learning.protocol.RespValue;
import dev.learning.storage.model.RedisBytes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class CommandTestSupport {
    private CommandTestSupport() {
    }

    static List<RespValue> command(String... elements) {
        return Arrays.stream(elements)
                .<RespValue>map(CommandTestSupport::bulkString)
                .toList();
    }

    static RespValue.BulkString bulkString(String value) {
        return new RespValue.BulkString(value.getBytes(StandardCharsets.UTF_8));
    }

    static void assertBulkStringEquals(String expected, RespValue result) {
        RespValue.BulkString value = assertInstanceOf(RespValue.BulkString.class, result);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), value.value());
    }

    static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    static List<RedisBytes> byteList(String... values) {
        return Arrays.stream(values).map(CommandTestSupport::bytes).toList();
    }
}
