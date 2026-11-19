package dev.learning.protocol;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes RESP2 with bounds on frame size, payload size, and array nesting.
 */
public class RespDecoder {

    private static final int MAX_READ_VALUE = 16 * 1024;
    private static final int MAX_ARRAY_ELEMENTS = 1024;
    private static final int MAX_NESTING_DEPTH = 32;
    private static final int MAX_FRAME_BYTES = 256 * 1024;

    /**
     * Reads one RESP2 value from a caller-owned stream.
     *
     * @return the decoded value, or {@code null} at EOF before the frame
     * @throws IOException if the frame is invalid, truncated, or unreadable
     */
    public RespValue readAndExecuteRespMessage(InputStream input)
            throws IOException {

        FrameInput stream = new FrameInput(input);
        int prefix = stream.read();

        if (prefix == -1) {
            return null;
        }

        RespType type = validateRespType(prefix);

        return executeType(type, stream, 0);
    }

    private RespValue executeType(
            RespType type,
            FrameInput stream,
            int depth
    ) throws IOException {

        return switch (type) {
            case SIMPLE_STRING -> executeSimpleString(stream);
            case ERROR -> executeError(stream);
            case INTEGER -> executeInteger(stream);
            case BULK_STRING -> executeBulkString(stream);
            case ARRAY -> executeArray(stream, depth);
        };
    }

    private RespValue executeSimpleString(FrameInput stream)
            throws IOException {

        byte[] value = findCRLF(stream);

        return new RespValue.SimpleString(
                decodeText(value)
        );
    }

    private RespValue executeError(FrameInput stream)
            throws IOException {

        byte[] value = findCRLF(stream);

        return new RespValue.ErrorValue(
                decodeText(value)
        );
    }

    private String decodeText(byte[] value) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 in RESP text", e);
        }
    }

    private RespValue executeInteger(FrameInput stream)
            throws IOException {

        byte[] value = findCRLF(stream);

        try {
            long number = Long.parseLong(
                    new String(value, StandardCharsets.US_ASCII)
            );

            return new RespValue.IntegerValue(number);

        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP integer", e);
        }
    }

    private RespValue executeBulkString(FrameInput stream)
            throws IOException {

        int length = findCounts(stream, MAX_READ_VALUE);

        if (length == -1) {
            return new RespValue.BulkString(null);
        }

        byte[] body = stream.readBulkPayload(length);

        if (body.length != length) {
            throw new EOFException("Incomplete Bulk String");
        }

        expectCRLF(stream);

        return new RespValue.BulkString(body);
    }

    private RespValue executeArray(FrameInput stream, int depth)
            throws IOException {

        if (depth >= MAX_NESTING_DEPTH) {
            throw new IOException("RESP array nesting too deep");
        }

        int number = findCounts(stream, MAX_ARRAY_ELEMENTS);

        if (number == -1) {
            return new RespValue.ArrayValue(null);
        }

        List<RespValue> body = new ArrayList<>(number);

        for (int i = 0; i < number; i++) {

            int prefix = stream.read();

            if (prefix == -1) {
                throw new EOFException("Incomplete RESP Array");
            }

            RespType type = validateRespType(prefix);

            body.add(executeType(type, stream, depth + 1));
        }

        return new RespValue.ArrayValue(body);
    }

    private int findCounts(FrameInput stream, int maximum)
            throws IOException {

        byte[] counts = findCRLF(stream);

        String text = new String(
                counts,
                StandardCharsets.US_ASCII
        );

        try {
            int number = Integer.parseInt(text);

            if (number < -1 || number > maximum) {
                throw new IOException("Invalid RESP length: " + number);
            }

            return number;

        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP length", e);
        }
    }

    private RespType validateRespType(int prefix) throws IOException {

        for (RespType type : RespType.values()) {
            if (type.getPrefix() == prefix) {
                return type;
            }
        }

        throw new IOException(
                "Unknown RESP prefix: " + (char) prefix
        );
    }

    private byte[] findCRLF(FrameInput stream) throws IOException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        while (true) {

            int current = stream.read();

            if (current == -1) {
                throw new EOFException("Incomplete RESP line");
            }

            if (current == '\r') {

                int next = stream.read();

                if (next == -1) {
                    throw new EOFException("Incomplete CRLF");
                }

                if (next != '\n') {
                    throw new IOException("Invalid CRLF");
                }

                return output.toByteArray();
            }

            if (current == '\n') {
                throw new IOException("Unexpected LF");
            }

            if (output.size() >= MAX_READ_VALUE) {
                throw new IOException("RESP line too long");
            }

            output.write(current);
        }
    }

    private void expectCRLF(FrameInput stream) throws IOException {
        int first = stream.read();
        if (first == -1) {
            throw new EOFException("Incomplete CRLF");
        }
        if (first != '\r') {
            throw new IOException("Expected CRLF");
        }

        int second = stream.read();
        if (second == -1) {
            throw new EOFException("Incomplete CRLF");
        }
        if (second != '\n') {
            throw new IOException("Expected CRLF");
        }
    }

    private static final class FrameInput {
        private final InputStream input;
        private int remaining = MAX_FRAME_BYTES;

        private FrameInput(InputStream input) {
            this.input = input;
        }

        private int read() throws IOException {
            requireRemaining(1);
            int value = input.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        private byte[] readBulkPayload(int length) throws IOException {
            // Reject oversized payloads before reading or allocating, including their trailer.
            requireRemaining(length + 2);
            byte[] payload = input.readNBytes(length);
            remaining -= payload.length;
            return payload;
        }

        private void requireRemaining(int bytes) throws IOException {
            if (bytes > remaining) {
                throw new IOException("RESP frame too large");
            }
        }
    }
}
