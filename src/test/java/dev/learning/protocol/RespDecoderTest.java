package dev.learning.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class RespDecoderTest {
    @Test
    void shouldDecodeSimpleString() throws IOException {
        //Arrange
        String inputString = "+OK\r\n";
        ByteArrayInputStream stream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(stream);

        //Assert
        RespValue.SimpleString value =
                assertInstanceOf(RespValue.SimpleString.class, result);
        assertEquals("OK", value.value());
    }

    @Test
    void shouldDecodeErrorResponse() throws IOException {
        //Arrange
        String inputString = "-Err Invalid Variable\r\n";
        ByteArrayInputStream stream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(stream);

        //Assert
        RespValue.ErrorValue value = assertInstanceOf(
                RespValue.ErrorValue.class,
                result
        );
        assertEquals("Err Invalid Variable", value.message());
    }

    @Test
    void shouldDecodeInteger() throws IOException {
        //Arrange
        String inputString = ":17925\r\n";
        ByteArrayInputStream stream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(stream);

        //Assert
        RespValue.IntegerValue value = assertInstanceOf(
                RespValue.IntegerValue.class,
                result
        );
        assertEquals(17925L, value.value());
    }

    @Test
    void shouldDecodeBulkString() throws IOException {
        //Arrange
        String inputString = "$5\r\nhello\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.BulkString value = assertInstanceOf(
                RespValue.BulkString.class,
                result
        );
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                value.value()
        );
    }

    @Test
    void shouldDecodeArrayWithMultipleTypes() throws IOException {
        //Arrange
        String inputString = "*3\r\n$5\r\nWSACJ\r\n:17762\r\n+HELLO\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                inputString.getBytes(StandardCharsets.UTF_8)
        );
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.ArrayValue value = assertInstanceOf(
                RespValue.ArrayValue.class,
                result
        );
        assertEquals(3, value.elements().size());

        RespValue.BulkString value1 = assertInstanceOf(
                RespValue.BulkString.class,
                value.elements().getFirst()
        );
        assertArrayEquals("WSACJ".getBytes(StandardCharsets.UTF_8), value1.value());

        RespValue.IntegerValue value2 = assertInstanceOf(
                RespValue.IntegerValue.class,
                value.elements().get(1)
        );
        assertEquals(17762L, value2.value());

        RespValue.SimpleString value3 = assertInstanceOf(
                RespValue.SimpleString.class,
                value.elements().getLast()
        );
        assertEquals("HELLO", value3.value());
    }

    @Test
    void shouldDecodeNullBulkString() throws IOException {
        //Arrange
        String inputString = "$-1\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.BulkString value = assertInstanceOf(
                RespValue.BulkString.class,
                result
        );
        assertNull(value.value());
    }

    @Test
    void shouldDecodeEmptyBulkString() throws IOException {
        //Arrange
        String inputString = "$0\r\n\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.BulkString value = assertInstanceOf(
                RespValue.BulkString.class,
                result
        );
        assertArrayEquals(new byte[0], value.value());
    }

    @Test
    void shouldDecodeNullArray() throws IOException {
        //Arrange
        String inputString = "*-1\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.ArrayValue value = assertInstanceOf(
                RespValue.ArrayValue.class,
                result
        );
        assertNull(value.elements());
    }

    @Test
    void shouldDecodeEmptyArray() throws IOException {
        //Arrange
        String inputString = "*0\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.ArrayValue value = assertInstanceOf(
                RespValue.ArrayValue.class,
                result
        );
        assertTrue(value.elements().isEmpty());
    }

    @Test
    void shouldPreserveCRLFInsideBulkString() throws IOException {
        //Arrange
        String inputString = "$12\r\nHello\r\nWorld\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.BulkString value = assertInstanceOf(
                RespValue.BulkString.class,
                result
        );
        assertArrayEquals(
                "Hello\r\nWorld".getBytes(StandardCharsets.UTF_8),
                value.value()
        );
    }

    @Test
    void shouldDecodeConsecutiveMessages() throws IOException {
        //Arrange
        String firstMessage = "$5\r\nhello\r\n";
        String secondMessage = ":42\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                (firstMessage + secondMessage).getBytes(StandardCharsets.UTF_8)
        );
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue resultFirst = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.BulkString valueFirst = assertInstanceOf(
                RespValue.BulkString.class,
                resultFirst
        );
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), valueFirst.value());
        assertEquals(secondMessage.getBytes(StandardCharsets.UTF_8).length,
                inputStream.available());

        //Act
        RespValue resultSecond = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.IntegerValue valueSecond = assertInstanceOf(
                RespValue.IntegerValue.class,
                resultSecond
        );
        assertEquals(42L, valueSecond.value());
        assertEquals(0, inputStream.available());
        assertNull(decoder.readAndExecuteRespMessage(inputStream));
    }

    @Test
    void shouldThrowWhenBulkStringIsIncomplete() {
        //Arrange
        String inputString = "$5\r\nhel";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        EOFException exception = assertThrows(
                EOFException.class,
                () -> decoder.readAndExecuteRespMessage(inputStream)
        );

        //Assert
        assertEquals("Incomplete Bulk String", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidRespPrefix() {
        //Arrange
        String inputString = "?OK\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        IOException exception = assertThrows(
                IOException.class,
                () -> decoder.readAndExecuteRespMessage(inputStream)
        );

        //Assert
        assertEquals("Unknown RESP prefix: ?", exception.getMessage());
    }

    @Test
    void shouldDecodeNestedArray() throws IOException {
        //Arrange
        String inputString = "*2\r\n:42\r\n*2\r\n+OK\r\n$5\r\nhello\r\n";
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        RespDecoder decoder = new RespDecoder();

        //Act
        RespValue result = decoder.readAndExecuteRespMessage(inputStream);

        //Assert
        RespValue.ArrayValue value = assertInstanceOf(
                RespValue.ArrayValue.class,
                result
        );
        assertEquals(2, value.elements().size());
        RespValue.IntegerValue integer = assertInstanceOf(
                RespValue.IntegerValue.class,
                value.elements().getFirst()
        );
        assertEquals(42L, integer.value());

        RespValue.ArrayValue nestedArray = assertInstanceOf(
                RespValue.ArrayValue.class,
                value.elements().getLast()
        );
        assertEquals(2, nestedArray.elements().size());
        RespValue.SimpleString simpleString = assertInstanceOf(
                RespValue.SimpleString.class,
                nestedArray.elements().getFirst()
        );
        assertEquals("OK", simpleString.value());
        RespValue.BulkString bulkString = assertInstanceOf(
                RespValue.BulkString.class,
                nestedArray.elements().getLast()
        );
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bulkString.value());
        assertNull(decoder.readAndExecuteRespMessage(inputStream));
    }

    @Test
    void shouldDecodeBulkStringWhenInputIsFragmented() throws IOException {
        //Arrange
        String inputString = "$5\r\nhello\r\n";

        for (int fragmentSize = 1; fragmentSize <= 3; fragmentSize++) {
            InputStream inputStream = new FragmentedInputStream(inputString, fragmentSize);
            RespDecoder decoder = new RespDecoder();

            //Act
            RespValue result = decoder.readAndExecuteRespMessage(inputStream);

            //Assert
            RespValue.BulkString value = assertInstanceOf(
                    RespValue.BulkString.class,
                    result
            );
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), value.value());
            assertNull(decoder.readAndExecuteRespMessage(inputStream));
        }
    }

    @Test
    void shouldDecodeArrayWhenInputIsFragmented() throws IOException {
        //Arrange
        String inputString = "*3\r\n:42\r\n+OK\r\n$5\r\nhello\r\n";

        for (int fragmentSize = 1; fragmentSize <= 3; fragmentSize++) {
            InputStream inputStream = new FragmentedInputStream(inputString, fragmentSize);
            RespDecoder decoder = new RespDecoder();

            //Act
            RespValue result = decoder.readAndExecuteRespMessage(inputStream);

            //Assert
            RespValue.ArrayValue value = assertInstanceOf(
                    RespValue.ArrayValue.class,
                    result
            );
            assertEquals(3, value.elements().size());
            RespValue.IntegerValue integer = assertInstanceOf(
                    RespValue.IntegerValue.class,
                    value.elements().getFirst()
            );
            assertEquals(42L, integer.value());
            RespValue.SimpleString simpleString = assertInstanceOf(
                    RespValue.SimpleString.class,
                    value.elements().get(1)
            );
            assertEquals("OK", simpleString.value());
            RespValue.BulkString bulkString = assertInstanceOf(
                    RespValue.BulkString.class,
                    value.elements().getLast()
            );
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bulkString.value());
            assertNull(decoder.readAndExecuteRespMessage(inputStream));
        }
    }

    private static class FragmentedInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int fragmentSize;

        private FragmentedInputStream(String input, int fragmentSize) {
            this.delegate = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
            this.fragmentSize = fragmentSize;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            // Limit each bulk read; inherited readNBytes must assemble the fragments.
            return delegate.read(buffer, offset, Math.min(length, fragmentSize));
        }
    }
}
