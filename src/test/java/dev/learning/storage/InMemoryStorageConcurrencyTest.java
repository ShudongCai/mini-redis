package dev.learning.storage;

import dev.learning.command.SetOptions;
import dev.learning.storage.model.RedisBytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

import static dev.learning.storage.StorageTestSupport.bytes;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InMemoryStorageConcurrencyTest {
    @Test
    void shouldNotMissKeyWhenGetOverlapsOverwriteAtExpiration() throws Exception {
        assertReadSurvivesOverwrite(storage -> storage.get(bytes("key")).isPresent());
    }

    @Test
    void shouldNotMissKeyWhenExistsOverlapsOverwriteAtExpiration() throws Exception {
        assertReadSurvivesOverwrite(storage -> storage.exists(bytes("key")));
    }

    @Test
    void shouldStartSetExpirationAfterWaitingForConcurrentDelete() throws Exception {
        PausedClock clock = new PausedClock();
        InMemoryStorage storage = new InMemoryStorage(clock);
        assertTrue(storage.set(bytes("key"), bytes("old"), SetOptions.builder().ttlSeconds(10L).build()));

        FutureTask<Integer> delete = new FutureTask<>(() -> storage.del(bytes("key")));
        clock.pauseOn(delete);
        FutureTask<Boolean> write = new FutureTask<>(
                () -> storage.set(bytes("key"), bytes("new"), SetOptions.builder().nx().ttlSeconds(1L).build()));
        try {
            clock.awaitPause();
            Thread writer = Thread.ofPlatform().daemon().start(write);
            awaitBlockedOrDone(writer, write);
            assertFalse(write.isDone(), "SET should wait for DEL on the same key");
            clock.now.set(1_000);
        } finally {
            clock.resume.countDown();
        }

        assertEquals(1, delete.get(5, SECONDS));
        assertTrue(write.get(5, SECONDS));
        clock.now.set(1_999);
        assertEquals(Optional.of(bytes("new")), storage.get(bytes("key")));
        clock.now.set(2_000);
        assertEquals(Optional.empty(), storage.get(bytes("key")));
    }

    private static void assertReadSurvivesOverwrite(Function<InMemoryStorage, Boolean> readKey)
            throws Exception {
        PausedClock clock = new PausedClock();
        InMemoryStorage storage = new InMemoryStorage(clock);
        assertTrue(storage.set(bytes("key"), bytes("old"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.now.set(999);

        FutureTask<Boolean> read = new FutureTask<>(() -> readKey.apply(storage));
        clock.pauseOn(read);
        FutureTask<Boolean> overwrite = new FutureTask<>(() -> storage.set(bytes("key"), bytes("new")));
        try {
            clock.awaitPause();
            Thread writer = Thread.ofPlatform().daemon().start(overwrite);
            awaitBlockedOrDone(writer, overwrite);
            if (overwrite.isDone()) {
                // An unlocked read now holds a snapshot that has expired since replacement.
                clock.now.set(1_000);
            }
        } finally {
            clock.resume.countDown();
        }

        assertTrue(read.get(5, SECONDS), "The key was live throughout the read");
        assertTrue(overwrite.get(5, SECONDS));
        clock.now.set(1_000);
        assertEquals(Optional.of(bytes("new")), storage.get(bytes("key")));
    }

    private static void awaitBlockedOrDone(Thread thread, FutureTask<?> task) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && !task.isDone()) {
            if (System.nanoTime() - deadline >= 0) {
                fail("Writer did not reach the map operation");
            }
            Thread.sleep(1);
        }
    }

    private static final class PausedClock implements LongSupplier {
        private final AtomicLong now = new AtomicLong();
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private Thread pausedThread;

        private void pauseOn(FutureTask<?> task) {
            pausedThread = Thread.ofPlatform().daemon().unstarted(task);
            pausedThread.start();
        }

        private void awaitPause() throws InterruptedException {
            assertTrue(paused.await(5, SECONDS), "Worker did not reach the expiration check");
        }

        @Override
        public long getAsLong() {
            if (Thread.currentThread() == pausedThread) {
                paused.countDown();
                try {
                    assertTrue(resume.await(10, SECONDS), "Test did not resume the clock");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return now.get();
        }
    }

    @Test
    void shouldPreserveAllElementsWhenPushingConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int elementsPerWorker = 64;
        List<RedisBytes> expected = IntStream.range(0, workers * elementsPerWorker)
                .mapToObj(index -> bytes(Integer.toString(index)))
                .toList();
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<Void>> pushes = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                int first = worker * elementsPerWorker;
                boolean pushLeft = worker % 2 == 0;
                pushes.add(executor.submit(() -> {
                    start.await(5, SECONDS);
                    for (RedisBytes value : expected.subList(first, first + elementsPerWorker)) {
                        if (pushLeft) {
                            storage.lpush(bytes("items"), List.of(value));
                        } else {
                            storage.rpush(bytes("items"), List.of(value));
                        }
                    }
                    return null;
                }));
            }
            for (Future<Void> push : pushes) {
                push.get(10, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<RedisBytes> actual = storage.lrange(bytes("items"), 0, -1);
        assertEquals(expected.size(), actual.size());
        assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    void shouldPopEachElementAtMostOnceWhenPoppingConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int popsPerWorker = 64;
        List<RedisBytes> expected = IntStream.range(0, workers * popsPerWorker)
                .mapToObj(index -> bytes(Integer.toString(index)))
                .toList();
        storage.rpush(bytes("items"), expected);
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<List<RedisBytes>>> pops = new ArrayList<>();
        List<RedisBytes> popped = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                boolean popLeft = worker % 2 == 0;
                pops.add(executor.submit(() -> {
                    start.await(5, SECONDS);
                    List<RedisBytes> values = new ArrayList<>();
                    for (int attempt = 0; attempt < popsPerWorker; attempt++) {
                        Optional<RedisBytes> value = popLeft ? storage.lpop(bytes("items")) : storage.rpop(bytes("items"));
                        value.ifPresent(values::add);
                    }
                    return values;
                }));
            }
            for (Future<List<RedisBytes>> pop : pops) {
                popped.addAll(pop.get(10, SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(expected.size(), popped.size());
        assertEquals(new HashSet<>(expected), new HashSet<>(popped));
        assertFalse(storage.exists(bytes("items")));
    }

    @Test
    void shouldReturnConsistentSnapshotWhenLrangeRunsConcurrentlyWithPush() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 128;
        int batchSize = 8;
        List<RedisBytes> expected = IntStream.range(0, (rounds + 1) * batchSize)
                .mapToObj(index -> bytes(Integer.toString(index)))
                .toList();
        storage.rpush(bytes("items"), expected.subList(0, batchSize));
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> pushes = executor.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    int first = round * batchSize;
                    startRound.await(5, SECONDS);
                    storage.rpush(bytes("items"), expected.subList(first, first + batchSize));
                }
                return null;
            });
            Future<Void> reads = executor.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    int beforeSize = round * batchSize;
                    int afterSize = beforeSize + batchSize;
                    startRound.await(5, SECONDS);
                    List<RedisBytes> snapshot = storage.lrange(bytes("items"), 0, -1);

                    // The next batch waits at the barrier until this snapshot is checked.
                    assertTrue(snapshot.size() == beforeSize || snapshot.size() == afterSize,
                            "Round " + round + " returned a partial batch");
                    assertEquals(expected.subList(0, snapshot.size()), snapshot, "Round " + round);
                }
                return null;
            });

            reads.get(10, SECONDS);
            pushes.get(10, SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(expected, storage.lrange(bytes("items"), 0, -1));
    }

    @Test
    void shouldStoreAllValuesWhenDifferentKeysAreSetConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int workers = 8;
        int keysPerWorker = 250;
        CyclicBarrier start = new CyclicBarrier(workers);
        List<Future<Void>> writes = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (int worker = 0; worker < workers; worker++) {
                int firstKey = worker * keysPerWorker;
                writes.add(executor.submit(() -> {
                    start.await(10, SECONDS);
                    for (int key = firstKey; key < firstKey + keysPerWorker; key++) {
                        storage.set(bytes("key-" + key), bytes("value-" + key));
                    }
                    return null;
                }));
            }
            for (Future<Void> write : writes) {
                write.get(10, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        for (int key = 0; key < workers * keysPerWorker; key++) {
            assertEquals(Optional.of(bytes("value-" + key)), storage.get(bytes("key-" + key)), "key-" + key);
        }
    }

    @Test
    void shouldNotFailWhenGetAndDelRunConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 2_000;
        for (int round = 0; round < rounds; round++) {
            storage.set(bytes("key-" + round), bytes("value"));
        }
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> reads = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    RedisBytes key = bytes("key-" + round);
                    startRound.await(10, SECONDS);
                    storage.get(key).ifPresent(value -> assertEquals(bytes("value"), value));
                }
                return null;
            });
            Future<Void> deletes = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    RedisBytes key = bytes("key-" + round);
                    startRound.await(10, SECONDS);
                    assertEquals(1, storage.del(key), "key-" + round);
                }
                return null;
            });

            reads.get(10, SECONDS);
            deletes.get(10, SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
