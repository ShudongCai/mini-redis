package dev.learning.command;

import dev.learning.command.handler.*;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class CommandDispatcher {
    private final SetCommandHandler setCommandHandler;
    private final ListCommandHandler listCommandHandler;
    private final KeyCommandHandler keyCommandHandler;
    private final StringCommandHandler stringCommandHandler;
    private final SortedSetCommandHandler sortedSetCommandHandler;

    public CommandDispatcher(
            InMemoryStorage storage
    ) {
        this.setCommandHandler = new SetCommandHandler(storage);
        this.listCommandHandler = new ListCommandHandler(storage);
        this.keyCommandHandler = new KeyCommandHandler(storage);
        this.stringCommandHandler = new StringCommandHandler(storage);
        this.sortedSetCommandHandler = new SortedSetCommandHandler(storage);
    }

    public RespValue dispatch(RespValue request) {
        if (request instanceof RespValue.BulkString(byte[] value) && value != null && value.length != 0) {
            String bulkValue = new String(value, StandardCharsets.US_ASCII);
            return keyCommandHandler.executePingCommand(bulkValue);
        }

        if (request instanceof RespValue.SimpleString(String value) && value != null) {
            return keyCommandHandler.executePingCommand(value);
        }

        if (request instanceof RespValue.ArrayValue(List<RespValue> elements)) {
            Optional<RespValue> validationError = CommandArguments.validateArrayRequest(elements);
            return validationError.orElseGet(() -> executeCommand(elements));
        }

        return new RespValue.ErrorValue("The Array Request is invalid");
    }

    private RespValue executeCommand(List<RespValue> elements) {
        Optional<String> command = CommandArguments.extractAsciiText(elements.getFirst());

        if (command.isEmpty()) {
            return new RespValue.ErrorValue("ERR command should not be null");
        }

        CommandType type = CommandArguments.isValidCommand(command.get());
        if (type == null) {
            return new RespValue.ErrorValue("Invalid Command type");
        }

        return switch (type) {
            case CommandType.SET -> stringCommandHandler.executeSetCommand(elements);
            case CommandType.DEL -> keyCommandHandler.executeDelCommand(elements);
            case CommandType.EXISTS -> keyCommandHandler.executeExistCommand(elements);
            case CommandType.GET -> stringCommandHandler.executeGetCommand(elements);
            case CommandType.TTL -> keyCommandHandler.executeTTLCommand(elements);
            case CommandType.EXPIRE -> keyCommandHandler.executeExpireCommand(elements);
            case CommandType.PERSIST -> keyCommandHandler.executePersistCommand(elements);
            case CommandType.LPUSH -> listCommandHandler.executeLPushCommand(elements);
            case CommandType.RPUSH -> listCommandHandler.executeRPushCommand(elements);
            case CommandType.RPOP -> listCommandHandler.executeRPopCommand(elements);
            case CommandType.LPOP -> listCommandHandler.executeLPopCommand(elements);
            case CommandType.LLEN -> listCommandHandler.executeLlenCommand(elements);
            case CommandType.LRANGE -> listCommandHandler.executeLrangeCommand(elements);
            case CommandType.SADD -> setCommandHandler.executeSaddCommand(elements);
            case CommandType.SCARD -> setCommandHandler.executeScardCommand(elements);
            case CommandType.SMEMBERS -> setCommandHandler.executeSmembersCommand(elements);
            case CommandType.SREM -> setCommandHandler.executeSremCommand(elements);
            case CommandType.SISMEMBER -> setCommandHandler.executeSismemberCommand(elements);
            case CommandType.ZADD -> sortedSetCommandHandler.executeZaddCommand(elements);
            case CommandType.ZRANGE -> sortedSetCommandHandler.executeZrangeCommand(elements);
            case CommandType.ZREM -> sortedSetCommandHandler.executeZremCommand(elements);
            case CommandType.ZSCORE -> sortedSetCommandHandler.executeZscoreCommand(elements);
            case PING -> elements.size() == 1
                    ? new RespValue.SimpleString("PONG")
                    : new RespValue.ErrorValue(
                    "ERR wrong number of arguments for 'ping' command"
            );
        };
    }
}
