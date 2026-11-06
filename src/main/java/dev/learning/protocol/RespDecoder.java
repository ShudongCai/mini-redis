package dev.learning.protocol;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RespDecoder {

    private static final int MAX_READ_VALUE = 16 * 1024;
    private static final int MAX_ARRAY_ELEMENTS = 1024;
    private static final int MAX_NESTING_DEPTH = 32;

    public RespValue readAndExecuteRespMessage(InputStream stream)
            throws IOException {

        int prefix = stream.read();

        if (prefix == -1) {
            return null;
        }

        RespType type = validateRespType(prefix);

        return executeType(type, stream, 0);
    }

    private RespValue executeType(
            RespType type,
            InputStream stream,
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

    private RespValue executeSimpleString(InputStream stream)
            throws IOException {

        byte[] value = findCRLF(stream);

        return new RespValue.SimpleString(
                new String(value, StandardCharsets.UTF_8)
        );
    }

    private RespValue executeError(InputStream stream)
            throws IOException {

        byte[] value = findCRLF(stream);

        return new RespValue.ErrorValue(
                new String(value, StandardCharsets.UTF_8)
        );
    }

    private RespValue executeInteger(InputStream stream)
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

    private RespValue executeBulkString(InputStream stream)
            throws IOException {

        int length = findCounts(stream, MAX_READ_VALUE);

        if (length == -1) {
            return new RespValue.BulkString(null);
        }

        byte[] body = stream.readNBytes(length);

        if (body.length != length) {
            throw new EOFException("Incomplete Bulk String");
        }

        expectCRLF(stream);

        return new RespValue.BulkString(body);
    }

    private RespValue executeArray(InputStream stream, int depth)
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

    private int findCounts(InputStream stream, int maximum)
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

    private byte[] findCRLF(InputStream stream) throws IOException {

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

    private void expectCRLF(InputStream stream) throws IOException {

        int first = stream.read();
        int second = stream.read();

        if (first == -1 || second == -1) {
            throw new EOFException("Incomplete CRLF");
        }

        if (first != '\r' || second != '\n') {
            throw new IOException("Expected CRLF");
        }
    }
}