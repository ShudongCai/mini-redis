package dev.learning.persistence.snapshot;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.ListValue;
import dev.learning.storage.model.redisValue.SetValue;
import dev.learning.storage.model.redisValue.SortedSetValue;
import dev.learning.storage.model.redisValue.StringValue;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SnapshotWriter {
    private final DataOutputStream writer;

    public SnapshotWriter(OutputStream stream) {
        this.writer = new DataOutputStream(stream);
    }

    public void write(List<SnapshotEntry> entries) throws IOException {
        writeHeader(entries.size());
        for (SnapshotEntry entry : entries) {
            writeBody(entry);
        }
        writer.flush();
    }


    private void writeHeader(int entryCount) throws IOException {
        writer.write(SnapshotFormat.MAGIC);
        writer.writeInt(SnapshotFormat.VERSION);
        writer.writeInt(entryCount);
    }

    private void writeBody(SnapshotEntry entry) throws IOException {
        byte valueType = SnapshotFormat.getValueType(entry.value());
        long expireAt = entry.expireAt() == null ? -1 : entry.expireAt();
        byte[] keyBytes = entry.key().getBytes();
        //Key length
        writer.writeInt(keyBytes.length);
        //Key
        writer.write(keyBytes);
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
            writeRedisBytes(value);
        }
    }

    private void writeList(SnapshotEntry entry) throws IOException {
        if (entry.value() instanceof ListValue(Deque<RedisBytes> values)) {
            writer.writeInt(values.size());

            for (RedisBytes value : values) {
                writeRedisBytes(value);
            }
        }
    }

    private void writeSortedSet(SnapshotEntry entry) throws IOException {
        if (entry.value() instanceof SortedSetValue setValues) {
            Map<RedisBytes, Double> values = setValues.score();
            writer.writeInt(values.size());

            for (Map.Entry<RedisBytes, Double> map : values.entrySet()) {
                RedisBytes value = map.getKey();
                double score = map.getValue();

                writeRedisBytes(value);
                writer.writeDouble(score);
            }
        }
    }

    private void writeSet(SnapshotEntry entry) throws IOException {
        if (entry.value() instanceof SetValue(Set<RedisBytes> values)) {
            writer.writeInt(values.size());

            for (RedisBytes value : values) {
                writeRedisBytes(value);
            }
        }
    }

    private void writeRedisBytes(RedisBytes value) throws IOException {
        byte[] bytes = value.getBytes();
        writer.writeInt(bytes.length);
        writer.write(bytes);
    }
}
