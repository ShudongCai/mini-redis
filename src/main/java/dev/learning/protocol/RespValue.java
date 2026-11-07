package dev.learning.protocol;

import java.util.List;

public sealed interface RespValue {
    record SimpleString(String value) implements RespValue {}

    record ErrorValue(String message) implements RespValue {}

    record IntegerValue(long value) implements RespValue {}

    record BulkString(byte[] value) implements RespValue {}

    record ArrayValue(List<RespValue> elements) implements RespValue {}
}
