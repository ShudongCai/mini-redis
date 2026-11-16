package dev.learning.persistence;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.SortedSetEntry;
import dev.learning.storage.model.redisValue.ListValue;
import dev.learning.storage.model.redisValue.SetValue;
import dev.learning.storage.model.redisValue.SortedSetValue;
import dev.learning.storage.model.redisValue.StringValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotReaderTest {
    @Test
    void shouldReadEmptySnapshot() throws IOException {
        assertEquals(List.of(), readHex("4d525331" + "00000001" + "00000000"));
    }

    @Test
    void shouldReadPersistentEmptyStringWithBinaryKeyFromVersionOneSnapshot() throws IOException {
        String snapshot = "4d525331" + "00000001" + "00000001"
                + "00000002" + "00ff" + "01" + "ffffffffffffffff" + "00000000";
        SnapshotEntry expected = new SnapshotEntry(
                new RedisBytes(new byte[]{0, (byte) 0xff}), new StringValue(bytes("")), null);

        assertEquals(List.of(expected), readHex(snapshot));
    }

    @Test
    void shouldRejectInvalidSnapshotMagic() {
        IOException error = assertThrows(IOException.class,
                () -> readHex("00000000" + "00000001" + "00000000"));

        assertEquals("Invalid snapshot magic", error.getMessage());
    }

    @Test
    void shouldRejectUnsupportedSnapshotVersion() {
        IOException error = assertThrows(IOException.class,
                () -> readHex("4d525331" + "00000002" + "00000000"));

        assertEquals("Unsupported snapshot version", error.getMessage());
    }

    @Test
    void shouldRejectTruncatedStringPayload() {
        String snapshot = "4d525331" + "00000001" + "00000001"
                + "00000001" + "61" + "01" + "ffffffffffffffff"
                + "00000002" + "42";

        assertThrows(EOFException.class, () -> readHex(snapshot));
    }

    @Test
    void shouldRestoreAllValueTypesAndExpirationsAfterRoundTrip() throws IOException {
        RedisBytes binary = new RedisBytes(new byte[]{0, (byte) 0xfe, '\r', '\n'});
        List<RedisBytes> listItems = List.of(bytes("second"), binary, bytes("second"));
        RedisBytes highByte = new RedisBytes(new byte[]{(byte) 0xff});
        RedisBytes lowByte = bytes("a");
        RedisBytes lowestScore = bytes("negative");
        Map<RedisBytes, Double> scores = new LinkedHashMap<>();
        scores.put(highByte, 2.5);
        scores.put(lowByte, 2.5);
        scores.put(lowestScore, -1.25);
        List<SortedSetEntry> expectedOrder = List.of(new SortedSetEntry(lowestScore, -1.25),
                new SortedSetEntry(lowByte, 2.5), new SortedSetEntry(highByte, 2.5));

        TreeSet<SortedSetEntry> sortedEntries = new TreeSet<>(
                Comparator.comparingDouble(SortedSetEntry::score)
                        .thenComparing(entry -> entry.value().getBytes(), Arrays::compareUnsigned));
        sortedEntries.addAll(expectedOrder);
        List<SnapshotEntry> original = List.of(
                new SnapshotEntry(binary, new StringValue(binary), 1_700_000_000_123L),
                new SnapshotEntry(bytes("list"), new ListValue(new ArrayDeque<>(listItems)), null),
                new SnapshotEntry(bytes("set"), new SetValue(Set.of(binary, bytes(""))), null),
                new SnapshotEntry(bytes("sorted"), new SortedSetValue(scores, sortedEntries), 1_700_000_000_456L));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new SnapshotWriter(output).write(original);
        List<SnapshotEntry> restored = new SnapshotReader(new ByteArrayInputStream(output.toByteArray())).read();

        assertEquals(original.stream().map(SnapshotEntry::key).toList(),
                restored.stream().map(SnapshotEntry::key).toList());
        assertEquals(original.stream().map(SnapshotEntry::expireAt).toList(),
                restored.stream().map(SnapshotEntry::expireAt).toList());
        assertEquals(original.get(0).value(), restored.get(0).value());
        ListValue list = assertInstanceOf(ListValue.class, restored.get(1).value());
        assertEquals(listItems, List.copyOf(list.values()));
        assertEquals(original.get(2).value(), restored.get(2).value());
        SortedSetValue sortedSet = assertInstanceOf(SortedSetValue.class, restored.get(3).value());
        assertEquals(scores, sortedSet.score());
        assertEquals(expectedOrder, List.copyOf(sortedSet.sortedEntry()));
    }

    private static List<SnapshotEntry> readHex(String hex) throws IOException {
        byte[] snapshot = HexFormat.of().parseHex(hex);
        return new SnapshotReader(new ByteArrayInputStream(snapshot)).read();
    }
}
