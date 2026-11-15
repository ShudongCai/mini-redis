package dev.learning.command.handler;

import dev.learning.command.CommandArguments;
import dev.learning.exception.InvalidElementsException;
import dev.learning.exception.InvalidKeyException;
import dev.learning.exception.WrongTypeException;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.model.RedisBytes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SetCommandHandler {
    private final InMemoryStorage storage;

    public SetCommandHandler(InMemoryStorage storage) {
        this.storage = storage;
    }

    public RespValue executeSismemberCommand(List<RespValue> elements) {
        if (elements.size() != 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'sismember' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            RedisBytes value = CommandArguments.extractValue(elements.get(2));

            return new RespValue.IntegerValue(storage.sismember(key, value));
        } catch (InvalidKeyException | WrongTypeException | InvalidElementsException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeSremCommand(List<RespValue> elements) {
        if (elements.size() < 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'srem' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            List<RedisBytes> values = CommandArguments.decodeValues(elements);
            return new RespValue.IntegerValue(storage.srem(key, values));

        } catch (InvalidKeyException | WrongTypeException | InvalidElementsException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeSmembersCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'smembers' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            Set<RedisBytes> values = storage.smembers(key);
            List<RespValue> result = new ArrayList<>();

            for (RedisBytes value : values) {
                result.add(new RespValue.BulkString(value.getBytes()));
            }

            return new RespValue.ArrayValue(result);
        } catch (InvalidKeyException | WrongTypeException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeScardCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'scard' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            return new RespValue.IntegerValue(storage.scard(key));
        } catch (WrongTypeException | InvalidKeyException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeSaddCommand(List<RespValue> elements) {
        if (elements.size() < 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'sadd' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            List<RedisBytes> values = CommandArguments.decodeValues(elements);
            return new RespValue.IntegerValue(storage.sadd(key, values));
        } catch (WrongTypeException | InvalidKeyException | InvalidElementsException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }
}
