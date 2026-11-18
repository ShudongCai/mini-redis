package dev.learning;

import dev.learning.command.CommandDispatcher;
import dev.learning.persistence.AofCommandWrapper;
import dev.learning.persistence.AofService;
import dev.learning.persistence.model.SnapshotEntry;
import dev.learning.persistence.snapshot.SnapshotReader;
import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.protocol.RespValue;
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
import java.util.function.Function;

public class App {
    public static void main(String[] args) throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        Path recoveryPath = Path.of("data", "snapshot.bin");

        // Recovery
        if (Files.exists(recoveryPath)) {
            try (InputStream inputStream = Files.newInputStream(recoveryPath)) {
                SnapshotReader snapshotReader = new SnapshotReader(inputStream);
                List<SnapshotEntry> entries = snapshotReader.read();

                storage.restoreSnapshot(entries);
            }
        }

        // Server initialization
        RespDecoder decoder = new RespDecoder();
        RespEncoder encoder = new RespEncoder();

        ReentrantReadWriteLock snapshotLock =
                new ReentrantReadWriteLock(true);

        CommandDispatcher dispatcher =
                new CommandDispatcher(storage, snapshotLock);

        AofCommandWrapper aofCommandWrapper = new AofCommandWrapper(dispatcher, new AofService());

        boolean enableAof = false;

        Function<RespValue, RespValue> executor = dispatcher::dispatch;

        if (enableAof) {
            AofService aofService = new AofService();

            AofCommandWrapper wrapper =
                    new AofCommandWrapper(dispatcher, aofService);

            executor = wrapper::dispatch;
        }

        try (
                BackgroundScheduler scheduler = new BackgroundScheduler();
                MiniRedisServer server =
                        new MiniRedisServer(16380, decoder, encoder, aofCommandWrapper::dispatch)
        ) {
            scheduler.scheduledWithFixedDelay(
                    storage.expirationCleanupTask(snapshotLock),
                    1,
                    5,
                    TimeUnit.SECONDS
            );

            server.acceptAndResponse();
        }
    }
}
