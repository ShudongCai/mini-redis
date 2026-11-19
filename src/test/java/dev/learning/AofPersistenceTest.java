package dev.learning;

import dev.learning.command.CommandDispatcher;
import dev.learning.command.handler.SnapshotCommandHandler;
import dev.learning.persistence.AofService;
import dev.learning.persistence.aof.AofReader;
import dev.learning.persistence.aof.AofWriter;
import dev.learning.persistence.model.AofEntry;
import dev.learning.persistence.model.SnapshotEntry;
import dev.learning.persistence.snapshot.SnapshotReader;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.StringValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static dev.learning.storage.StorageTestSupport.bytes;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class AofPersistenceTest {
    @TempDir
    Path directory;

    @Test
    void shouldRoundTripRecordsWhenWritingAndReadingAof() throws IOException {
        RedisBytes key = new RedisBytes(new byte[]{0, (byte) 0xff});
        RedisBytes value = new RedisBytes(new byte[]{(byte) 0xfe, 0});
        AofEntry replacement = new AofEntry.Replace(new SnapshotEntry(key, new StringValue(value), 12_345L));
        AofEntry deletion = new AofEntry.Delete(key);
        try (AofWriter writer = new AofWriter(FileChannel.open(logPath(), CREATE_NEW, WRITE))) {
            writer.append(replacement);
            writer.append(deletion);
        }

        List<AofEntry> replayed = new ArrayList<>();
        try (FileChannel channel = FileChannel.open(logPath())) {
            assertEquals(channel.size(), new AofReader().replay(channel, replayed::add));
        }

        assertEquals(List.of(replacement, deletion), replayed);
    }

    @Test
    void shouldRestoreAllValueTypesWhenAofIsReplayed() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        RedisBytes binary = new RedisBytes(new byte[]{0, (byte) 0xff, 13, 10});
        try (AofService aof = open(storage)) {
            CommandDispatcher dispatcher = dispatcher(storage, aof);
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, bytes("SET"), binary, binary));

            assertEquals(new RespValue.IntegerValue(3),
                    dispatch(dispatcher, bytes("RPUSH"), bytes("list"), binary, bytes("b"), binary));
            assertEquals(new RespValue.IntegerValue(4), dispatch(dispatcher, "LPUSH", "list", "discard"));
            assertInstanceOf(RespValue.BulkString.class, dispatch(dispatcher, "LPOP", "list"));
            assertEquals(new RespValue.IntegerValue(4), dispatch(dispatcher, "RPUSH", "list", "discard"));
            assertInstanceOf(RespValue.BulkString.class, dispatch(dispatcher, "RPOP", "list"));

            assertEquals(new RespValue.IntegerValue(2),
                    dispatch(dispatcher, bytes("SADD"), bytes("set"), binary, bytes("a")));
            assertEquals(new RespValue.IntegerValue(1), dispatch(dispatcher, "SREM", "set", "a"));

            assertEquals(new RespValue.IntegerValue(3),
                    dispatch(dispatcher, "ZADD", "zset", "1.5", "a", "2", "b", "3", "discard"));
            assertEquals(new RespValue.IntegerValue(0), dispatch(dispatcher, "ZADD", "zset", "4.5", "a"));
            assertEquals(new RespValue.IntegerValue(1), dispatch(dispatcher, "ZREM", "zset", "discard"));
        }

        InMemoryStorage recovered = new InMemoryStorage();
        try (AofService ignored = open(recovered)) {
            assertEquals(Optional.of(binary), recovered.get(binary));
            assertEquals(List.of(binary, bytes("b"), binary), recovered.lrange(bytes("list"), 0, -1));
            assertEquals(Set.of(binary), recovered.smembers(bytes("set")));
            assertEquals(List.of(bytes("b"), bytes("a")), recovered.zrange(bytes("zset"), 0, -1));
            assertEquals(Optional.of(4.5), recovered.zscore(bytes("zset"), bytes("a")));
        }
    }

    @Test
    void shouldPreserveExpirationAndSupersedeOlderValuesWhenAofIsRecovered() throws IOException {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        try (AofService aof = open(storage)) {
            CommandDispatcher dispatcher = dispatcher(storage, aof);
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "live", "first"));
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "live", "second", "EX", "10"));
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "expired", "older"));
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "expired", "newer", "EX", "2"));
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "deleted", "old"));
            assertEquals(new RespValue.IntegerValue(1), dispatch(dispatcher, "DEL", "deleted"));
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "persistent", "value"));
            assertEquals(new RespValue.IntegerValue(1), dispatch(dispatcher, "EXPIRE", "persistent", "4"));
            assertEquals(new RespValue.IntegerValue(1), dispatch(dispatcher, "PERSIST", "persistent"));
        }

        clock.advanceMillis(5_000);
        InMemoryStorage recovered = clock.newStorage();
        try (AofService ignored = open(recovered)) {
            assertEquals(Optional.of(bytes("second")), recovered.get(bytes("live")));
            assertEquals(5, recovered.ttl(bytes("live")));
            assertFalse(recovered.exists(bytes("expired")));
            assertFalse(recovered.exists(bytes("deleted")));
            assertEquals(Optional.of(bytes("value")), recovered.get(bytes("persistent")));
            assertEquals(-1, recovered.ttl(bytes("persistent")));
        }
    }

    @Test
    void shouldKeepLogUnchangedWhenCommandsFail() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        try (AofService aof = open(storage)) {
            CommandDispatcher dispatcher = dispatcher(storage, aof);
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "key", "old"));
            long before = Files.size(logPath());

            assertInstanceOf(RespValue.ErrorValue.class, dispatch(dispatcher, "SET", "key"));
            assertInstanceOf(RespValue.ErrorValue.class, dispatch(dispatcher, "LPUSH", "key", "wrong type"));
            assertInstanceOf(RespValue.ErrorValue.class, dispatch(dispatcher, "SAVE"));

            assertEquals(before, Files.size(logPath()));
            assertEquals(new RespValue.BulkString(null), dispatch(dispatcher, "SET", "key", "ignored", "NX"));
        }
        InMemoryStorage recovered = new InMemoryStorage();
        try (AofService ignored = open(recovered)) {
            assertEquals(Optional.of(bytes("old")), recovered.get(bytes("key")));
        }
    }

    @Test
    void shouldPreserveListOrderWhenConcurrentWritesAreRecovered() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        List<RedisBytes> order;
        try (AofService aof = open(storage);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CommandDispatcher dispatcher = dispatcher(storage, aof);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<RespValue>> writes = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                String value = Integer.toString(i);
                writes.add(executor.submit(() -> {
                    assertTrue(start.await(5, SECONDS));
                    return dispatch(dispatcher, "RPUSH", "shared", value);
                }));
            }
            start.countDown();
            for (Future<RespValue> write : writes) {
                assertInstanceOf(RespValue.IntegerValue.class, write.get(15, SECONDS));
            }
            order = storage.lrange(bytes("shared"), 0, -1);
            assertEquals(24, order.size());
        }

        InMemoryStorage recovered = new InMemoryStorage();
        try (AofService ignored = open(recovered)) {
            assertEquals(order, recovered.lrange(bytes("shared"), 0, -1));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 13})
    void shouldDiscardIncompleteFinalRecordWhenAofIsReopened(int bytesOfTail) throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        long completeEnd;
        try (AofService aof = open(storage)) {
            CommandDispatcher dispatcher = dispatcher(storage, aof);
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "key", "complete"));
            completeEnd = Files.size(logPath());
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "key", "cut off"));
        }
        try (FileChannel channel = FileChannel.open(logPath(), WRITE)) {
            channel.truncate(completeEnd + bytesOfTail);
        }

        InMemoryStorage recovered = new InMemoryStorage();
        try (AofService aof = open(recovered)) {
            assertEquals(completeEnd, Files.size(logPath()));
            assertEquals(Optional.of(bytes("complete")), recovered.get(bytes("key")));
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher(recovered, aof), "SET", "key", "next"));
        }
        InMemoryStorage latest = new InMemoryStorage();
        try (AofService ignored = open(latest)) {
            assertEquals(Optional.of(bytes("next")), latest.get(bytes("key")));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 13})
    void shouldRejectCorruptedCompleteRecordWhenAofIsReopened(int offset) throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        try (AofService aof = open(storage)) {
            assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher(storage, aof), "SET", "key", "value"));
        }
        byte[] corrupted = Files.readAllBytes(logPath());
        corrupted[offset] ^= 1;
        Files.write(logPath(), corrupted);

        assertThrows(IOException.class, () -> open(new InMemoryStorage()));
        assertArrayEquals(corrupted, Files.readAllBytes(logPath()));
    }

    @Test
    void shouldStartEmptyWhenAofFileIsMissing() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        assertFalse(Files.exists(logPath()));
        try (AofService ignored = open(storage)) {
            assertTrue(Files.isRegularFile(logPath()));
            assertEquals(0, Files.size(logPath()));
            assertTrue(storage.exportSnapshot().isEmpty());
        }
    }

    @Test
    void shouldRejectOversizedRecordWhenAofIsReopened() throws IOException {
        byte[] invalid = ByteBuffer.allocate(12)
                .putInt(0x4d524101).putInt(Integer.MAX_VALUE).putInt(~Integer.MAX_VALUE).array();
        Files.write(logPath(), invalid);

        assertThrows(IOException.class, () -> open(new InMemoryStorage()));
        assertArrayEquals(invalid, Files.readAllBytes(logPath()));
    }

    @Test
    void shouldUseSnapshotPersistenceWhenAofIsDisabled() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        CommandDispatcher dispatcher = new CommandDispatcher(storage, new ReentrantReadWriteLock());
        Path snapshot = directory.resolve("snapshot.bin");
        assertEquals(new RespValue.SimpleString("OK"), dispatch(dispatcher, "SET", "key", "value"));

        assertEquals(new RespValue.SimpleString("OK"), new SnapshotCommandHandler(storage, snapshot)
                .executeSaveCommand(List.of(new RespValue.SimpleString("SAVE"))));
        assertFalse(Files.exists(logPath()));

        InMemoryStorage recovered = new InMemoryStorage();
        try (var input = Files.newInputStream(snapshot)) {
            recovered.restoreSnapshot(new SnapshotReader(input).read());
        }
        assertEquals(Optional.of(bytes("value")), recovered.get(bytes("key")));
    }

    private AofService open(InMemoryStorage storage) throws IOException {
        return AofService.open(logPath(), entry -> App.replay(storage, entry));
    }

    private Path logPath() {
        return directory.resolve("appendonly.aof");
    }

    private static CommandDispatcher dispatcher(InMemoryStorage storage, AofService aof) {
        return new CommandDispatcher(storage, new ReentrantReadWriteLock(true), aof);
    }

    private static RespValue dispatch(CommandDispatcher dispatcher, String... arguments) {
        return dispatch(dispatcher, Arrays.stream(arguments).map(value -> bytes(value)).toArray(RedisBytes[]::new));
    }

    private static RespValue dispatch(CommandDispatcher dispatcher, RedisBytes... arguments) {
        List<RespValue> elements = Arrays.stream(arguments)
                .<RespValue>map(value -> new RespValue.BulkString(value.getBytes()))
                .toList();
        return dispatcher.dispatch(new RespValue.ArrayValue(elements));
    }
}
