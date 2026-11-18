package dev.learning;

import dev.learning.command.CommandDispatcher;
import dev.learning.persistence.AofService;
import dev.learning.persistence.model.AofEntry;
import dev.learning.persistence.snapshot.SnapshotReader;
import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.scheduler.BackgroundScheduler;
import dev.learning.server.MiniRedisServer;
import dev.learning.storage.InMemoryStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Recovers the selected persistence mode before starting the server.
 */
public class App {
    public static void main(String[] args) throws IOException {
        boolean enableAof = args.length == 1 && args[0].equals("--aof");
        if (args.length != 0 && !enableAof) {
            throw new IllegalArgumentException("Usage: mini-redis-server [--aof]");
        }

        Path snapshotPath = Path.of("data", "snapshot.bin");
        Path aofPath = Path.of("data", "appendonly.aof");
        Path otherModePath = enableAof ? snapshotPath : aofPath;
        if (!Files.notExists(otherModePath)) {
            throw new IOException("Persistence mode conflicts with " + otherModePath);
        }

        InMemoryStorage storage = new InMemoryStorage();
        ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock(true);
        if (!enableAof && Files.exists(snapshotPath)) {
            try (InputStream input = Files.newInputStream(snapshotPath)) {
                storage.restoreSnapshot(new SnapshotReader(input).read());
            }
        }

        try (AofService aof = enableAof ? AofService.open(aofPath, entry -> replay(storage, entry)) : null) {
            CommandDispatcher dispatcher = new CommandDispatcher(storage, snapshotLock, aof);
            try (BackgroundScheduler scheduler = new BackgroundScheduler();
                 MiniRedisServer server = new MiniRedisServer(
                         16380, new RespDecoder(), new RespEncoder(), dispatcher::dispatch)) {
                scheduler.scheduledWithFixedDelay(
                        storage.expirationCleanupTask(snapshotLock), 1, 5, TimeUnit.SECONDS);

                Thread shutdownHook = aof == null ? null : new Thread(() -> {
                    try {
                        closeAof(aof, snapshotLock);
                    } catch (IOException e) {
                        System.err.println("AOF close failed: " + e.getMessage());
                    }
                }, "aof-shutdown");
                if (shutdownHook != null) {
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                }

                try {
                    server.acceptAndResponse();
                } finally {
                    if (shutdownHook != null) {
                        try {
                            closeAof(aof, snapshotLock);
                        } finally {
                            try {
                                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                            } catch (IllegalStateException ignored) {
                                // JVM shutdown is already running the hook.
                            }
                        }
                    }
                }
            }
        }
    }

    static void replay(InMemoryStorage storage, AofEntry entry) {
        switch (entry) {
            case AofEntry.Replace replace -> {
                // An expired replacement also supersedes the earlier value.
                storage.del(replace.entry().key());
                storage.restoreSnapshot(List.of(replace.entry()));
            }
            case AofEntry.Delete delete -> storage.del(delete.key());
        }
    }

    private static void closeAof(AofService aof, ReentrantReadWriteLock snapshotLock) throws IOException {
        var lock = snapshotLock.writeLock();
        lock.lock();
        try {
            aof.close();
        } finally {
            lock.unlock();
        }
    }
}
