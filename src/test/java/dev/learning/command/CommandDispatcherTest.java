package dev.learning.command;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandDispatcherTest {
    private final StorageTestClock clock = new StorageTestClock();
    private final InMemoryStorage storage = clock.newStorage();
    private final CommandDispatcher dispatcher = new CommandDispatcher(storage);

    @Test
    void shouldReturnPongWhenPingIsExecuted() {
        RespValue.SimpleString pong = new RespValue.SimpleString("PONG");

        assertEquals(pong, dispatch("PING"));
        assertEquals(pong, dispatcher.dispatch(new RespValue.SimpleString("ping")));
        assertEquals(pong, dispatcher.dispatch(bulkString("PING")));
    }

    @Test
    void shouldStoreValueWhenSetIsExecuted() {
        RespValue result = dispatch("SET", "name", "Alice");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldOverwriteValueWhenSetIsExecutedAgain() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice"));

        RespValue result = dispatch("SET", "name", "Bob");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of("Bob"), storage.get("name"));
    }

    @Test
    void shouldReturnValueWhenGetIsExecuted() {
        storage.set("name", "Alice");

        assertBulkStringEquals("Alice", dispatch("GET", "name"));
    }

    @Test
    void shouldReturnNullBulkStringWhenKeyDoesNotExist() {
        storage.set("name", "Alice");

        RespValue result = dispatch("GET", "missing");

        RespValue.BulkString value = assertInstanceOf(RespValue.BulkString.class, result);
        assertNull(value.value());
    }

    @Test
    void shouldReturnMinusTwoForMissingTtlKey() {
        assertEquals(new RespValue.IntegerValue(-2), dispatch("TTL", "missing"));
    }

    @Test
    void shouldReturnMinusOneForPersistentTtlKey() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice"));

        assertEquals(new RespValue.IntegerValue(-1), dispatch("TTL", "name"));
    }

    @ParameterizedTest(name = "{0} ms remaining returns RESP integer {1}")
    @CsvSource({"10000, 10", "9600, 10", "9400, 9", "400, 0", "0, -2"})
    void shouldReturnTtlForExpiringKey(long remainingMillis, long expectedSeconds) {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "10"));
        clock.advanceMillis(10_000 - remainingMillis);

        assertEquals(new RespValue.IntegerValue(expectedSeconds), dispatch("TTL", "name"));
    }

    @Test
    void shouldRejectTtlWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue("ERR wrong number of arguments for 'ttl' command");

        assertEquals(expected, dispatch("TTL"));
        assertEquals(expected, dispatch("TTL", "name", "extra"));
    }

    @Test
    void shouldRejectTtlWithInvalidKeyType() {
        RespValue request = new RespValue.ArrayValue(List.of(bulkString("TTL"), new RespValue.IntegerValue(123)));

        assertInstanceOf(RespValue.ErrorValue.class, dispatcher.dispatch(request));
    }

    @Test
    void shouldReturnZeroWhenExpireKeyIsMissing() {
        assertEquals(new RespValue.IntegerValue(0), dispatch("EXPIRE", "missing", "10"));
        assertEquals(new RespValue.IntegerValue(-2), dispatch("TTL", "missing"));
    }

    @ParameterizedTest(name = "EXPIRE {0} on an expired key returns RESP integer zero")
    @ValueSource(strings = {"10", "0"})
    void shouldReturnZeroWhenExpireKeyIsAlreadyExpired(String seconds) {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "1"));
        clock.advanceMillis(1_000);

        assertEquals(new RespValue.IntegerValue(0), dispatch("EXPIRE", "name", seconds));
        assertEquals(new RespValue.IntegerValue(-2), dispatch("TTL", "name"));
    }

    @Test
    void shouldSetExpirationOnPersistentKeyWithoutChangingItsValue() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice"));

        assertEquals(new RespValue.IntegerValue(1), dispatch("EXPIRE", "name", "10"));
        assertEquals(new RespValue.IntegerValue(10), dispatch("TTL", "name"));
        assertBulkStringEquals("Alice", dispatch("GET", "name"));
    }

    @Test
    void shouldReplaceExistingExpirationRelativeToCurrentTime() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "10"));
        clock.advanceMillis(4_000);

        assertEquals(new RespValue.IntegerValue(1), dispatch("EXPIRE", "name", "20"));
        assertEquals(new RespValue.IntegerValue(20), dispatch("TTL", "name"));
        assertBulkStringEquals("Alice", dispatch("GET", "name"));
    }

    @ParameterizedTest(name = "EXPIRE {0} deletes a live key")
    @ValueSource(strings = {"0", "-5"})
    void shouldDeleteLiveKeyWhenExpireTimeIsNonPositive(String seconds) {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "10"));

        assertEquals(new RespValue.IntegerValue(1), dispatch("EXPIRE", "name", seconds));
        assertEquals(new RespValue.BulkString(null), dispatch("GET", "name"));
    }

    @ParameterizedTest(name = "EXPIRE {0} returns an error and preserves the entry")
    @ValueSource(strings = {"9223372036854775808", "9223372036854775807", "9223372036854775"})
    void shouldReturnErrorAndPreserveValueAndDeadlineWhenExpireOverflows(String seconds) {
        clock.advanceMillis(1_000);
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "10"));

        assertInstanceOf(RespValue.ErrorValue.class, dispatch("EXPIRE", "name", seconds));
        assertEquals(new RespValue.IntegerValue(10), dispatch("TTL", "name"));
        clock.advanceMillis(9_999);
        assertBulkStringEquals("Alice", dispatch("GET", "name"));
        clock.advanceMillis(1);
        assertEquals(new RespValue.IntegerValue(-2), dispatch("TTL", "name"));
    }

    @Test
    void shouldReturnOneWhenKeyExists() {
        storage.set("name", "Alice");

        assertEquals(new RespValue.IntegerValue(1), dispatch("EXISTS", "name"));
    }

    @Test
    void shouldReturnZeroWhenKeyDoesNotExist() {
        storage.set("name", "Alice");

        assertEquals(new RespValue.IntegerValue(0), dispatch("EXISTS", "missing"));
    }

    @Test
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        storage.set("name", "Alice");

        RespValue result = dispatch("DEL", "name");

        assertEquals(new RespValue.IntegerValue(1), result);
        assertFalse(storage.exists("name"));
    }

    @Test
    void shouldReturnZeroWhenNonExistingKeyIsDeleted() {
        storage.set("name", "Alice");

        RespValue result = dispatch("DEL", "missing");

        assertEquals(new RespValue.IntegerValue(0), result);
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldReturnErrorWhenCommandIsUnknown() {
        assertEquals(new RespValue.ErrorValue("Invalid Command type"), dispatch("UNKNOWN"));
    }

    @Test
    void shouldReturnErrorWhenArgumentCountIsInvalid() {
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'ping' command"),
                dispatch("PING", "extra"));
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'set' command"),
                dispatch("SET", "name"));
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'get' command"),
                dispatch("GET"));
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'exists' command"),
                dispatch("EXISTS"));
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'del' command"),
                dispatch("DEL"));
    }

    @Test
    void shouldReturnErrorWhenCommandArrayIsEmpty() {
        RespValue result = dispatcher.dispatch(new RespValue.ArrayValue(List.of()));

        assertEquals(new RespValue.ErrorValue("The command should not be empty"), result);
    }

    @Test
    void shouldPreserveUnicodeWhenSettingAndGettingValue() {
        String key = "问候";
        String value = "你好，Redis 👋";

        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", key, value));
        assertBulkStringEquals(value, dispatch("GET", key));
    }

    @Test
    void shouldSupportEmptyStringWhenSettingValue() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", ""));
        assertBulkStringEquals("", dispatch("GET", "name"));
    }

    @Test
    void shouldReturnErrorWhenKeyTypeIsInvalid() {
        RespValue request = new RespValue.ArrayValue(List.of(
                bulkString("GET"),
                new RespValue.IntegerValue(123)
        ));

        RespValue result = dispatcher.dispatch(request);

        assertInstanceOf(RespValue.ErrorValue.class, result);
    }

    @Test
    void shouldExecuteValidCommandAfterInvalidCommand() {
        RespValue invalid = dispatch("SET", "name");

        assertInstanceOf(RespValue.ErrorValue.class, invalid);

        RespValue valid = dispatch("SET", "name", "Alice");

        assertEquals(new RespValue.SimpleString("OK"), valid);
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldPreserveExistingValueWhenNxConditionFails() {
        storage.set("name", "Alice");

        RespValue result = dispatch("SET", "name", "Bob", "NX");

        assertEquals(new RespValue.BulkString(null), result);
        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldOverwriteExistingValueWhenXxConditionSucceeds() {
        storage.set("name", "Alice");

        RespValue result = dispatch("SET", "name", "Bob", "XX");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of("Bob"), storage.get("name"));
    }

    @Test
    void shouldTreatExpiredKeyAsAbsentWhenNxIsUsed() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "1"));
        clock.advanceMillis(1_000);

        RespValue result = dispatch("SET", "name", "Bob", "NX");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of("Bob"), storage.get("name"));
    }

    @Test
    void shouldRejectMalformedUtf8KeysWithoutAccessingReplacementCharacterKey() {
        storage.set("\uFFFD", "original");
        RespValue.BulkString malformedKey = new RespValue.BulkString(new byte[]{(byte) 0xff});

        RespValue setRequest = new RespValue.ArrayValue(List.of(
                bulkString("SET"), malformedKey, bulkString("replacement")));
        assertEquals(new RespValue.ErrorValue("ERR invalid key"), dispatcher.dispatch(setRequest));

        for (String command : List.of("GET", "EXISTS", "DEL")) {
            RespValue request = new RespValue.ArrayValue(List.of(bulkString(command), malformedKey));
            assertEquals(new RespValue.ErrorValue("ERR invalid key"), dispatcher.dispatch(request), command);
        }
        assertEquals(Optional.of("original"), storage.get("\uFFFD"));
    }

    @Test
    void shouldRejectMalformedUtf8ValueWithoutOverwritingExistingValue() {
        storage.set("name", "original");
        RespValue.BulkString malformedValue = new RespValue.BulkString(new byte[]{(byte) 0xc3, 0x28});
        RespValue request = new RespValue.ArrayValue(List.of(bulkString("SET"), bulkString("name"), malformedValue));

        assertEquals(new RespValue.ErrorValue("ERR invalid value"), dispatcher.dispatch(request));
        assertEquals(Optional.of("original"), storage.get("name"));
    }

    @Test
    void shouldPreserveValueAndTtlWhenSetOptionsAreInvalid() {
        clock.advanceMillis(1_000);
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "original", "EX", "1"));

        assertInstanceOf(RespValue.ErrorValue.class, dispatch("SET", "name", "changed", "NX", "XX"));
        assertInstanceOf(RespValue.ErrorValue.class, dispatch("SET", "name", "changed", "EX", "invalid"));
        assertInstanceOf(RespValue.ErrorValue.class, dispatch("SET", "name", "changed", "EX", "9223372036854775807"));
        assertInstanceOf(RespValue.ErrorValue.class, dispatch("SET", "name", "changed", "EX", "9223372036854775"));

        clock.advanceMillis(999);
        assertEquals(Optional.of("original"), storage.get("name"));
        clock.advanceMillis(1);
        assertEquals(Optional.empty(), storage.get("name"));
    }

    private RespValue dispatch(String... arguments) {
        List<RespValue> elements = Arrays.stream(arguments)
                .<RespValue>map(CommandDispatcherTest::bulkString)
                .toList();
        return dispatcher.dispatch(new RespValue.ArrayValue(elements));
    }

    private static RespValue.BulkString bulkString(String value) {
        return new RespValue.BulkString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertBulkStringEquals(String expected, RespValue result) {
        RespValue.BulkString value = assertInstanceOf(RespValue.BulkString.class, result);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), value.value());
    }
}
