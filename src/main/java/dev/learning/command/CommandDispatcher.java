package dev.learning.command;

import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CommandDispatcher {
    private final InMemoryStorage storage;

    public CommandDispatcher(
            InMemoryStorage storage
    ) {
        this.storage = storage;
    }

    public RespValue dispatch(RespValue request) {
        if (request instanceof RespValue.BulkString(byte[] value) && value != null && value.length != 0) {
            String bulkValue = new String(value, StandardCharsets.US_ASCII);
            return executePingCommand(bulkValue);
        }

        if (request instanceof RespValue.SimpleString(String value) && value != null) {
            return executePingCommand(value);
        }

        if (request instanceof RespValue.ArrayValue(List<RespValue> elements)) {
            Optional<RespValue> validationError = validateArrayRequest(elements);
            return validationError.orElseGet(() -> executeCommand(elements));
        }

        return new RespValue.ErrorValue("The Array Request is invalid");
    }

    private String extractCommand(List<RespValue> elements) {
        if (elements.getFirst() instanceof RespValue.BulkString(byte[] value)) {
            return new String(value, StandardCharsets.US_ASCII);
        }

        if (elements.getFirst() instanceof RespValue.SimpleString(String value)) {
            return value;
        }

        throw new IllegalArgumentException("Invalid command");
    }

    private Optional<String> extractBulkOrSimpleString(RespValue request) {
        if (request instanceof RespValue.BulkString(byte[] value)
                && value != null) {
            return Optional.of(new String(value, StandardCharsets.UTF_8));
        }

        if (request instanceof RespValue.SimpleString(String value)
                && value != null) {
            return Optional.of(value);
        }

        return Optional.empty();
    }

    private CommandType isValidCommand(String command) {
        for (CommandType type : CommandType.values()) {
            if (command.trim().toLowerCase(Locale.ROOT).equals(type.getValue())) {
                return type;
            }
        }
        return null;
    }

    private RespValue executeCommand(List<RespValue> elements) {
        String command = extractCommand(elements);
        CommandType type = isValidCommand(command);
        if (type == null) {
            return new RespValue.ErrorValue("Invalid Command type");
        }

        return switch (type) {
            case CommandType.SET -> executeSetCommand(elements);
            case CommandType.DEL -> executeDelCommand(elements);
            case CommandType.EXISTS -> executeExistCommand(elements);
            case CommandType.GET -> executeGetCommand(elements);
            case PING -> elements.size() == 1
                    ? new RespValue.SimpleString("PONG")
                    : new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'ping' command"
            );
        };
    }

    private RespValue executeGetCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'get' command"
            );
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid key");
        }

        Optional<String> result = storage.get(key.get());

        if (result.isEmpty()) {
            return new RespValue.BulkString(null);
        }

        return new RespValue.BulkString(
                result.get().getBytes(StandardCharsets.UTF_8)
        );
    }

    private RespValue executeExistCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'exists' command"
            );
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid key");
        }

        boolean result = storage.exists(key.get());

        return new RespValue.IntegerValue(result ? 1 : 0);
    }

    private RespValue executeDelCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'del' command"
            );
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid key");
        }

        int result = storage.del(key.get());

        return new RespValue.IntegerValue(result);
    }

    private RespValue executeSetCommand(List<RespValue> elements) {
        if (elements.size() != 3) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'set' command"
            );
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));
        Optional<String> value = extractBulkOrSimpleString(elements.get(2));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid key");
        }

        if (value.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid value");
        }

        storage.set(key.get(), value.get());

        return new RespValue.SimpleString("OK");
    }

    private Optional<RespValue> validateArrayRequest(List<RespValue> elements) {
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

    private RespValue executePingCommand(String value) {
        if (!value.equalsIgnoreCase("PING")) {
            return new RespValue.ErrorValue("Single Bulk Command only support PING");
        }

        return new RespValue.SimpleString("PONG");
    }
}
