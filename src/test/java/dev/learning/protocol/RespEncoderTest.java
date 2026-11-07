package dev.learning.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RespEncoderTest {
    private final RespEncoder encoder = new RespEncoder();

    @Test
    void shouldEncodeSimpleString() throws IOException {
        assertEncoded("+OK\r\n", new RespValue.SimpleString("OK"));
    }

    @Test
    void shouldEncodeErrorResponse() throws IOException {
        assertEncoded("-ERR unknown command\r\n", new RespValue.ErrorValue("ERR unknown command"));
    }

    @Test
    void shouldEncodeInteger() throws IOException {
        assertEncoded(":42\r\n", new RespValue.IntegerValue(42));
        assertEncoded(":0\r\n", new RespValue.IntegerValue(0));
        assertEncoded(":-9223372036854775808\r\n", new RespValue.IntegerValue(Long.MIN_VALUE));
        assertEncoded(":9223372036854775807\r\n", new RespValue.IntegerValue(Long.MAX_VALUE));
    }

    @Test
    void shouldEncodeBulkString() throws IOException {
        byte[] payload = "héllo".getBytes(StandardCharsets.UTF_8);

        assertEncoded("$6\r\nhéllo\r\n", new RespValue.BulkString(payload));
    }

    @Test
    void shouldEncodeNullBulkString() throws IOException {
        assertEncoded("$-1\r\n", new RespValue.BulkString(null));
    }

    @Test
    void shouldEncodeEmptyBulkString() throws IOException {
        assertEncoded("$0\r\n\r\n", new RespValue.BulkString(new byte[0]));
    }

    @Test
    void shouldEncodeArrayWithMultipleTypes() throws IOException {
        RespValue.ArrayValue value = new RespValue.ArrayValue(List.of(
                new RespValue.SimpleString("OK"),
                new RespValue.ErrorValue("ERR unknown command"),
                new RespValue.IntegerValue(42),
                new RespValue.BulkString("hello".getBytes(StandardCharsets.UTF_8))
        ));

        assertEncoded("*4\r\n+OK\r\n-ERR unknown command\r\n:42\r\n$5\r\nhello\r\n", value);
    }

    @Test
    void shouldEncodeNestedArray() throws IOException {
        RespValue.ArrayValue nested = new RespValue.ArrayValue(List.of(
                new RespValue.SimpleString("OK"),
                new RespValue.BulkString("hello".getBytes(StandardCharsets.UTF_8))
        ));
        RespValue.ArrayValue value = new RespValue.ArrayValue(List.of(
                new RespValue.IntegerValue(42),
                nested
        ));

        assertEncoded("*2\r\n:42\r\n*2\r\n+OK\r\n$5\r\nhello\r\n", value);
    }

    @Test
    void shouldEncodeNullArray() throws IOException {
        assertEncoded("*-1\r\n", new RespValue.ArrayValue(null));
    }

    @Test
    void shouldEncodeEmptyArray() throws IOException {
        assertEncoded("*0\r\n", new RespValue.ArrayValue(List.of()));
    }

    @Test
    void shouldPreserveBinaryDataWhenEncodingBulkString() throws IOException {
        byte[] payload = {0, (byte) 0x80, (byte) 0xFF, '\r', '\n'};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        encoder.encode(new RespValue.BulkString(payload), output);

        byte[] expected = {'$', '5', '\r', '\n', 0, (byte) 0x80, (byte) 0xFF, '\r', '\n', '\r', '\n'};
        assertArrayEquals(expected, output.toByteArray());
    }

    @Test
    void shouldRejectSimpleStringContainingCRLF() {
        RespValue.SimpleString value = new RespValue.SimpleString("OK\r\n+injected");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(IllegalArgumentException.class, () -> encoder.encode(value, output));
        assertEquals(0, output.size());
    }

    @Test
    void shouldRejectErrorResponseContainingCRLF() {
        RespValue.ErrorValue value = new RespValue.ErrorValue("ERR invalid input\r\n+injected");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(IllegalArgumentException.class, () -> encoder.encode(value, output));
        assertEquals(0, output.size());
    }

    private void assertEncoded(String expected, RespValue value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        encoder.encode(value, output);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), output.toByteArray());
    }
}
