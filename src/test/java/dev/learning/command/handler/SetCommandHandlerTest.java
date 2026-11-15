package dev.learning.command.handler;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.learning.command.handler.CommandTestSupport.bulkString;
import static dev.learning.command.handler.CommandTestSupport.byteList;
import static dev.learning.command.handler.CommandTestSupport.bytes;
import static dev.learning.command.handler.CommandTestSupport.command;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SetCommandHandlerTest {
    private final InMemoryStorage storage = new InMemoryStorage();
    private final SetCommandHandler handler = new SetCommandHandler(storage);

    @Test
    void shouldAddAllMembersWhenSaddHasMultipleMembers() {
        assertEquals(new RespValue.IntegerValue(3),
                handler.executeSaddCommand(command("SADD", "members", "A", "B", "C", "A")));

        assertEquals(Set.of(bytes("A"), bytes("B"), bytes("C")), storage.smembers(bytes("members")));
    }

    @Test
    void shouldRemoveAllSpecifiedMembersWhenSremIsCalled() {
        storage.sadd(bytes("members"), byteList("A", "B", "C", "D"));

        assertEquals(new RespValue.IntegerValue(2),
                handler.executeSremCommand(command("SREM", "members", "A", "C", "missing", "A")));
        assertEquals(Set.of(bytes("B"), bytes("D")), storage.smembers(bytes("members")));
    }

    @Test
    void shouldReturnAllMembersWhenSmembersIsCalled() {
        Set<RedisBytes> expected = Set.of(bytes("A"), bytes("B"), new RedisBytes(new byte[]{0, (byte) 0xff}));
        storage.sadd(bytes("members"), List.copyOf(expected));

        RespValue.ArrayValue result = assertInstanceOf(RespValue.ArrayValue.class,
                handler.executeSmembersCommand(command("SMEMBERS", "members")));

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
        assertEquals(new RespValue.ArrayValue(List.of()),
                handler.executeSmembersCommand(command("SMEMBERS", "missing")));
    }

    @Test
    void shouldReturnWrongTypeWhenKeyContainsStringValue() {
        storage.set(bytes("members"), bytes("original"));
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "WRONGTYPE Operation against a key holding the wrong kind of value");

        assertEquals(expected, handler.executeSaddCommand(command("SADD", "members", "A")));
        assertEquals(expected, handler.executeSremCommand(command("SREM", "members", "A")));
        assertEquals(expected, handler.executeSmembersCommand(command("SMEMBERS", "members")));
        assertEquals(expected, handler.executeScardCommand(command("SCARD", "members")));
        assertEquals(expected, handler.executeSismemberCommand(command("SISMEMBER", "members", "A")));
        assertEquals(Optional.of(bytes("original")), storage.get(bytes("members")));
    }

    @Test
    void shouldFindBinaryMemberWhenSismemberIsCalled() {
        byte[] member = {0, (byte) 0xff};
        storage.sadd(bytes("members"), List.of(new RedisBytes(member)));
        List<RespValue> request = List.of(
                bulkString("SISMEMBER"), bulkString("members"), new RespValue.BulkString(member));

        assertEquals(new RespValue.IntegerValue(1), handler.executeSismemberCommand(request));
    }

    @Test
    void shouldReturnZeroForMissingSetOrMemberWhenSismemberIsCalled() {
        storage.sadd(bytes("members"), byteList("A"));
        RespValue.IntegerValue zero = new RespValue.IntegerValue(0);

        assertEquals(zero, handler.executeSismemberCommand(command("SISMEMBER", "missing", "A")));
        assertEquals(zero, handler.executeSismemberCommand(command("SISMEMBER", "members", "B")));
    }

    @Test
    void shouldRejectSismemberWithWrongArgumentCount() {
        RespValue.ErrorValue expected = new RespValue.ErrorValue(
                "ERR wrong number of arguments for 'sismember' command");

        assertEquals(expected, handler.executeSismemberCommand(command("SISMEMBER", "members")));
        assertEquals(expected, handler.executeSismemberCommand(command("SISMEMBER", "members", "A", "B")));
    }

    @Test
    void shouldRejectSismemberWithInvalidKeyOrMember() {
        RespValue command = bulkString("SISMEMBER");
        RespValue invalid = new RespValue.IntegerValue(42);
        List<RespValue> invalidKey = List.of(command, invalid, bulkString("A"));
        List<RespValue> invalidMember = List.of(command, bulkString("members"), invalid);

        assertEquals(new RespValue.ErrorValue("ERR invalid key argument"), handler.executeSismemberCommand(invalidKey));
        assertEquals(new RespValue.ErrorValue("ERR invalid value argument"),
                handler.executeSismemberCommand(invalidMember));
    }

    @Test
    void shouldLeaveSetUnchangedWhenBatchContainsInvalidMember() {
        RedisBytes key = bytes("members");
        Set<RedisBytes> expected = Set.of(bytes("A"), bytes("B"));
        storage.sadd(key, List.copyOf(expected));
        RespValue invalid = new RespValue.IntegerValue(42);
        List<RespValue> add = List.of(bulkString("SADD"), bulkString("members"), bulkString("C"), invalid);
        List<RespValue> remove = List.of(bulkString("SREM"), bulkString("members"), bulkString("A"), invalid);

        assertInstanceOf(RespValue.ErrorValue.class, handler.executeSaddCommand(add));
        assertEquals(expected, storage.smembers(key));
        assertInstanceOf(RespValue.ErrorValue.class, handler.executeSremCommand(remove));
        assertEquals(expected, storage.smembers(key));
    }
}
