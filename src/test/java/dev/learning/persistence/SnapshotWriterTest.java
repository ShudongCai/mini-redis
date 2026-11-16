package dev.learning.persistence;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.ListValue;
import dev.learning.storage.model.redisValue.StringValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;

import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SnapshotWriterTest {
    @Test
    void shouldWriteVersionOneHeaderForEmptySnapshot() {
        assertSnapshotBytes("4d525331" + "00000001" + "00000000", List.of());
    }

    @Test
    void shouldWriteBinaryStringEntryAndExpirationUsingVersionOneLayout() {
        RedisBytes key = new RedisBytes(new byte[]{0, (byte) 0xff});
        RedisBytes value = new RedisBytes(new byte[]{'\r', '\n', (byte) 0xfe});
        SnapshotEntry entry = new SnapshotEntry(key, new StringValue(value), 1_234L);

        String expected = "4d525331" + "00000001" + "00000001"
                + "00000002" + "00ff" + "01" + "00000000000004d2"
                + "00000003" + "0d0afe";
        assertSnapshotBytes(expected, List.of(entry));
    }

    @Test
    void shouldWritePersistentListElementsInOrder() {
        ListValue value = new ListValue(new ArrayDeque<>(List.of(bytes("B"), bytes("A"), bytes("B"))));
        SnapshotEntry entry = new SnapshotEntry(bytes("q"), value, null);

        String expected = "4d525331" + "00000001" + "00000001"
                + "00000001" + "71" + "02" + "ffffffffffffffff"
                + "00000003" + "00000001" + "42" + "00000001" + "41" + "00000001" + "42";
        assertSnapshotBytes(expected, List.of(entry));
    }

    private static void assertSnapshotBytes(String expectedHex, List<SnapshotEntry> entries) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new SnapshotWriter(output).write(entries);

        assertArrayEquals(HexFormat.of().parseHex(expectedHex), output.toByteArray());
    }
}
