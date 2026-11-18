package dev.learning.command;

import dev.learning.command.handler.*;
import dev.learning.persistence.AofService;
import dev.learning.persistence.model.AofEntry;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.model.RedisBytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CommandDispatcher {
    private final SetCommandHandler setCommandHandler;
    private final ListCommandHandler listCommandHandler;
    private final KeyCommandHandler keyCommandHandler;
    private final StringCommandHandler stringCommandHandler;
    private final SortedSetCommandHandler sortedSetCommandHandler;
    private final SnapshotCommandHandler snapshotCommandHandler;
    private final ReentrantReadWriteLock snapshotLock;
    private final InMemoryStorage storage;
    private final AofService aof;

    public CommandDispatcher(
            InMemoryStorage storage,
            ReentrantReadWriteLock snapshotLock
    ) {
        this(storage, snapshotLock, null);
    }

    public CommandDispatcher(InMemoryStorage storage, ReentrantReadWriteLock snapshotLock, AofService aof) {
        this.storage = storage;
        this.aof = aof;
        this.setCommandHandler = new SetCommandHandler(storage);
        this.listCommandHandler = new ListCommandHandler(storage);
        this.keyCommandHandler = new KeyCommandHandler(storage);
        this.stringCommandHandler = new StringCommandHandler(storage);
        this.sortedSetCommandHandler = new SortedSetCommandHandler(storage);
        this.snapshotCommandHandler = new SnapshotCommandHandler(storage);
        this.snapshotLock = snapshotLock;
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

        boolean journalMutation = aof != null && isMutation(type);
        Lock lock = type == CommandType.SAVE || journalMutation
                ? snapshotLock.writeLock() : snapshotLock.readLock();

        lock.lock();

        try {
            if (aof != null && type == CommandType.SAVE) {
                return new RespValue.ErrorValue("ERR SAVE is unavailable in AOF mode");
            }
            if (aof != null && !aof.isAvailable() && type != CommandType.PING) {
                return new RespValue.ErrorValue("ERR AOF persistence is unavailable");
            }

            RespValue response = switch (type) {
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
                case CommandType.SAVE -> snapshotCommandHandler.executeSaveCommand(elements);
                case PING -> elements.size() == 1
                        ? new RespValue.SimpleString("PONG")
                        : new RespValue.ErrorValue(
                        "ERR wrong number of arguments for 'ping' command"
                );
            };

            if (journalMutation && !(response instanceof RespValue.ErrorValue)) {
                RedisBytes key = CommandArguments.extractKey(elements.get(1));
                AofEntry entry = storage.captureSnapshotEntry(key)
                        .<AofEntry>map(AofEntry.Replace::new)
                        .orElseGet(() -> new AofEntry.Delete(key));
                try {
                    aof.append(entry);
                } catch (IOException e) {
                    System.err.println("AOF append failed: " + e.getMessage());
                    return new RespValue.ErrorValue("ERR AOF persistence failed, command result is uncertain");
                }
            }
            return response;
        } catch (IOException e) {
            return new RespValue.ErrorValue("ERR snapshot failed");
        } finally {
            lock.unlock();
        }
    }

    private static boolean isMutation(CommandType type) {
        return switch (type) {
            case SET, DEL, EXPIRE, PERSIST, LPUSH, RPUSH, LPOP, RPOP, SADD, SREM, ZADD, ZREM -> true;
            default -> false;
        };
    }
}
