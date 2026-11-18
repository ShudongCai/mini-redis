package dev.learning.persistence.aof;

import dev.learning.persistence.model.AofEntry;
import dev.learning.persistence.model.SnapshotEntry;
import dev.learning.persistence.snapshot.SnapshotReader;
import dev.learning.storage.model.RedisBytes;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.CRC32;

public final class AofReader {
    // Return the end of the last complete record so startup can remove an interrupted tail.
    public long replay(FileChannel channel, Consumer<AofEntry> apply) throws IOException {
        channel.position(0);
        long size = channel.size();
        long validEnd = 0;

        while (size - validEnd >= AofFormat.HEADER_BYTES) {
            ByteBuffer header = readFully(channel, AofFormat.HEADER_BYTES);
            int version = header.getInt();
            int length = header.getInt();
            int invertedLength = header.getInt();
            if (version != AofFormat.MAGIC_VERSION) {
                throw new IOException("Invalid AOF magic or version at offset " + validEnd);
            }
            if (length < 1 || length > AofFormat.MAX_RECORD_BYTES || invertedLength != ~length) {
                throw new IOException("Invalid AOF record length at offset " + validEnd);
            }

            if (size - channel.position() < (long) length + Integer.BYTES) {
                return validEnd;
            }
            byte[] payload = readFully(channel, length).array();
            int recordedChecksum = readFully(channel, Integer.BYTES).getInt();
            CRC32 checksum = new CRC32();
            checksum.update(payload);
            if (recordedChecksum != (int) checksum.getValue()) {
                throw new IOException("Invalid AOF checksum at offset " + validEnd);
            }

            apply.accept(decode(payload));
            validEnd = channel.position();
        }

        return validEnd;
    }

    private static AofEntry decode(byte[] payload) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(payload);
        return switch (input.read()) {
            case AofFormat.REPLACE -> {
                List<SnapshotEntry> entries = new SnapshotReader(input).read();
                if (entries.size() != 1) {
                    throw new IOException("AOF replacement must contain exactly one snapshot entry");
                }
                yield new AofEntry.Replace(entries.getFirst());
            }
            case AofFormat.DELETE -> new AofEntry.Delete(new RedisBytes(input.readAllBytes()));
            default -> throw new IOException("Invalid AOF record type");
        };
    }

    private static ByteBuffer readFully(FileChannel channel, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("AOF file changed during recovery");
            }
        }
        return buffer.flip();
    }
}
