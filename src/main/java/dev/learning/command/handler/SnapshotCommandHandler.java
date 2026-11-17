package dev.learning.command.handler;

import dev.learning.persistence.SnapshotEntry;
import dev.learning.persistence.SnapshotWriter;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SnapshotCommandHandler {
    private final InMemoryStorage inMemoryStorage;
    private final Path snapshotPath;

    public SnapshotCommandHandler(InMemoryStorage inMemoryStorage,
                                  Path snapshotPath) {
        this.inMemoryStorage = inMemoryStorage;
        this.snapshotPath = snapshotPath;
    }

    public SnapshotCommandHandler(InMemoryStorage inMemoryStorage) {
        this(inMemoryStorage, Path.of("data", "snapshot.bin"));
    }

    public RespValue executeSaveCommand() throws IOException {
        List<SnapshotEntry> snapshots = inMemoryStorage.exportSnapshot();

        Files.createDirectories(snapshotPath.getParent());
        try (OutputStream outputStream = Files.newOutputStream(snapshotPath)) {
            SnapshotWriter snapshotWriter = new SnapshotWriter(outputStream);
            snapshotWriter.write(snapshots);
        }
        return new RespValue.SimpleString("OK");
    }
}
