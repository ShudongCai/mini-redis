package dev.learning.command;

import dev.learning.exception.WrongTypeException;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
            try {
                return Optional.of(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString());
            } catch (CharacterCodingException e) {
                return Optional.empty();
            }
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
            case CommandType.TTL -> executeTTLCommand(elements);
            case CommandType.EXPIRE -> executeExpireCommand(elements);
            case CommandType.PERSIST -> executePersistCommand(elements);
            case CommandType.LPUSH -> executeLPushCommand(elements);
            case CommandType.RPUSH -> executeRPushCommand(elements);
            case PING -> elements.size() == 1
                    ? new RespValue.SimpleString("PONG")
                    : new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'ping' command"
            );
        };
    }

    private RespValue executeRPushCommand(List<RespValue> elements) {
        return executePushCommand(elements, false);
    }

    private RespValue executeLPushCommand(List<RespValue> elements) {
        return executePushCommand(elements, true);
    }

    private RespValue executePushCommand(List<RespValue> elements, boolean isLPush) {
        String command = isLPush ? "lpush" : "rpush";

        if (elements.size() < 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for " + command + " command");
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid key argument");
        }

        List<String> input = new ArrayList<>();

        for (int i = 2; i < elements.size(); ++i) {
            Optional<String> temp = extractBulkOrSimpleString(elements.get(i));

            if (temp.isEmpty()) {
                return new RespValue.ErrorValue("ERR invalid list element");
            }

            input.add(temp.get());
        }

        try {
            long length = isLPush ? storage.lpush(key.get(), input) : storage.rpush(key.get(), input);
            return new RespValue.IntegerValue(length);
        } catch (WrongTypeException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    private RespValue executePersistCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'persist' command");
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("The Persist command is invalid");
        }

        int result = storage.persist(key.get());
        return new RespValue.IntegerValue(result);
    }

    private RespValue executeExpireCommand(List<RespValue> elements) {
        if (elements.size() != 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'expire' command");
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));
        Optional<String> sTTL = extractBulkOrSimpleString(elements.get(2));

        if (key.isEmpty() || sTTL.isEmpty()) {
            return new RespValue.ErrorValue("The Expire command is invalid");
        }

        try {
            long seconds = Long.parseLong(sTTL.get());
            int result = storage.expire(key.get(), seconds);
            return new RespValue.IntegerValue(result);
        } catch (NumberFormatException | ArithmeticException e) {
            return new RespValue.ErrorValue(
                    "ERR invalid expire time"
            );
        }
    }

    private RespValue executeTTLCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'ttl' command");
        }

        Optional<String> key = extractBulkOrSimpleString(elements.get(1));

        if (key.isEmpty()) {
            return new RespValue.ErrorValue("The TTL command is invalid");
        }

        long result = storage.ttl(key.get());

        return new RespValue.IntegerValue(result);
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
        if (elements.size() < 3) {
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

        if (elements.size() > 3) {
            try {
                Optional<SetOptions> subOptions = extractSubcommand(elements);
                if (subOptions.isEmpty()) {
                    return new RespValue.ErrorValue("Suboptions are invalid");
                }

                if (subOptions.get().nx() && subOptions.get().xx()) {
                    return new RespValue.ErrorValue("NX and XX cannot be set together");
                }

                boolean settable = storage.set(key.get(), value.get(), subOptions.get());
                return settable ? new RespValue.SimpleString("OK")
                        : new RespValue.BulkString(null);
            } catch (IllegalArgumentException | ArithmeticException e) {
                return new RespValue.ErrorValue("ERR invalid SET options");
            }
        }

        boolean settable = storage.set(key.get(), value.get());

        return settable ? new RespValue.SimpleString("OK")
                : new RespValue.BulkString(null);
    }

    private Optional<SetOptions> extractSubcommand(List<RespValue> elements) {
        SetOptions.Builder builder = new SetOptions.Builder();


        for (int i = 3; i < elements.size(); i++) {
            Optional<String> command = extractBulkOrSimpleString(elements.get(i));

            if (command.isEmpty()) {
                return Optional.empty();
            }

            switch (command.get().trim().toUpperCase(Locale.ROOT)) {
                case "EX" -> {
                    if (i + 1 >= elements.size()) {
                        return Optional.empty();
                    }

                    Optional<String> secondsText =
                            extractBulkOrSimpleString(elements.get(++i));

                    if (secondsText.isEmpty()) {
                        return Optional.empty();
                    }

                    long seconds = Long.parseLong(secondsText.get());
                    builder.ttlSeconds(seconds);
                }

                case "NX" -> builder.nx();
                case "XX" -> builder.xx();
                default -> throw new IllegalArgumentException("Invalid SET option");
            }
        }

        return Optional.of(builder.build());
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
