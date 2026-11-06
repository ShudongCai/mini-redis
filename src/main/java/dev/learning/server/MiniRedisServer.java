package dev.learning.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MiniRedisServer {
    private final int port;
    private final ServerSocket serverSocket;
    public MiniRedisServer(int port) throws IOException {
        this.port = port;
        serverSocket = new ServerSocket(port);
    }

    public MiniRedisServer() throws IOException{
        this(6380);
    }

    public void acceptAndResponse() throws IOException {
        try {
            Socket socket = serverSocket.accept();

            try(socket) {

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
