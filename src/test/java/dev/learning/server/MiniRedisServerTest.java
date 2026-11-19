package dev.learning.server;

import dev.learning.command.CommandDispatcher;
import dev.learning.protocol.RespDecoder;
import dev.learning.protocol.RespEncoder;
import dev.learning.protocol.RespValue;
import dev.learning.storage.InMemoryStorage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniRedisServerTest {
    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    void shouldStopAcceptingConnectionsWhenServerIsClosed() throws Exception {
        FutureTask<Void> acceptLoop;
        try (MiniRedisServer server = newServer(new RespDecoder())) {
            acceptLoop = acceptLoopTask(server);
            Thread acceptThread = Thread.ofPlatform().daemon().start(acceptLoop);

            // Wait until the listener is in accept() before closing it.
            assertTimeoutPreemptively(Duration.ofMillis(TIMEOUT_MILLIS), () -> {
                while (!isInAccept(acceptThread)) {
                    Thread.sleep(10);
                }
            }, "Server did not reach accept()");
        }

        acceptLoop.get(TIMEOUT_MILLIS, MILLISECONDS);
    }

    @Test
    void shouldCloseIdleClientConnectionsWhenServerIsClosed() throws Exception {
        CompletableFuture<Thread> workerStarted = new CompletableFuture<>();
        RespDecoder decoder = new RespDecoder() {
            @Override
            public RespValue readAndExecuteRespMessage(InputStream stream) throws IOException {
                workerStarted.complete(Thread.currentThread());
                return super.readAndExecuteRespMessage(stream);
            }
        };

        try (Socket client = new Socket()) {
            FutureTask<Void> acceptLoop;
            Thread worker;
            try (MiniRedisServer server = newServer(decoder)) {
                acceptLoop = acceptLoopTask(server);
                Thread.ofPlatform().daemon().start(acceptLoop);
                client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()),
                        TIMEOUT_MILLIS);
                client.setSoTimeout(TIMEOUT_MILLIS);
                worker = workerStarted.get(TIMEOUT_MILLIS, MILLISECONDS);
            }

            assertTrue(worker.join(Duration.ofMillis(TIMEOUT_MILLIS)), "Idle client worker did not terminate");
            assertEquals(-1, client.getInputStream().read(), "Client did not observe the connection closing");
            acceptLoop.get(TIMEOUT_MILLIS, MILLISECONDS);
        }
    }

    private static MiniRedisServer newServer(RespDecoder decoder) throws IOException {
        return new MiniRedisServer(0, decoder, new RespEncoder(), new CommandDispatcher(new InMemoryStorage(), new ReentrantReadWriteLock(true))::dispatch);
    }

    private static FutureTask<Void> acceptLoopTask(MiniRedisServer server) {
        return new FutureTask<>(() -> {
            server.acceptAndResponse();
            return null;
        });
    }

    private static boolean isInAccept(Thread thread) {
        return Arrays.stream(thread.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(ServerSocket.class.getName()) && frame.getMethodName().equals("accept"));
    }
}
