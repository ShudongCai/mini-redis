package dev.learning;

import dev.learning.command.CommandDispatcher;
import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.scheduler.BackgroundScheduler;
import dev.learning.server.MiniRedisServer;
import dev.learning.storage.InMemoryStorage;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class App {
    public static void main(String[] args) throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        RespDecoder decoder = new RespDecoder();
        RespEncoder encoder = new RespEncoder();
        ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock(true);
        CommandDispatcher dispatcher = new CommandDispatcher(storage, reentrantReadWriteLock);

        try (
                BackgroundScheduler scheduler = new BackgroundScheduler();
                MiniRedisServer server =
                        new MiniRedisServer(16380, decoder, encoder, dispatcher)
        ) {
            scheduler.scheduledWithFixedDelay(
                    storage.expirationCleanupTask(),
                    1,
                    5,
                    TimeUnit.SECONDS
            );

            server.acceptAndResponse();
        }
    }
}
