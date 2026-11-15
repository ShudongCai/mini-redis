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
import java.util.Optional;

public class ListCommandHandler {
    private final InMemoryStorage storage;

    public ListCommandHandler(InMemoryStorage storage) {
        this.storage = storage;
    }

    public RespValue executeLrangeCommand(List<RespValue> elements) {
        if (elements.size() != 4) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'lrange' command");
        }


        Optional<String> start = CommandArguments.extractAsciiText(elements.get(2));
        Optional<String> end = CommandArguments.extractAsciiText(elements.get(3));

        if (start.isEmpty() || end.isEmpty()) {
            return new RespValue.ErrorValue("ERR invalid argument");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));

            long startL = Long.parseLong(start.get());
            long endL = Long.parseLong(end.get());
            List<RedisBytes> temp = storage.lrange(key, startL, endL);
            List<RespValue> result = new ArrayList<>();

            for (RedisBytes t : temp) {
                result.add(new RespValue.BulkString(t.getBytes()));
            }

            return new RespValue.ArrayValue(result);

        } catch (NumberFormatException e) {
            return new RespValue.ErrorValue("ERR value is not an integer or out of range");
        } catch (WrongTypeException | InvalidKeyException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeLlenCommand(List<RespValue> elements) {
        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'llen' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            return new RespValue.IntegerValue(storage.llen(key));
        } catch (InvalidKeyException | WrongTypeException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeRPopCommand(List<RespValue> elements) {
        return pop(elements, false);
    }

    public RespValue executeLPopCommand(List<RespValue> elements) {
        return pop(elements, true);
    }

    private RespValue pop(List<RespValue> elements, boolean isLPop) {
        String command = isLPop ? "lpop" : "rpop";

        if (elements.size() != 2) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for '" + command + "' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            Optional<RedisBytes> value = isLPop ? storage.lpop(key) : storage.rpop(key);
            return value.map(s -> new RespValue.BulkString(s.getBytes()))
                    .orElseGet(() -> new RespValue.BulkString(null));
        } catch (InvalidKeyException | WrongTypeException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeRPushCommand(List<RespValue> elements) {
        return executePushCommand(elements, false);
    }

    public RespValue executeLPushCommand(List<RespValue> elements) {
        return executePushCommand(elements, true);
    }

    private RespValue executePushCommand(List<RespValue> elements, boolean isLPush) {
        String command = isLPush ? "lpush" : "rpush";

        if (elements.size() < 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for " + command + " command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            List<RedisBytes> input = CommandArguments.decodeValues(elements);

            long length = isLPush ? storage.lpush(key, input) : storage.rpush(key, input);
            return new RespValue.IntegerValue(length);
        } catch (WrongTypeException | InvalidElementsException | InvalidKeyException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }
}
