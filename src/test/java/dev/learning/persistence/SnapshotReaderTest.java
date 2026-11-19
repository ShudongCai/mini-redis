package dev.learning.persistence;

import dev.learning.persistence.model.SnapshotEntry;
import dev.learning.persistence.snapshot.SnapshotReader;
import dev.learning.storage.model.RedisBytes;
import dev.learning.storage.model.redisValue.StringValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

import static dev.learning.storage.StorageTestSupport.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotReaderTest {
    private static final String VERSION_ONE = "4d525331" + "00000001";
    private static final String ONE_ENTRY = VERSION_ONE + "00000001";
    private static final String KEY_A = "00000001" + "61";
    private static final String NO_EXPIRATION = "ffffffffffffffff";
    private static final String STRING_ENTRY = ONE_ENTRY + KEY_A + "01" + NO_EXPIRATION;

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

    @ParameterizedTest(name = "negative {0} count")
    @CsvSource({
            "entry, " + VERSION_ONE,
            "list, " + ONE_ENTRY + KEY_A + "02" + NO_EXPIRATION,
            "set, " + ONE_ENTRY + KEY_A + "03" + NO_EXPIRATION,
            "sorted set, " + ONE_ENTRY + KEY_A + "04" + NO_EXPIRATION
    })
    void shouldRejectNegativeCountWhenReadingSnapshot(String field, String prefix) {
        IOException error = assertThrows(IOException.class, () -> readHex(prefix + "ffffffff"), field);

        assertEquals("Negative element count", error.getMessage());
    }

    @ParameterizedTest(name = "oversized {0} length")
    @CsvSource({
            "key, " + ONE_ENTRY + "7fffffff",
            "string value, " + STRING_ENTRY + "01000001"
    })
    void shouldRejectOversizedLengthWhenReadingSnapshot(String field, String snapshot) {
        IOException error = assertThrows(IOException.class, () -> readHex(snapshot), field);

        assertEquals("Invalid RedisBytes length", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3ff0000000000000", "4000000000000000"})
    void shouldRejectDuplicateMemberWhenReadingSortedSet(String secondScore) {
        String member = "00000001" + "78";
        String snapshot = ONE_ENTRY + KEY_A + "04" + NO_EXPIRATION + "00000002"
                + member + "3ff0000000000000"
                + member + secondScore;

        IOException error = assertThrows(IOException.class, () -> readHex(snapshot));

        assertEquals("Duplicate sorted set member", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {VERSION_ONE + "00000000", STRING_ENTRY + "00000000"})
    void shouldRejectTrailingBytesWhenReadingSnapshot(String snapshot) {
        IOException error = assertThrows(IOException.class, () -> readHex(snapshot + "00"));

        assertEquals("Unexpected trailing data", error.getMessage());
    }

    @ParameterizedTest(name = "truncated {0}")
    @CsvSource({
            "header count, " + VERSION_ONE,
            "key, " + ONE_ENTRY + "00000002" + "61",
            "string value, " + STRING_ENTRY + "00000002" + "42"
    })
    void shouldRejectTruncatedDataWhenReadingSnapshot(String field, String snapshot) {
        assertThrows(EOFException.class, () -> readHex(snapshot), field);
    }

    private static List<SnapshotEntry> readHex(String hex) throws IOException {
        byte[] snapshot = HexFormat.of().parseHex(hex);
        return new SnapshotReader(new ByteArrayInputStream(snapshot)).read();
    }
}
