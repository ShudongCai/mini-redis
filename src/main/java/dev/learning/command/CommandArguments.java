package dev.learning.command;

import dev.learning.exception.InvalidElementsException;
import dev.learning.exception.InvalidKeyException;
import dev.learning.protocol.RespValue;
import dev.learning.storage.model.RedisBytes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CommandArguments {
    public static Optional<RespValue> validateArrayRequest(List<RespValue> elements) {
        if (elements == null) {
            return Optional.of(new RespValue.ErrorValue("The command should not be null"));
        }

        if (elements.isEmpty()) {
            return Optional.of(new RespValue.ErrorValue("The command should not be empty"));
        }

        if (elements.getFirst() == null) {
            return Optional.of(new RespValue.ErrorValue("The command should not be null"));
        }

        if (!(elements.getFirst() instanceof RespValue.SimpleString ||
                elements.getFirst() instanceof  RespValue.BulkString)) {
            return Optional.of(new RespValue.ErrorValue("The first input should be a command"));
        }

        if (elements.getFirst() instanceof RespValue.SimpleString(String value)
                && (value == null || value.isBlank())) {
            return Optional.of(
                    new RespValue.ErrorValue("The command is blank")
            );
        }

        if (elements.getFirst() instanceof RespValue.BulkString(byte[] arr)
                && (arr == null || arr.length == 0)) {
            return Optional.of(
                    new RespValue.ErrorValue("The command is null or empty")
            );
        }

        return Optional.empty();
    }

    public static List<RedisBytes> decodeValues(List<RespValue> elements) {
        List<RedisBytes> values = new ArrayList<>();

        for (int i = 2; i < elements.size(); ++i) {
            values.add(extractValue(elements.get(i)));
        }

        return values;
    }

    /**
     * Extracts text from an ASCII RESP string, returning empty for other values.
     */
    public static Optional<String> extractAsciiText(RespValue request) {
        if (request instanceof RespValue.BulkString(byte[] value)
                && value != null) {
            for (byte b : value) {
                if (b < 0) {
                    return Optional.empty();
                }
            }
            return Optional.of(new String(value, StandardCharsets.US_ASCII));
        }

        if (request instanceof RespValue.SimpleString(String value)
                && value != null) {
            if (!value.chars().allMatch(character -> character < 128)) {
                return Optional.empty();
            }
            return Optional.of(value);
        }

        return Optional.empty();
    }

    public static RedisBytes extractKey(RespValue element) {
        return extractRedisBytes(element).orElseThrow(InvalidKeyException::new);
    }

    public static RedisBytes extractValue(RespValue element) {
        return extractRedisBytes(element).orElseThrow(InvalidElementsException::new);
    }

    private static Optional<RedisBytes> extractRedisBytes(RespValue element) {
        if (element instanceof RespValue.BulkString(byte[] value)
                && value != null) {
            return Optional.of(new RedisBytes(value));
        }

        if (element instanceof RespValue.SimpleString(String value)
                && value != null) {
            return Optional.of(
                    new RedisBytes(value.getBytes(StandardCharsets.UTF_8))
            );
        }

        return Optional.empty();
    }

    public static CommandType isValidCommand(String command) {
        for (CommandType type : CommandType.values()) {
            if (command.toLowerCase(Locale.ROOT).equals(type.getValue())) {
                return type;
            }
        }
        return null;
    }
}
