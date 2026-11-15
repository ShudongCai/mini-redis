package dev.learning.command.handler;

import dev.learning.command.CommandArguments;
import dev.learning.exception.InvalidKeyException;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.model.RedisBytes;

import java.util.List;
import java.util.Optional;

public class KeyCommandHandler {
    private final InMemoryStorage storage;

    public KeyCommandHandler(InMemoryStorage storage) {
        this.storage = storage;
    }

    public RespValue executePersistCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'persist' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            int result = storage.persist(key);
            return new RespValue.IntegerValue(result);
        } catch (InvalidKeyException | IllegalArgumentException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeExpireCommand(List<RespValue> elements) {
        if (elements.size() != 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'expire' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            Optional<String> sTTL = CommandArguments.extractAsciiText(elements.get(2));

            if (sTTL.isEmpty()) {
                return new RespValue.ErrorValue("The Expire command is invalid");
            }

            long seconds = Long.parseLong(sTTL.get());
            int result = storage.expire(key, seconds);
            return new RespValue.IntegerValue(result);
        } catch (NumberFormatException | ArithmeticException e) {
            return new RespValue.ErrorValue(
                    "ERR invalid expire time"
            );
        } catch (InvalidKeyException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeTTLCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'ttl' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            long result = storage.ttl(key);
            return new RespValue.IntegerValue(result);
        } catch (InvalidKeyException | IllegalArgumentException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeExistCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'exists' command"
            );
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            boolean result = storage.exists(key);

            return new RespValue.IntegerValue(result ? 1 : 0);
        } catch (InvalidKeyException | IllegalArgumentException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeDelCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'del' command"
            );
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            int result = storage.del(key);

            return new RespValue.IntegerValue(result);
        }
        catch (InvalidKeyException | IllegalArgumentException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executePingCommand(String value) {
        if (!value.equalsIgnoreCase("PING")) {
            return new RespValue.ErrorValue("Single Bulk Command only support PING");
        }

        return new RespValue.SimpleString("PONG");
    }
}
