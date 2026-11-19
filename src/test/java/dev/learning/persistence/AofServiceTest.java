package dev.learning.persistence;

import dev.learning.command.CommandDispatcher;
import dev.learning.persistence.aof.AofWriter;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static dev.learning.storage.StorageTestSupport.bytes;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class AofServiceTest {
    @TempDir
    Path directory;

    @Test
    void shouldRejectFurtherMutationsWhenAofWriteFails() throws IOException {
        FileChannel channel = FileChannel.open(directory.resolve("test.aof"), CREATE_NEW, WRITE);
        try (channel; AofService aof = new AofService(new AofWriter(channel))) {
            InMemoryStorage storage = new InMemoryStorage();
            ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
            CommandDispatcher dispatcher = new CommandDispatcher(storage, lock, aof);
            channel.close();

            assertEquals(new RespValue.ErrorValue("ERR AOF persistence failed, command result is uncertain"),
                    dispatcher.dispatch(request("SET", "key", "first")));
            assertFalse(lock.isWriteLocked());
            assertFalse(aof.isAvailable());
            assertEquals(new RespValue.ErrorValue("ERR AOF persistence is unavailable"),
                    dispatcher.dispatch(request("SET", "key", "second")));
            assertEquals(new RespValue.ErrorValue("ERR AOF persistence is unavailable"),
                    dispatcher.dispatch(request("GET", "key")));
            assertEquals(Optional.of(bytes("first")), storage.get(bytes("key")));
        }
    }

    @Test
    void shouldKeepFileLockedWhenSecondWriterIsRejected() throws Exception {
        Path path = directory.resolve("test.aof");
        try (AofService owner = AofService.open(path, entry -> fail("Unexpected record"))) {
            assertThrows(IOException.class, () -> AofService.open(path, entry -> fail("Unexpected record")));

            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            Process contender = new ProcessBuilder(javaExecutable.toString(),
                    "-cp", System.getProperty("java.class.path"),
                    LockProbe.class.getName(), path.toString())
                    .redirectErrorStream(true)
                    .start();
            try {
                assertTrue(contender.waitFor(10, SECONDS), "AOF lock probe timed out");
                String output = new String(contender.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(0, contender.exitValue(), output);
            } finally {
                contender.destroyForcibly();
            }
        }
    }

    public static class LockProbe {
        public static void main(String[] args) throws IOException {
            try (AofService ignored = AofService.open(Path.of(args[0]), entry -> {})) {
                throw new AssertionError("A second process acquired a live writer's AOF file");
            } catch (IOException e) {
                if (!e.getMessage().startsWith("AOF file is already in use")) {
                    throw e;
                }
            }
        }
    }

    private static RespValue.ArrayValue request(String... arguments) {
        return new RespValue.ArrayValue(List.of(arguments).stream()
                .<RespValue>map(value -> new RespValue.SimpleString(value))
                .toList());
    }
}
