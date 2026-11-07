package dev.learning.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MiniRedisServer implements AutoCloseable{
    private final ServerSocket serverSocket;
    public MiniRedisServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public MiniRedisServer() throws IOException{
        this(6380);
    }

    public void acceptAndResponse() throws IOException {
        Socket socket = serverSocket.accept();

        try (socket) {

        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }
}
