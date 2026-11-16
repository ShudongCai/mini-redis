package dev.learning.command.handler;

import dev.learning.command.CommandArguments;
import dev.learning.exception.InvalidElementsException;
import dev.learning.exception.InvalidKeyException;
import dev.learning.exception.WrongTypeException;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.model.RedisBytes;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class SortedSetCommandHandler {
    private final InMemoryStorage storage;

    public SortedSetCommandHandler(InMemoryStorage storage) {
        this.storage = storage;
    }


    public RespValue executeZaddCommand(List<RespValue> elements) {
        if (elements.size() < 3 || elements.size() % 2 != 0) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'zadd' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            Map<RedisBytes, Double> values = new HashMap<>();

            for (int i = 2; i < elements.size(); i += 2) {
                RedisBytes scoreBytes = CommandArguments.extractValue(elements.get(i));
                RedisBytes value = CommandArguments.extractValue(elements.get(i + 1));
                double score = Double.parseDouble(new String(scoreBytes.getBytes(), StandardCharsets.US_ASCII));

                if (Double.isNaN(score)) {
                    return new RespValue.ErrorValue("ERR value is not a valid float");
                }

                values.put(value, score);
            }
            return new RespValue.IntegerValue(storage.zadd(key, values));

        } catch (InvalidKeyException | WrongTypeException | InvalidElementsException e) {
            return new RespValue.ErrorValue(e.getMessage());
        } catch (NumberFormatException e) {
            return new RespValue.ErrorValue("Invalid score input");
        }
    }

    public RespValue executeZscoreCommand(List<RespValue> elements) {
        if (elements.size() != 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'zscore' command");
        }

        try {
          RedisBytes key = CommandArguments.extractKey(elements.get(1));
          RedisBytes value = CommandArguments.extractValue(elements.get(2));

          Optional<Double> oResult = storage.zscore(key, value);

            return oResult.map(aDouble ->
                    new RespValue.BulkString(aDouble.toString().getBytes(StandardCharsets.UTF_8)))
                    .orElseGet(() -> new RespValue.BulkString(null));

        } catch (WrongTypeException | InvalidKeyException | InvalidElementsException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeZremCommand(List<RespValue> elements) {
        if (elements.size() < 3) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'zrem' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            List<RedisBytes> values = new ArrayList<>();

            for (int i = 2; i < elements.size(); ++i) {
                values.add(CommandArguments.extractValue(elements.get(i)));
            }
            return new RespValue.IntegerValue(storage.zrem(key, values));
        } catch (InvalidKeyException | InvalidElementsException | WrongTypeException e) {
            return new RespValue.ErrorValue(e.getMessage());
        }
    }

    public RespValue executeZrangeCommand(List<RespValue> elements) {
        if (elements.size() != 4) {
            return new RespValue.ErrorValue("ERR wrong number of arguments for 'zrange' command");
        }

        try {
            RedisBytes key = CommandArguments.extractKey(elements.get(1));
            Optional<String> startS = CommandArguments.extractAsciiText(elements.get(2));
            Optional<String> endS = CommandArguments.extractAsciiText(elements.get(3));

            if (startS.isEmpty() || endS.isEmpty()) {
                return new RespValue.ErrorValue("ERR range input for 'zrange' command");
            }

            long start = Long.parseLong(startS.get());
            long end = Long.parseLong(endS.get());

            List<RedisBytes> list = storage.zrange(key, start, end);
            List<RespValue> result = new ArrayList<>();

            for (RedisBytes temp : list) {
                result.add(new RespValue.BulkString(temp.getBytes()));
            }

            return new RespValue.ArrayValue(result);
        } catch (InvalidKeyException | InvalidElementsException | WrongTypeException e) {
            return new RespValue.ErrorValue(e.getMessage());
        } catch (NumberFormatException e) {
            return new RespValue.ErrorValue("ERR range input for 'zrange' command");
        }
    }


}
