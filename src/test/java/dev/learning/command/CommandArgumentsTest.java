package dev.learning.command;

import dev.learning.exception.InvalidElementsException;
import dev.learning.exception.InvalidKeyException;
import dev.learning.protocol.RespValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandArgumentsTest {
    @Test
    void shouldExtractAsciiTextWhenBulkStringIsValid() {
        RespValue argument = new RespValue.BulkString("10".getBytes(StandardCharsets.US_ASCII));

        assertEquals(Optional.of("10"), CommandArguments.extractAsciiText(argument));
    }

    @Test
    void shouldRejectNonAsciiBytesWhenExtractingAsciiText() {
        RespValue argument = new RespValue.BulkString(new byte[]{(byte) 0xff});

        assertEquals(Optional.empty(), CommandArguments.extractAsciiText(argument));
    }

    @Test
    void shouldRejectNonAsciiSimpleStringWhenExtractingAsciiText() {
        RespValue argument = new RespValue.SimpleString("\uFF11");

        assertEquals(Optional.empty(), CommandArguments.extractAsciiText(argument));
    }

    @Test
    void shouldPreserveBinaryDataWhenExtractingRedisBytes() {
        byte[] payload = {0, (byte) 0xff, '\r', '\n'};
        RespValue argument = new RespValue.BulkString(payload);

        assertArrayEquals(payload, CommandArguments.extractKey(argument).getBytes());
        assertArrayEquals(payload, CommandArguments.extractValue(argument).getBytes());
    }

    @Test
    void shouldRejectNonStringArgumentsAccordingToTheirRole() {
        RespValue argument = new RespValue.IntegerValue(42);

        assertThrows(InvalidKeyException.class, () -> CommandArguments.extractKey(argument));
        assertThrows(InvalidElementsException.class, () -> CommandArguments.extractValue(argument));
    }
}
