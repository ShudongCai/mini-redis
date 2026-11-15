package dev.learning.command.handler;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.learning.command.handler.CommandTestSupport.assertBulkStringEquals;
import static dev.learning.command.handler.CommandTestSupport.bulkString;
import static dev.learning.command.handler.CommandTestSupport.byteList;
import static dev.learning.command.handler.CommandTestSupport.bytes;
import static dev.learning.command.handler.CommandTestSupport.command;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ListCommandHandlerTest {
    private final StorageTestClock clock = new StorageTestClock();
    private final InMemoryStorage storage = clock.newStorage();
    private final ListCommandHandler handler = new ListCommandHandler(storage);

    @Test
    void shouldReturnListLengthWhenLpushIsExecuted() {
        assertEquals(new RespValue.IntegerValue(3),
                handler.executeLPushCommand(command("LPUSH", "items", "A", "B", "C")));
        assertEquals(new RespValue.IntegerValue(4), handler.executeLPushCommand(command("LPUSH", "items", "D")));
    }

    @Test
    void shouldReturnListLengthWhenRpushIsExecuted() {
        assertEquals(new RespValue.IntegerValue(3),
                handler.executeRPushCommand(command("RPUSH", "items", "A", "B", "C")));
        assertEquals(new RespValue.IntegerValue(4), handler.executeRPushCommand(command("RPUSH", "items", "D")));
    }

    @Test
    void shouldReturnWrongTypeWhenPushingToStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, handler.executeLPushCommand(command("LPUSH", "items", "A")));
        assertEquals(expected, handler.executeRPushCommand(command("RPUSH", "items", "B")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldRejectPushWhenNoValuesAreProvided() {
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for lpush command"),
                handler.executeLPushCommand(command("LPUSH", "items")));
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for rpush command"),
                handler.executeRPushCommand(command("RPUSH", "items")));
        assertFalse(storage.exists(bytes("items")));
    }

    @Test
    void shouldReturnFirstElementWhenLpopIsCalled() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertBulkStringEquals("A", handler.executeLPopCommand(command("LPOP", "items")));
        assertBulkStringEquals("B", handler.executeLPopCommand(command("LPOP", "items")));
    }

    @Test
    void shouldReturnLastElementWhenRpopIsCalled() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertBulkStringEquals("C", handler.executeRPopCommand(command("RPOP", "items")));
        assertBulkStringEquals("B", handler.executeRPopCommand(command("RPOP", "items")));
    }

    @Test
    void shouldRemoveKeyWhenLastElementIsPopped() {
        storage.rpush(bytes("left"), byteList("A"));
        storage.rpush(bytes("right"), byteList("B"));

        assertBulkStringEquals("A", handler.executeLPopCommand(command("LPOP", "left")));
        assertFalse(storage.exists(bytes("left")));
        assertBulkStringEquals("B", handler.executeRPopCommand(command("RPOP", "right")));
        assertFalse(storage.exists(bytes("right")));
    }

    @Test
    void shouldReturnEmptyWhenPoppingMissingKey() {
        assertEquals(new RespValue.BulkString(null), handler.executeLPopCommand(command("LPOP", "missing")));
        assertEquals(new RespValue.BulkString(null), handler.executeRPopCommand(command("RPOP", "missing")));
        assertFalse(storage.exists(bytes("missing")));
    }

    @Test
    void shouldReturnWrongTypeWhenPoppingStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, handler.executeLPopCommand(command("LPOP", "items")));
        assertEquals(expected, handler.executeRPopCommand(command("RPOP", "items")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldReturnListLengthWhenLlenIsExecuted() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));

        assertEquals(new RespValue.IntegerValue(3), handler.executeLlenCommand(command("LLEN", "items")));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyDoesNotExist() {
        assertEquals(new RespValue.IntegerValue(0), handler.executeLlenCommand(command("LLEN", "missing")));
    }

    @Test
    void shouldReturnZeroWhenLlenKeyHasExpired() {
        storage.rpush(bytes("items"), byteList("A"));
        assertEquals(1, storage.expire(bytes("items"), 1));
        clock.advanceMillis(1_000);

        assertEquals(new RespValue.IntegerValue(0), handler.executeLlenCommand(command("LLEN", "items")));
    }

    @Test
    void shouldReturnWrongTypeWhenLlenKeyContainsStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, handler.executeLlenCommand(command("LLEN", "items")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldRejectLlenWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'llen' command");

        assertEquals(expected, handler.executeLlenCommand(command("LLEN")));
        assertEquals(expected, handler.executeLlenCommand(command("LLEN", "items", "extra")));
    }

    @Test
    void shouldReturnBulkStringsInOrderForLrangeWithNegativeEndIndex() {
        storage.rpush(bytes("items"), byteList("A", "B", "\u4E2D"));

        RespValue.ArrayValue result = assertInstanceOf(
                RespValue.ArrayValue.class, handler.executeLrangeCommand(command("LRANGE", "items", "1", "-1")));

        assertEquals(2, result.elements().size());
        assertBulkStringEquals("B", result.elements().get(0));
        assertBulkStringEquals("\u4E2D", result.elements().get(1));
    }

    @Test
    void shouldReturnEmptyArrayWhenLrangeSelectsNoElements() {
        storage.rpush(bytes("items"), byteList("A", "B", "C"));
        RespValue.ArrayValue empty = new RespValue.ArrayValue(List.of());

        assertEquals(empty, handler.executeLrangeCommand(command("LRANGE", "missing", "0", "-1")));
        assertEquals(empty, handler.executeLrangeCommand(command("LRANGE", "items", "3", "-1")));
    }

    @Test
    void shouldReturnWrongTypeWhenLrangeKeyContainsStringValue() {
        storage.set(bytes("items"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, handler.executeLrangeCommand(command("LRANGE", "items", "0", "-1")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("items")));
    }

    @Test
    void shouldRejectLrangeWithInvalidIndexes() {
        storage.rpush(bytes("items"), byteList("A"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue("ERR value is not an integer or out of range");

        assertEquals(expected, handler.executeLrangeCommand(command("LRANGE", "items", "not-an-integer", "0")));
        assertEquals(expected, handler.executeLrangeCommand(command("LRANGE", "items", "0", "9223372036854775808")));
    }

    @Test
    void shouldRejectLrangeWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'lrange' command");

        assertEquals(expected, handler.executeLrangeCommand(command("LRANGE", "items", "0")));
        assertEquals(expected, handler.executeLrangeCommand(command("LRANGE", "items", "0", "-1", "extra")));
    }

    @Test
    void shouldRoundTripBinaryListKeyAndElement() {
        RespValue.BulkString key = new RespValue.BulkString(new byte[]{0, (byte) 0xff});
        byte[] payload = {0, (byte) 0xfe, '\r', '\n'};
        List<RespValue> push = List.of(bulkString("RPUSH"), key, new RespValue.BulkString(payload));
        List<RespValue> pop = List.of(bulkString("LPOP"), key);

        assertEquals(new RespValue.IntegerValue(1), handler.executeRPushCommand(push));
        RespValue.BulkString result = assertInstanceOf(RespValue.BulkString.class, handler.executeLPopCommand(pop));
        assertArrayEquals(payload, result.value());
    }

    @Test
    void shouldReturnErrorWhenListCommandHasInvalidKey() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue("ERR invalid key argument");

        RespValue invalidKey = new RespValue.IntegerValue(42);

        assertEquals(expected, handler.executeLlenCommand(List.of(bulkString("LLEN"), invalidKey)));
        assertEquals(expected, handler.executeLPopCommand(List.of(bulkString("LPOP"), invalidKey)));
        assertEquals(expected, handler.executeRPopCommand(List.of(bulkString("RPOP"), invalidKey)));
    }
}
