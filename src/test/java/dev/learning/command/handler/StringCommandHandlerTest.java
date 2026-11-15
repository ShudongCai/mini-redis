package dev.learning.command.handler;

import dev.learning.command.SetOptions;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.learning.command.handler.CommandTestSupport.assertBulkStringEquals;
import static dev.learning.command.handler.CommandTestSupport.bulkString;
import static dev.learning.command.handler.CommandTestSupport.bytes;
import static dev.learning.command.handler.CommandTestSupport.command;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringCommandHandlerTest {
    private final StorageTestClock clock = new StorageTestClock();
    private final InMemoryStorage storage = clock.newStorage();
    private final StringCommandHandler handler = new StringCommandHandler(storage);

    @Test
    void shouldStoreValueWhenSetIsExecuted() {
        RespValue result = handler.executeSetCommand(command("SET", "name", "Alice"));

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldOverwriteValueWhenSetIsExecutedAgain() {
        assertEquals(new RespValue.SimpleString("OK"), handler.executeSetCommand(command("SET", "name", "Alice")));

        RespValue result = handler.executeSetCommand(command("SET", "name", "Bob"));

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnValueWhenGetIsExecuted() {
        storage.set(bytes("name"), bytes("Alice"));

        assertBulkStringEquals("Alice", handler.executeGetCommand(command("GET", "name")));
    }

    @Test
    void shouldReturnNullBulkStringWhenKeyDoesNotExist() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = handler.executeGetCommand(command("GET", "missing"));

        RespValue.BulkString value = assertInstanceOf(RespValue.BulkString.class, result);
        assertNull(value.value());
    }

    @Test
    void shouldPreserveUnicodeWhenSettingAndGettingValue() {
        String key = "问候";
        String value = "你好，Redis 👋";

        assertEquals(new RespValue.SimpleString("OK"), handler.executeSetCommand(command("SET", key, value)));
        assertBulkStringEquals(value, handler.executeGetCommand(command("GET", key)));
    }

    @Test
    void shouldSupportEmptyStringWhenSettingValue() {
        assertEquals(new RespValue.SimpleString("OK"), handler.executeSetCommand(command("SET", "name", "")));
        assertBulkStringEquals("", handler.executeGetCommand(command("GET", "name")));
    }

    @Test
    void shouldReturnErrorWhenKeyTypeIsInvalid() {
        List<RespValue> request = List.of(bulkString("GET"), new RespValue.IntegerValue(123));

        RespValue result = handler.executeGetCommand(request);

        assertInstanceOf(RespValue.ErrorValue.class, result);
    }

    @Test
    void shouldPreserveExistingValueWhenNxConditionFails() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = handler.executeSetCommand(command("SET", "name", "Bob", "NX"));

        assertEquals(new RespValue.BulkString(null), result);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldOverwriteExistingValueWhenXxConditionSucceeds() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = handler.executeSetCommand(command("SET", "name", "Bob", "XX"));

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldTreatExpiredKeyAsAbsentWhenNxIsUsed() {
        assertEquals(new RespValue.SimpleString("OK"),
                handler.executeSetCommand(command("SET", "name", "Alice", "EX", "1")));
        clock.advanceMillis(1_000);

        RespValue result = handler.executeSetCommand(command("SET", "name", "Bob", "NX"));

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldKeepBinaryKeysDistinctFromEachOtherAndFromUtf8ReplacementCharacter() {
        storage.set(bytes("\uFFFD"), bytes("original"));
        RespValue.BulkString firstKey = new RespValue.BulkString(new byte[]{(byte) 0xff});
        RespValue.BulkString secondKey = new RespValue.BulkString(new byte[]{(byte) 0xfe});
        RespValue.SimpleString ok = new RespValue.SimpleString("OK");

        assertEquals(ok, handler.executeSetCommand(List.of(bulkString("SET"), firstKey, bulkString("first"))));
        assertEquals(ok, handler.executeSetCommand(List.of(bulkString("SET"), secondKey, bulkString("second"))));

        assertBulkStringEquals("first", handler.executeGetCommand(List.of(bulkString("GET"), firstKey)));
        assertBulkStringEquals("second", handler.executeGetCommand(List.of(bulkString("GET"), secondKey)));
        assertBulkStringEquals("original", handler.executeGetCommand(command("GET", "\uFFFD")));
    }

    @Test
    void shouldReturnBinaryValueUnchangedWhenReadingAfterSet() {
        storage.set(bytes("name"), bytes("original"));
        byte[] payload = {(byte) 0xc3, 0x28, 0, '\r', '\n'};
        List<RespValue> request = List.of(
                bulkString("SET"), bulkString("name"), new RespValue.BulkString(payload));

        assertEquals(new RespValue.SimpleString("OK"), handler.executeSetCommand(request));
        RespValue.BulkString result = assertInstanceOf(RespValue.BulkString.class,
                handler.executeGetCommand(command("GET", "name")));
        assertArrayEquals(payload, result.value());
    }

    @Test
    void shouldPreserveValueAndTtlWhenSetOptionsAreInvalid() {
        clock.advanceMillis(1_000);
        assertEquals(new RespValue.SimpleString("OK"),
                handler.executeSetCommand(command("SET", "name", "original", "EX", "1")));

        assertInstanceOf(RespValue.ErrorValue.class,
                handler.executeSetCommand(command("SET", "name", "changed", "NX", "XX")));
        assertInstanceOf(RespValue.ErrorValue.class,
                handler.executeSetCommand(command("SET", "name", "changed", "EX", "invalid")));
        assertInstanceOf(RespValue.ErrorValue.class,
                handler.executeSetCommand(command("SET", "name", "changed", "EX", "9223372036854775807")));
        assertInstanceOf(RespValue.ErrorValue.class,
                handler.executeSetCommand(command("SET", "name", "changed", "EX", "9223372036854775")));

        clock.advanceMillis(999);
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("name")));
        clock.advanceMillis(1);
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldRejectSetWithInvalidValueWithoutChangingEntry() {
        RedisBytes key = bytes("name");
        storage.set(key, bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build());
        List<RespValue> request = List.of(
                bulkString("SET"), bulkString("name"), new RespValue.IntegerValue(42));

        assertEquals(new RespValue.ErrorValue("ERR invalid value argument"), handler.executeSetCommand(request));
        assertEquals(Optional.of(bytes("Alice")), storage.get(key));
        assertEquals(10L, storage.ttl(key));
    }

    @Test
    void shouldRejectSetWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'set' command");

        assertEquals(expected, handler.executeSetCommand(command("SET", "name")));
    }

    @Test
    void shouldRejectGetWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'get' command");

        assertEquals(expected, handler.executeGetCommand(command("GET")));
    }
}
