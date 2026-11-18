package dev.learning.command.handler;

import dev.learning.persistence.model.SnapshotEntry;
import dev.learning.persistence.snapshot.SnapshotWriter;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Handles {@code SAVE} by atomically replacing the snapshot file while the caller holds the persistence write lock.
 */
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

    public RespValue executeSaveCommand(List<RespValue> elements) throws IOException {
        if (elements.size() != 1) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'save' command"
            );
        }

        Path target = snapshotPath.toAbsolutePath();
        Files.createDirectories(target.getParent());

        List<SnapshotEntry> snapshots = inMemoryStorage.exportSnapshot();

        Path temp = Files.createTempFile(
                target.getParent(),
                "snapshot-",
                ".tmp"
        );

        try {
            try (OutputStream outputStream = Files.newOutputStream(temp)) {
                SnapshotWriter snapshotWriter = new SnapshotWriter(outputStream);
                snapshotWriter.write(snapshots);
            }

            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            Files.deleteIfExists(temp);
        }

        return new RespValue.SimpleString("OK");
    }
}
