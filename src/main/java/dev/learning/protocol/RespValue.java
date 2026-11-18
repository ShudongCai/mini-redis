package dev.learning.protocol;

import java.util.List;

/**
 * A value exchanged through the RESP2 codec and command dispatcher.
 */
public sealed interface RespValue {
    record SimpleString(String value) implements RespValue {}

    record ErrorValue(String message) implements RespValue {}

    record IntegerValue(long value) implements RespValue {}

    /**
     * A binary payload whose {@code null} value represents a RESP null bulk string.
     */
    record BulkString(byte[] value) implements RespValue {}

    /**
     * A sequence whose {@code null} element list represents a RESP null array.
     */
    record ArrayValue(List<RespValue> elements) implements RespValue {}
}
