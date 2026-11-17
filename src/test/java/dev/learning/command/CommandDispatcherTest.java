package dev.learning.command;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommandDispatcherTest {
    private final StorageTestClock clock = new StorageTestClock();
    private final InMemoryStorage storage = clock.newStorage();
    private final ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock(true);
    private final CommandDispatcher dispatcher = new CommandDispatcher(storage, reentrantReadWriteLock);

    @Test
    void shouldReturnPongWhenPingIsExecuted() {
        RespValue.SimpleString pong = new RespValue.SimpleString("PONG");

        assertEquals(pong, dispatch("PING"));
        assertEquals(pong, dispatcher.dispatch(new RespValue.SimpleString("ping")));
        assertEquals(pong, dispatcher.dispatch(bulkString("PING")));
    }

    @Test
    void shouldReturnErrorWhenCommandIsUnknown() {
        assertEquals(new RespValue.ErrorValue("Invalid Command type"), dispatch("UNKNOWN"));
    }

    @Test
    void shouldRejectPingWithWrongArgumentCount() {
        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'ping' command"),
                dispatch("PING", "extra"));
    }

    @Test
    void shouldRouteCommandsToTheirHandlers() {
        assertEquals(new RespValue.SimpleString("OK"), dispatch("sEt", "name", "Alice", "EX", "10"));
        assertEquals(new RespValue.IntegerValue(10), dispatch("TTL", "name"));
        assertEquals(new RespValue.IntegerValue(2), dispatch("RPUSH", "items", "A", "B"));
        assertEquals(new RespValue.IntegerValue(1), dispatch("SADD", "members", "A", "A"));
    }

    @Test
    void shouldReturnErrorWhenCommandArrayIsEmpty() {
        RespValue result = dispatcher.dispatch(new RespValue.ArrayValue(List.of()));

        assertEquals(new RespValue.ErrorValue("The command should not be empty"), result);
    }

    @Test
    void shouldExecuteValidCommandAfterInvalidCommand() {
        RespValue invalid = dispatch("SET", "name");

        assertInstanceOf(RespValue.ErrorValue.class, invalid);

        RespValue valid = dispatch("SET", "name", "Alice");

        assertEquals(new RespValue.SimpleString("OK"), valid);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
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

    private static RedisBytes bytes(String value) {
        return new RedisBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
