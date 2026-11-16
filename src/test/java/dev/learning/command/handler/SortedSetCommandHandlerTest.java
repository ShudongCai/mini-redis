package dev.learning.command.handler;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.learning.command.handler.CommandTestSupport.assertBulkStringEquals;
import static dev.learning.command.handler.CommandTestSupport.bytes;
import static dev.learning.command.handler.CommandTestSupport.command;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SortedSetCommandHandlerTest {
    private final InMemoryStorage storage = new InMemoryStorage();
    private final SortedSetCommandHandler handler = new SortedSetCommandHandler(storage);

    @Test
    void shouldAddMultipleScoreMemberPairsWhenZaddIsExecuted() {
        RespValue response = handler.executeZaddCommand(command("ZADD", "players", "2.5", "alice", "-1", "bob"));

        assertEquals(new RespValue.IntegerValue(2), response);
        assertEquals(Optional.of(2.5), storage.zscore(bytes("players"), bytes("alice")));
        assertEquals(Optional.of(-1.0), storage.zscore(bytes("players"), bytes("bob")));
    }

    @Test
    void shouldRejectZaddWhenScoreHasNoMember() {
        RespValue response = handler.executeZaddCommand(command("ZADD", "players", "1"));

        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'zadd' command"), response);
        assertFalse(storage.exists(bytes("players")));
    }

    @Test
    void shouldRejectNaNWithoutApplyingEarlierZaddPairs() {
        RespValue response = handler.executeZaddCommand(command("ZADD", "players", "1", "alice", "NaN", "bob"));

        assertEquals(new RespValue.ErrorValue("ERR value is not a valid float"), response);
        assertFalse(storage.exists(bytes("players")));
    }

    @Test
    void shouldReturnScoreAsBulkStringWhenZscoreIsExecuted() {
        storage.zadd(bytes("players"), Map.of(bytes("alice"), 2.5));

        assertBulkStringEquals("2.5", handler.executeZscoreCommand(command("ZSCORE", "players", "alice")));
    }

    @Test
    void shouldReturnNullBulkStringWhenZscoreMemberIsMissing() {
        storage.zadd(bytes("players"), Map.of(bytes("alice"), 2.5));

        assertEquals(new RespValue.BulkString(null), handler.executeZscoreCommand(command("ZSCORE", "players", "bob")));
        assertEquals(new RespValue.BulkString(null), handler.executeZscoreCommand(command("ZSCORE", "missing", "alice")));
    }

    @Test
    void shouldRejectZscoreWithoutMember() {
        RespValue response = handler.executeZscoreCommand(command("ZSCORE", "players"));

        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'zscore' command"), response);
    }

    @Test
    void shouldReturnNumberOfRemovedMembersWhenZremIsExecuted() {
        storage.zadd(bytes("players"), Map.of(bytes("alice"), 1.0, bytes("bob"), 2.0, bytes("carol"), 3.0));

        RespValue response = handler.executeZremCommand(command("ZREM", "players", "alice", "missing", "carol"));

        assertEquals(new RespValue.IntegerValue(2), response);
        assertEquals(List.of(bytes("bob")), storage.zrange(bytes("players"), 0, -1));
    }

    @Test
    void shouldRejectZremWithoutMembers() {
        RespValue response = handler.executeZremCommand(command("ZREM", "players"));

        assertEquals(new RespValue.ErrorValue("ERR wrong number of arguments for 'zrem' command"), response);
    }

    @Test
    void shouldReturnOrderedBulkStringsWhenZrangeUsesNegativeIndex() {
        storage.zadd(bytes("players"), Map.of(bytes("alice"), 2.0, bytes("bob"), 3.0, bytes("carol"), 1.0));

        RespValue.ArrayValue response = assertInstanceOf(RespValue.ArrayValue.class,
                handler.executeZrangeCommand(command("ZRANGE", "players", "1", "-1")));

        assertEquals(2, response.elements().size());
        assertBulkStringEquals("alice", response.elements().get(0));
        assertBulkStringEquals("bob", response.elements().get(1));
    }

    @Test
    void shouldReturnEmptyArrayWhenZrangeKeyDoesNotExist() {
        assertEquals(new RespValue.ArrayValue(List.of()),
                handler.executeZrangeCommand(command("ZRANGE", "missing", "0", "-1")));
    }

    @Test
    void shouldRejectZrangeWhenIndexOverflows() {
        RespValue response = handler.executeZrangeCommand(command("ZRANGE", "players", "0", "9223372036854775808"));

        assertEquals(new RespValue.ErrorValue("ERR range input for 'zrange' command"), response);
    }
}
