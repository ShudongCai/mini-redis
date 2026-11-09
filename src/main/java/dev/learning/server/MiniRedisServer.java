package dev.learning.server;

import dev.learning.command.CommandDispatcher;
import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.protocol.RespValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MiniRedisServer implements AutoCloseable{
    private final ServerSocket serverSocket;
    private final RespDecoder respDecoder;
    private final RespEncoder respEncoder;
    private final CommandDispatcher dispatcher;
    private final ExecutorService threadPools;

    public MiniRedisServer(int port, RespDecoder respDecoder, RespEncoder respEncoder, CommandDispatcher dispatcher) throws IOException {
        serverSocket = new ServerSocket(port);
        this.respDecoder = respDecoder;
        this.respEncoder = respEncoder;
        this.dispatcher = dispatcher;
        this.threadPools = Executors.newVirtualThreadPerTaskExecutor();
    }

    public MiniRedisServer(RespDecoder respDecoder, RespEncoder respEncoder, CommandDispatcher dispatcher) throws IOException{
        this(6380, respDecoder, respEncoder, dispatcher);
    }

    public void acceptAndResponse() throws IOException {
        System.out.println("Server listening on port " + serverSocket.getLocalPort());

        while (!serverSocket.isClosed()) {
            System.out.println("Waiting for client...");

            Socket socket = serverSocket.accept();

            System.out.println("Client connected: " + socket.getRemoteSocketAddress());

            threadPools.submit(() -> {
                startConnectionAndExecuteCommands(socket);
            });
        }
    }

    private void startConnectionAndExecuteCommands(Socket socket) {
        try (socket) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            while (true) {
                RespValue decodeValue = respDecoder.readAndExecuteRespMessage(input);

                if (decodeValue == null) {
                    break;
                }

                RespValue dispatchedValue = dispatcher.dispatch(decodeValue);
                respEncoder.encode(dispatchedValue, output);
                output.flush();
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }
}
