package dev.learning.persistence;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.StringValue;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class SnapshotWriter {
    public static final byte[] MAGIC = {'M', 'R', 'S', '1'};
    public static final int VERSION = 1;

    private final DataOutputStream writer;

    public SnapshotWriter(OutputStream stream) {
        this.writer = new DataOutputStream(stream);
    }

    public void write(List<SnapshotEntry> entries) {
        try {
            writeHeader(entries.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeHeader(int entryCount) throws IOException {
        writer.write(MAGIC);
        writer.writeInt(VERSION);
        writer.writeInt(entryCount);
    }

    private void writeBody(SnapshotEntry entry) throws IOException {
        byte valueType = SnapshotFormat.getValueType(entry.value());
        long expireAt = entry.expireAt() == null ? -1 : entry.expireAt();
        //Key length
        writer.writeInt(entry.key().getBytes().length);
        //Key
        writer.write(entry.key().getBytes());
        //value type
        writer.write(valueType);
        //expire time
        writer.writeLong(expireAt);
        //value data
        switch (valueType) {
            case 1 -> writeString(entry);
            case 2 -> writeList(entry);
            case 3 -> writeSet(entry);
            case 4 -> writeSortedSet(entry);
        }
    }

    private void writeString(SnapshotEntry entry) throws IOException {
        if (entry.value() instanceof StringValue(RedisBytes value)) {
            writer.writeInt(value.getBytes().length);
            writer.write(value.getBytes());
        }
    }

    private void writeSortedSet(SnapshotEntry entry) {

    }

    private void writeSet(SnapshotEntry entry) {

    }

    private void writeList(SnapshotEntry entry) {

    }
}
