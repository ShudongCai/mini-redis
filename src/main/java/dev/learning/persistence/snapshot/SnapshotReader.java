package dev.learning.persistence.snapshot;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.SortedSetEntry;
import dev.learning.storage.model.redisValue.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SnapshotReader {
    private static final int MAX_BINARY_LENGTH = 16 * 1024 * 1024;

    private final DataInputStream dataInputStream;

    public SnapshotReader(InputStream inputStream) {
        this.dataInputStream = new DataInputStream(inputStream);
    }

    public List<SnapshotEntry> read() throws IOException {
        List<SnapshotEntry> snapshotData = new ArrayList<>();
        int entryCounts = readHeader();
        readEntries(entryCounts, snapshotData);
        if (dataInputStream.read() != -1) {
            throw new IOException("Unexpected trailing data");
        }
        return snapshotData;
    }

    private void readEntries(int entryCounts, List<SnapshotEntry> snapshotData) throws IOException {
        if (entryCounts == 0) {
            return;
        }

        for (int i = 0; i < entryCounts; ++i) {
            RedisBytes keyBytes = readRedisBytes();
            byte valueType = dataInputStream.readByte();
            long timestamp = dataInputStream.readLong();
            Long expireAt = timestamp == -1L ? null : timestamp;

            RedisValue redisValue = switch (valueType) {
                case SnapshotFormat.TYPE_STRING -> readTypeString();
                case SnapshotFormat.TYPE_LIST -> readTypeList();
                case SnapshotFormat.TYPE_SET -> readTypeSet();
                case SnapshotFormat.TYPE_SORTED_SET -> readTypeSortedSet();
                default -> throw new IOException(
                        "Unknown snapshot value type: " + valueType
                );
            };

            snapshotData.add(new SnapshotEntry(
                    keyBytes,
                    redisValue,
                    expireAt
            ));
        }
    }

    private SortedSetValue readTypeSortedSet() throws IOException {
        Map<RedisBytes, Double> map = new HashMap<>();
        TreeSet<SortedSetEntry> set = new TreeSet<>(
                (a, b) -> {
                    int compareResult = Double.compare(a.score(), b.score());
                    if (compareResult == 0) {
                        return Arrays.compareUnsigned(a.value().getBytes(), b.value().getBytes());
                    }
                    return compareResult;
                }
        );
        int readCounts = readCount();

        for (int i = 0; i < readCounts; ++i) {
            RedisBytes redisBytes = readRedisBytes();
            Double score = dataInputStream.readDouble();
            if (map.putIfAbsent(redisBytes, score) != null) {
                throw new IOException("Duplicate sorted set member");
            }
            set.add(new SortedSetEntry(redisBytes, score));
        }

        return new SortedSetValue(map, set);
    }

    private SetValue readTypeSet() throws IOException {
        Set<RedisBytes> setValue = new HashSet<>();
        int readCounts = readCount();

        for (int i = 0; i < readCounts; ++i) {
            setValue.add(readRedisBytes());
        }
        return new SetValue(setValue);
    }

    private ListValue readTypeList() throws IOException {
        Deque<RedisBytes> listValue = new ArrayDeque<>();
        int readCounts = readCount();

        for (int i = 0; i < readCounts; ++i) {
            listValue.offer(readRedisBytes());
        }
        return new ListValue(listValue);
    }

    private StringValue readTypeString() throws IOException {
        return new StringValue(readRedisBytes());
    }

    private int readHeader() throws IOException {
        byte[] magic = dataInputStream.readNBytes(4);
        if (Arrays.compare(magic, SnapshotFormat.MAGIC) != 0) {
            throw new IOException("Invalid snapshot magic");
        }

        int version = dataInputStream.readInt();
        if (version != SnapshotFormat.VERSION) {
            throw new IOException("Unsupported snapshot version");
        }

        return readCount();
    }

    private int readCount() throws IOException {
        int count = dataInputStream.readInt();
        if (count < 0) {
            throw new IOException("Negative element count");
        }
        return count;
    }

    private RedisBytes readRedisBytes() throws IOException {
        int length = dataInputStream.readInt();

        if (length < 0 || length > MAX_BINARY_LENGTH) {
            throw new IOException("Invalid RedisBytes length");
        }

        byte[] bytes = new byte[length];
        dataInputStream.readFully(bytes);

        return new RedisBytes(bytes);
    }
}
