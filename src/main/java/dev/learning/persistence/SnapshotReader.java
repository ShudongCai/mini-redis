package dev.learning.persistence;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.SortedSetEntry;
import dev.learning.storage.model.redisValue.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SnapshotReader {
    private final DataInputStream dataInputStream;

    public SnapshotReader(InputStream inputStream) {
        this.dataInputStream = new DataInputStream(inputStream);
    }

    public List<SnapshotEntry> read() throws IOException {
        List<SnapshotEntry> snapshotData = new ArrayList<>();
        int entryCounts = readHeader();
        readEntries(entryCounts, snapshotData);
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

            RedisValue redisValue = null;

            switch (valueType) {
                case SnapshotFormat.TYPE_STRING -> redisValue = readTypeString();
                case SnapshotFormat.TYPE_LIST -> redisValue = readTypeList();
                case SnapshotFormat.TYPE_SET -> redisValue = readTypeSet();
                case SnapshotFormat.TYPE_SORTED_SET -> redisValue = readTypeSortedSet();
            }

            if (redisValue != null) {
                SnapshotEntry newEntry = new SnapshotEntry(
                        keyBytes,
                        redisValue,
                        expireAt
                );
                snapshotData.add(newEntry);
            } else {
                throw new IllegalArgumentException();
            }
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
        int readCounts = dataInputStream.readInt();

        for (int i = 0; i < readCounts; ++i) {
            RedisBytes redisBytes = readRedisBytes();
            Double score = dataInputStream.readDouble();
            map.put(redisBytes, score);
            set.add(new SortedSetEntry(redisBytes, score));
        }

        return new SortedSetValue(map, set);
    }

    private SetValue readTypeSet() throws IOException {
        Set<RedisBytes> setValue = new HashSet<>();
        int readCounts = dataInputStream.readInt();

        for (int i = 0; i < readCounts; ++i) {
            setValue.add(readRedisBytes());
        }
        return new SetValue(setValue);
    }

    private ListValue readTypeList() throws IOException {
        Deque<RedisBytes> listValue = new ArrayDeque<>();
        int readCounts = dataInputStream.readInt();

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

        return dataInputStream.readInt();
    }

    private RedisBytes readRedisBytes() throws IOException {
        int length = dataInputStream.readInt();

        if (length < 0) {
            throw new IOException("Invalid RedisBytes length");
        }

        byte[] bytes = new byte[length];
        dataInputStream.readFully(bytes);

        return new RedisBytes(bytes);
    }
}
