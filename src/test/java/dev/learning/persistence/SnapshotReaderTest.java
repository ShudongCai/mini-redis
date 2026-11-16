package dev.learning.persistence;

import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.StringValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static List<SnapshotEntry> readHex(String hex) throws IOException {
        byte[] snapshot = HexFormat.of().parseHex(hex);
        return new SnapshotReader(new ByteArrayInputStream(snapshot)).read();
    }
}
