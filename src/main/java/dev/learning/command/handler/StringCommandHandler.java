package dev.learning.command.handler;

import dev.learning.command.CommandArguments;
import dev.learning.command.SetOptions;
import dev.learning.exception.InvalidKeyException;
import dev.learning.exception.WrongTypeException;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.model.RedisBytes;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class StringCommandHandler {
    private final InMemoryStorage storage;

    public StringCommandHandler(InMemoryStorage storage) {
        this.storage = storage;
    }

    public RespValue executeGetCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'get' command"
            );
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            Optional<RedisBytes> result = storage.get(key);

            return result.map(s -> new RespValue.BulkString(s.getBytes()))
                    .orElseGet(() -> new RespValue.BulkString(null));
        } catch (WrongTypeException | IllegalArgumentException | InvalidKeyException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }

    }

    public RespValue executeSetCommand(List<RespValue> elements) {
        if (elements.size() < 3) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'set' command"
            );
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            RedisBytes value = CommandArguments.extractValue(elements.get(2));

            if (elements.size() > 3) {
                Optional<SetOptions> subOptions = extractSubcommand(elements);
                if (subOptions.isEmpty()) {
                    return new RespValue.ErrorValue("Suboptions are invalid");
                }

                if (subOptions.get().nx() && subOptions.get().xx()) {
                    return new RespValue.ErrorValue("NX and XX cannot be set together");
                }

                boolean settable = storage.set(key, value, subOptions.get());
                return settable ? new RespValue.SimpleString("OK")
                        : new RespValue.BulkString(null);
            }

            boolean settable = storage.set(key, value);

            return settable ? new RespValue.SimpleString("OK")
                    : new RespValue.BulkString(null);
        } catch (InvalidKeyException | IllegalArgumentException e) {
            return new RespValue.ErrorValue(e.getMessage());
        } catch (ArithmeticException e) {
            return new RespValue.ErrorValue("ERR invalid SET options");
        }
    }

    private Optional<SetOptions> extractSubcommand(List<RespValue> elements) {
        SetOptions.Builder builder = new SetOptions.Builder();


        for (int i = 3; i < elements.size(); i++) {
            Optional<String> command = CommandArguments.extractAsciiText(elements.get(i));

            if (command.isEmpty()) {
                return Optional.empty();
            }

            switch (command.get().toUpperCase(Locale.ROOT)) {
                case "EX" -> {
                    if (i + 1 >= elements.size()) {
                        return Optional.empty();
                    }

                    Optional<String> secondsText =
                            CommandArguments.extractAsciiText(elements.get(++i));

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
}
