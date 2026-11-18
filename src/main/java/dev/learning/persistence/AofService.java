package dev.learning.persistence;

import dev.learning.persistence.aof.AofReader;
import dev.learning.persistence.aof.AofWriter;
import dev.learning.persistence.model.AofEntry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public final class AofService implements AutoCloseable {
    private static final Set<Path> OPEN_FILES = new HashSet<>();

    private final AofWriter writer;
    private final Path ownedFile;
    private volatile boolean available = true;
    private boolean closed;

    AofService(AofWriter writer) {
        this(writer, null);
    }

    private AofService(AofWriter writer, Path ownedFile) {
        this.writer = writer;
        this.ownedFile = ownedFile;
    }

    public static AofService open(Path path, Consumer<AofEntry> replay) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        FileChannel channel = reserveAndOpen(target);
        try {
            lockFile(channel, target);
            long validEnd = new AofReader().replay(channel, replay);
            if (validEnd < channel.size()) {
                channel.truncate(validEnd);
                channel.force(true);
            }
            channel.position(validEnd);
            return new AofService(new AofWriter(channel), target);
        } catch (IOException | RuntimeException | Error e) {
            try {
                channel.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            } finally {
                releaseFile(target);
            }
            throw e;
        }
    }

    private static synchronized FileChannel reserveAndOpen(Path target) throws IOException {
        // Closing a second channel can release this process's existing OS lock on POSIX.
        boolean exists = Files.exists(target);
        for (Path openFile : OPEN_FILES) {
            if (target.equals(openFile) || (exists && Files.isSameFile(target, openFile))) {
                throw new IOException("AOF file is already in use: " + target);
            }
        }

        FileChannel channel = FileChannel.open(target, CREATE, READ, WRITE);
        OPEN_FILES.add(target);
        return channel;
    }

    private static synchronized void releaseFile(Path target) {
        OPEN_FILES.remove(target);
    }

    private static void lockFile(FileChannel channel, Path target) throws IOException {
        try {
            if (channel.tryLock() == null) {
                throw new IOException("AOF file is already in use: " + target);
            }
        } catch (OverlappingFileLockException e) {
            throw new IOException("AOF file is already in use: " + target, e);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    // The dispatcher holds the global write lock through append and force.
    public void append(AofEntry entry) throws IOException {
        if (!available) {
            throw new IOException("AOF persistence is unavailable");
        }
        try {
            writer.append(entry);
        } catch (IOException e) {
            available = false;
            throw e;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        available = false;
        try {
            writer.close();
        } finally {
            releaseFile(ownedFile);
        }
    }
}
