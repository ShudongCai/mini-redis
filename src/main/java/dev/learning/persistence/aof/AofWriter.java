package dev.learning.persistence.aof;

import dev.learning.persistence.model.AofEntry;
import dev.learning.persistence.snapshot.SnapshotWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Writes checksummed AOF records and owns the underlying file channel.
 */
public final class AofWriter implements AutoCloseable {
    private final FileChannel channel;

    public AofWriter(FileChannel channel) {
        this.channel = channel;
    }

    /**
     * Writes one record at the channel's current position and forces it to disk before returning.
     */
    public void append(AofEntry entry) throws IOException {
        Payload payload = new Payload();
        switch (entry) {
            case AofEntry.Replace replace -> {
                payload.write(AofFormat.REPLACE);
                new SnapshotWriter(payload).write(List.of(replace.entry()));
            }
            case AofEntry.Delete delete -> {
                payload.write(AofFormat.DELETE);
                payload.write(delete.key().getBytes());
            }
        }

        byte[] bytes = payload.buffer.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(bytes);
        ByteBuffer header = ByteBuffer.allocate(AofFormat.HEADER_BYTES);
        header.putInt(AofFormat.MAGIC_VERSION).putInt(bytes.length).putInt(~bytes.length).flip();
        ByteBuffer trailer = ByteBuffer.allocate(Integer.BYTES);
        trailer.putInt((int) checksum.getValue()).flip();

        writeFully(header);
        writeFully(ByteBuffer.wrap(bytes));
        writeFully(trailer);
        channel.force(true);
    }

    private void writeFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static final class Payload extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void write(int value) throws IOException {
            checkLength(1);
            buffer.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            checkLength(length);
            buffer.write(bytes, offset, length);
        }

        private void checkLength(int length) throws IOException {
            if (length > AofFormat.MAX_RECORD_BYTES - buffer.size()) {
                throw new IOException("AOF record exceeds the size limit");
            }
        }
    }
}
