package dev.learning.command;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldOverwriteValueWhenSetIsExecutedAgain() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice"));

        RespValue result = dispatch("SET", "name", "Bob");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnValueWhenGetIsExecuted() {
        storage.set(bytes("name"), bytes("Alice"));

        assertBulkStringEquals("Alice", dispatch("GET", "name"));
    }

    @Test
    void shouldReturnNullBulkStringWhenKeyDoesNotExist() {
        storage.set(bytes("name"), bytes("Alice"));

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
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(1), dispatch("EXISTS", "name"));
    }

    @Test
    void shouldReturnZeroWhenKeyDoesNotExist() {
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(0), dispatch("EXISTS", "missing"));
    }

    @Test
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = dispatch("DEL", "name");

        assertEquals(new RespValue.IntegerValue(1), result);
        assertFalse(storage.exists(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenNonExistingKeyIsDeleted() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = dispatch("DEL", "missing");

        assertEquals(new RespValue.IntegerValue(0), result);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
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
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldPreserveExistingValueWhenNxConditionFails() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = dispatch("SET", "name", "Bob", "NX");

        assertEquals(new RespValue.BulkString(null), result);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldOverwriteExistingValueWhenXxConditionSucceeds() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = dispatch("SET", "name", "Bob", "XX");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldTreatExpiredKeyAsAbsentWhenNxIsUsed() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("SET", "name", "Alice", "EX", "1"));
        clock.advanceMillis(1_000);

        RespValue result = dispatch("SET", "name", "Bob", "NX");

        assertEquals(new RespValue.SimpleString("OK"), result);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldKeepBinaryKeysDistinctFromEachOtherAndFromUtf8ReplacementCharacter() {
        storage.set(bytes("\uFFFD"), bytes("original"));
        RespValue.BulkString firstKey = new RespValue.BulkString(new byte[]{(byte) 0xff});
        RespValue.BulkString secondKey = new RespValue.BulkString(new byte[]{(byte) 0xfe});
        RespValue.SimpleString ok = new RespValue.SimpleString("OK");

        assertEquals(ok, dispatcher.dispatch(new RespValue.ArrayValue(List.of(
                bulkString("SET"), firstKey, bulkString("first")))));
        assertEquals(ok, dispatcher.dispatch(new RespValue.ArrayValue(List.of(
                bulkString("SET"), secondKey, bulkString("second")))));

        assertBulkStringEquals("first", dispatcher.dispatch(new RespValue.ArrayValue(List.of(
                bulkString("GET"), firstKey))));
        assertBulkStringEquals("second", dispatcher.dispatch(new RespValue.ArrayValue(List.of(
                bulkString("GET"), secondKey))));
        assertBulkStringEquals("original", dispatch("GET", "\uFFFD"));
    }

    @Test
    void shouldReturnBinaryValueUnchangedWhenReadingAfterSet() {
        storage.set(bytes("name"), bytes("original"));
        byte[] payload = {(byte) 0xc3, 0x28, 0, '\r', '\n'};
        RespValue request = new RespValue.ArrayValue(List.of(
                bulkString("SET"), bulkString("name"), new RespValue.BulkString(payload)));

        assertEquals(new RespValue.SimpleString("OK"), dispatcher.dispatch(request));
        RespValue.BulkString result = assertInstanceOf(RespValue.BulkString.class, dispatch("GET", "name"));
        assertArrayEquals(payload, result.value());
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
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("name")));
        clock.advanceMillis(1);
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnListLengthWhenLpushIsExecuted() {
        assertEquals(new RespValue.IntegerValue(3), dispatch("LPUSH", "items", "A", "B", "C"));
        assertEquals(new RespValue.IntegerValue(4), dispatch("LPUSH", "items", "D"));
    }

    @Test
    void shouldReturnListLengthWhenRpushIsExecuted() {
        assertEquals(new RespValue.IntegerValue(3), dispatch("RPUSH", "items", "A", "B", "C"));
        assertEquals(new RespValue.IntegerValue(4), dispatch("RPUSH", "items", "D"));
    }

    @Test
    void shouldReturnWrongTypeWhenPushingToStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, dispatch("LPUSH", "items", "A"));
        assertEquals(expected, dispatch("RPUSH", "items", "B"));
        assertBulkStringEquals("original", dispatch("GET", "items"));
    }

    @Test
    void shouldRejectPushWhenNoValuesAreProvided() {
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for lpush command"),
                dispatch("LPUSH", "items"));
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for rpush command"),
                dispatch("RPUSH", "items"));
        assertFalse(storage.exists(bytes("items")));
    }

    @Test
    void shouldReturnFirstElementWhenLpopIsCalled() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertBulkStringEquals("A", dispatch("LPOP", "items"));
        assertBulkStringEquals("B", dispatch("LPOP", "items"));
    }

    @Test
    void shouldReturnLastElementWhenRpopIsCalled() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertBulkStringEquals("C", dispatch("RPOP", "items"));
        assertBulkStringEquals("B", dispatch("RPOP", "items"));
    }

    @Test
    void shouldRemoveKeyWhenLastElementIsPopped() {
        storage.rpush(bytes("left"), byteList("A"));
        storage.rpush(bytes("right"), byteList("B"));

        assertBulkStringEquals("A", dispatch("LPOP", "left"));
        assertEquals(new RespValue.IntegerValue(0), dispatch("EXISTS", "left"));
        assertBulkStringEquals("B", dispatch("RPOP", "right"));
        assertEquals(new RespValue.IntegerValue(0), dispatch("EXISTS", "right"));
    }

    @Test
    void shouldReturnEmptyWhenPoppingMissingKey() {
        assertEquals(new RespValue.BulkString(null), dispatch("LPOP", "missing"));
        assertEquals(new RespValue.BulkString(null), dispatch("RPOP", "missing"));
        assertEquals(new RespValue.IntegerValue(0), dispatch("EXISTS", "missing"));
    }

    @Test
    void shouldReturnWrongTypeWhenPoppingStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, dispatch("LPOP", "items"));
        assertEquals(expected, dispatch("RPOP", "items"));
        assertBulkStringEquals("original", dispatch("GET", "items"));
    }

    @Test
    void shouldReturnListLengthWhenLlenIsExecuted() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertEquals(new RespValue.IntegerValue(3), dispatch("LLEN", "items"));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyDoesNotExist() {
        assertEquals(new RespValue.IntegerValue(0), dispatch("LLEN", "missing"));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyHasExpired() {
        storage.rpush(bytes("items"), byteList("A"));
        assertEquals(1, storage.expire(bytes("items"), 1));
        clock.advanceMillis(1_000);

        assertEquals(new RespValue.IntegerValue(0), dispatch("LLEN", "items"));
    }

    @Test
    void shouldReturnWrongTypeWhenLlenKeyContainsStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, dispatch("LLEN", "items"));
        assertBulkStringEquals("original", dispatch("GET", "items"));
    }

    @Test
    void shouldRejectLlenWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'llen' command");

        assertEquals(expected, dispatch("LLEN"));
        assertEquals(expected, dispatch("LLEN", "items", "extra"));
    }

    @Test
    void shouldReturnBulkStringsInOrderForLrangeWithNegativeEndIndex() {
        storage.rpush(bytes("items"), byteList("A", "B", "\u4E2D"));

        RespValue.ArrayValue result = assertInstanceOf(
                RespValue.ArrayValue.class, dispatch("LRANGE", "items", "1", "-1"));

        assertEquals(2, result.elements().size());
        assertBulkStringEquals("B", result.elements().get(0));
        assertBulkStringEquals("\u4E2D", result.elements().get(1));
    }

    @Test
    void shouldReturnEmptyArrayWhenLrangeSelectsNoElements() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));
        RespValue.ArrayValue empty = new RespValue.ArrayValue(List.of());

        assertEquals(empty, dispatch("LRANGE", "missing", "0", "-1"));
        assertEquals(empty, dispatch("LRANGE", "items", "3", "-1"));
    }

    @Test
    void shouldReturnWrongTypeWhenLrangeKeyContainsStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, dispatch("LRANGE", "items", "0", "-1"));
        assertBulkStringEquals("original", dispatch("GET", "items"));
    }

    @Test
    void shouldRejectLrangeWithInvalidIndexes() {
        storage.rpush(bytes("items"), byteList("A"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue("ERR value is not an integer or out of range");

        assertEquals(expected, dispatch("LRANGE", "items", "not-an-integer", "0"));
        assertEquals(expected, dispatch("LRANGE", "items", "0", "9223372036854775808"));
    }

    @Test
    void shouldRejectLrangeWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'lrange' command");

        assertEquals(expected, dispatch("LRANGE", "items", "0"));
        assertEquals(expected, dispatch("LRANGE", "items", "0", "-1", "extra"));
    }

    @Test
    void shouldExtractAsciiTextWhenBulkStringIsValid() {
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(1), dispatch("EXPIRE", "name", "10"));
        assertEquals(new RespValue.IntegerValue(10), dispatch("TTL", "name"));
    }

    @Test
    void shouldRejectNonAsciiBytesWhenExtractingAsciiText() {
        storage.set(bytes("name"), bytes("Alice"));
        RespValue request = new RespValue.ArrayValue(List.of(
                bulkString("EXPIRE"), bulkString("name"), new RespValue.BulkString(new byte[]{(byte) 0xff})));

        assertEquals(new RespValue.ErrorValue("The Expire command is invalid"), dispatcher.dispatch(request));
        assertEquals(-1L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldRejectNonAsciiSimpleStringWhenExtractingAsciiText() {
        storage.set(bytes("name"), bytes("Alice"));
        RespValue request = new RespValue.ArrayValue(List.of(
                bulkString("EXPIRE"), bulkString("name"), new RespValue.SimpleString("\uFF11")));

        assertEquals(new RespValue.ErrorValue("The Expire command is invalid"), dispatcher.dispatch(request));
        assertEquals(-1L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldPreserveBinaryDataWhenExtractingRedisBytes() {
        RespValue.BulkString key = new RespValue.BulkString(new byte[]{0, (byte) 0xff});
        byte[] payload = {0, (byte) 0xfe, '\r', '\n'};
        RespValue push = new RespValue.ArrayValue(List.of(
                bulkString("RPUSH"), key, new RespValue.BulkString(payload)));
        RespValue pop = new RespValue.ArrayValue(List.of(bulkString("LPOP"), key));

        assertEquals(new RespValue.IntegerValue(1), dispatcher.dispatch(push));
        RespValue.BulkString result = assertInstanceOf(RespValue.BulkString.class, dispatcher.dispatch(pop));
        assertArrayEquals(payload, result.value());
    }

    @Test
    void shouldAddAllMembersWhenSaddHasMultipleMembers() {
        assertEquals(new RespValue.IntegerValue(3), dispatch("SADD", "members", "A", "B", "C", "A"));

        assertEquals(Set.of(bytes("A"), bytes("B"), bytes("C")), storage.smembers(bytes("members")));
    }

    @Test
    void shouldRemoveAllSpecifiedMembersWhenSremIsCalled() {
        storage.sadd(bytes("members"), byteList("A", "B", "C", "D"));

        assertEquals(new RespValue.IntegerValue(2), dispatch("SREM", "members", "A", "C", "missing", "A"));
        assertEquals(Set.of(bytes("B"), bytes("D")), storage.smembers(bytes("members")));
    }

    @Test
    void shouldReturnAllMembersWhenSmembersIsCalled() {
        Set<RedisBytes> expected = Set.of(bytes("A"), bytes("B"), new RedisBytes(new byte[]{0, (byte) 0xff}));
        storage.sadd(bytes("members"), List.copyOf(expected));

        RespValue.ArrayValue result = assertInstanceOf(RespValue.ArrayValue.class, dispatch("SMEMBERS", "members"));

        assertEquals(expected.size(), result.elements().size());
        Set<RedisBytes> actual = new HashSet<>();
        for (RespValue element : result.elements()) {
            RespValue.BulkString member = assertInstanceOf(RespValue.BulkString.class, element);
            actual.add(new RedisBytes(member.value()));
        }
        assertEquals(expected, actual);
    }

    @Test
    void shouldReturnEmptyArrayWhenSetDoesNotExist() {
        assertEquals(new RespValue.ArrayValue(List.of()), dispatch("SMEMBERS", "missing"));
    }

    @Test
    void shouldReturnWrongTypeWhenKeyContainsStringValue() {
        storage.set(bytes("members"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, dispatch("SADD", "members", "A"));
        assertEquals(expected, dispatch("SREM", "members", "A"));
        assertEquals(expected, dispatch("SMEMBERS", "members"));
        assertEquals(expected, dispatch("SCARD", "members"));
        assertBulkStringEquals("original", dispatch("GET", "members"));
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

    private static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<RedisBytes> byteList(String... values) {
        return Arrays.stream(values).map(CommandDispatcherTest::bytes).toList();
    }
}
