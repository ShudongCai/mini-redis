package dev.learning;

import dev.learning.command.CommandDispatcher;
import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.server.MiniRedisServer;
import dev.learning.storage.InMemoryStorage;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main(String[] args) throws IOException {
        InMemoryStorage storage = new InMemoryStorage();

        RespDecoder decoder = new RespDecoder();
        RespEncoder encoder = new RespEncoder();
        CommandDispatcher dispatcher = new CommandDispatcher(storage);

        try (MiniRedisServer server =
                     new MiniRedisServer(16380, decoder, encoder, dispatcher)) {
            server.acceptAndResponse();
        }
    }
}
