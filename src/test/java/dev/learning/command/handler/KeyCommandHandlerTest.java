package dev.learning.command.handler;

import dev.learning.command.SetOptions;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static dev.learning.command.handler.CommandTestSupport.bulkString;
import static dev.learning.command.handler.CommandTestSupport.bytes;
import static dev.learning.command.handler.CommandTestSupport.command;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class KeyCommandHandlerTest {
    private final StorageTestClock clock = new StorageTestClock();
    private final InMemoryStorage storage = clock.newStorage();
    private final KeyCommandHandler handler = new KeyCommandHandler(storage);

    @Test
    void shouldReturnMinusTwoForMissingTtlKey() {
        assertEquals(new RespValue.IntegerValue(-2), handler.executeTTLCommand(command("TTL", "missing")));
    }

    @Test
    void shouldReturnMinusOneForPersistentTtlKey() {
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(-1), handler.executeTTLCommand(command("TTL", "name")));
    }

    @ParameterizedTest(name = "{0} ms remaining returns RESP integer {1}")
    @CsvSource({"10000, 10", "9600, 10", "9400, 9", "400, 0", "0, -2"})
    void shouldReturnTtlForExpiringKey(long remainingMillis, long expectedSeconds) {
        storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build());
        clock.advanceMillis(10_000 - remainingMillis);

        assertEquals(new RespValue.IntegerValue(expectedSeconds), handler.executeTTLCommand(command("TTL", "name")));
    }

    @Test
    void shouldRejectTtlWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue("ERR wrong number of arguments for 'ttl' command");

        assertEquals(expected, handler.executeTTLCommand(command("TTL")));
        assertEquals(expected, handler.executeTTLCommand(command("TTL", "name", "extra")));
    }

    @Test
    void shouldRejectTtlWithInvalidKeyType() {
        List<RespValue> request = List.of(bulkString("TTL"), new RespValue.IntegerValue(123));

        assertInstanceOf(RespValue.ErrorValue.class, handler.executeTTLCommand(request));
    }

    @Test
    void shouldReturnZeroWhenExpireKeyIsMissing() {
        assertEquals(new RespValue.IntegerValue(0), handler.executeExpireCommand(command("EXPIRE", "missing", "10")));
        assertEquals(new RespValue.IntegerValue(-2), handler.executeTTLCommand(command("TTL", "missing")));
    }

    @ParameterizedTest(name = "EXPIRE {0} on an expired key returns RESP integer zero")
    @ValueSource(strings = {"10", "0"})
    void shouldReturnZeroWhenExpireKeyIsAlreadyExpired(String seconds) {
        storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build());
        clock.advanceMillis(1_000);

        assertEquals(new RespValue.IntegerValue(0), handler.executeExpireCommand(command("EXPIRE", "name", seconds)));
        assertEquals(new RespValue.IntegerValue(-2), handler.executeTTLCommand(command("TTL", "name")));
    }

    @Test
    void shouldSetExpirationOnPersistentKeyWithoutChangingItsValue() {
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(1), handler.executeExpireCommand(command("EXPIRE", "name", "10")));
        assertEquals(new RespValue.IntegerValue(10), handler.executeTTLCommand(command("TTL", "name")));
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldReplaceExistingExpirationRelativeToCurrentTime() {
        storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build());
        clock.advanceMillis(4_000);

        assertEquals(new RespValue.IntegerValue(1), handler.executeExpireCommand(command("EXPIRE", "name", "20")));
        assertEquals(new RespValue.IntegerValue(20), handler.executeTTLCommand(command("TTL", "name")));
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @ParameterizedTest(name = "EXPIRE {0} deletes a live key")
    @ValueSource(strings = {"0", "-5"})
    void shouldDeleteLiveKeyWhenExpireTimeIsNonPositive(String seconds) {
        storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build());

        assertEquals(new RespValue.IntegerValue(1), handler.executeExpireCommand(command("EXPIRE", "name", seconds)));
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @ParameterizedTest(name = "EXPIRE {0} returns an error and preserves the entry")
    @ValueSource(strings = {"9223372036854775808", "9223372036854775807", "9223372036854775"})
    void shouldReturnErrorAndPreserveValueAndDeadlineWhenExpireOverflows(String seconds) {
        clock.advanceMillis(1_000);
        storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(10L).build());

        assertInstanceOf(RespValue.ErrorValue.class, handler.executeExpireCommand(command("EXPIRE", "name", seconds)));
        assertEquals(new RespValue.IntegerValue(10), handler.executeTTLCommand(command("TTL", "name")));
        clock.advanceMillis(9_999);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
        clock.advanceMillis(1);
        assertEquals(new RespValue.IntegerValue(-2), handler.executeTTLCommand(command("TTL", "name")));
    }

    @Test
    void shouldReturnOneWhenKeyExists() {
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(1), handler.executeExistCommand(command("EXISTS", "name")));
    }

    @Test
    void shouldReturnZeroWhenKeyDoesNotExist() {
        storage.set(bytes("name"), bytes("Alice"));

        assertEquals(new RespValue.IntegerValue(0), handler.executeExistCommand(command("EXISTS", "missing")));
    }

    @Test
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = handler.executeDelCommand(command("DEL", "name"));

        assertEquals(new RespValue.IntegerValue(1), result);
        assertFalse(storage.exists(bytes("name")));
    }

    @Test
    void shouldReturnZeroWhenNonExistingKeyIsDeleted() {
        storage.set(bytes("name"), bytes("Alice"));

        RespValue result = handler.executeDelCommand(command("DEL", "missing"));

        assertEquals(new RespValue.IntegerValue(0), result);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectNonAsciiExpireArgumentWithoutChangingTtl(boolean useBulkString) {
        storage.set(bytes("name"), bytes("Alice"));
        RespValue seconds = useBulkString
                ? new RespValue.BulkString(new byte[]{(byte) 0xff})
                : new RespValue.SimpleString("\uFF11");
        List<RespValue> request = List.of(bulkString("EXPIRE"), bulkString("name"), seconds);

        assertEquals(new RespValue.ErrorValue("The Expire command is invalid"), handler.executeExpireCommand(request));
        assertEquals(-1L, storage.ttl(bytes("name")));
    }

    @Test
    void shouldRejectExistsWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'exists' command");

        assertEquals(expected, handler.executeExistCommand(command("EXISTS")));
    }

    @Test
    void shouldRejectDelWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'del' command");

        assertEquals(expected, handler.executeDelCommand(command("DEL")));
    }
}
