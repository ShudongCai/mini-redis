package dev.learning.server;

import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.protocol.RespValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Serves RESP2 clients on one virtual thread per connection.
 */
public class MiniRedisServer implements AutoCloseable{
    private final ServerSocket serverSocket;
    private final RespDecoder respDecoder;
    private final RespEncoder respEncoder;
    private final Function<RespValue, RespValue> commandExecutor;
    private final ExecutorService threadPools;

    public MiniRedisServer(int port,
                           RespDecoder respDecoder,
                           RespEncoder respEncoder,
                           Function<RespValue, RespValue> commandExecutor) throws IOException {
        serverSocket = new ServerSocket(port);
        this.respDecoder = respDecoder;
        this.respEncoder = respEncoder;
        this.commandExecutor = commandExecutor;
        this.threadPools = Executors.newVirtualThreadPerTaskExecutor();
    }

    public MiniRedisServer(RespDecoder respDecoder, RespEncoder respEncoder, Function<RespValue, RespValue> commandExecutor) throws IOException{
        this(6380, respDecoder, respEncoder, commandExecutor);
    }

    int getLocalPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Accepts and dispatches client connections until the listening socket closes.
     */
    public void acceptAndResponse() throws IOException {
        System.out.println("Server listening on port " + serverSocket.getLocalPort());

        while (!serverSocket.isClosed()) {
            System.out.println("Waiting for client...");

            try {
                Socket socket = serverSocket.accept();

                System.out.println("Client connected: " + socket.getRemoteSocketAddress());

                try {
                    threadPools.submit(() -> startConnectionAndExecuteCommands(socket));
                } catch (RejectedExecutionException e) {
                    socket.close();

                    if (serverSocket.isClosed()) {
                        break;
                    }

                    throw e;
                }
            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    break;
                }
                throw e;
            }

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

                RespValue dispatchedValue = commandExecutor.apply(decodeValue);
                respEncoder.encode(dispatchedValue, output);
                output.flush();
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }

    /**
     * Closes the listener, interrupts client handlers, and waits up to ten seconds for them to exit.
     */
    @Override
    public void close() throws IOException {
        try {
            serverSocket.close();


        } finally {
            threadPools.shutdownNow();
            try {
                if (!threadPools.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Some client handlers are still running");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
