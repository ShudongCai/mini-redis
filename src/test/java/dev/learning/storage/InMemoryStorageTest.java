package dev.learning.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class InMemoryStorageTest {
    @Test
    void shouldStoreValueWhenKeyIsNew() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Alice", storage.get("name").get());
    }

    @Test
    void shouldOverwriteValueWhenKeyAlreadyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        storage.set("name", "Ben");

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Ben", storage.get("name").get());
    }

    @Test
    void shouldPreserveSpacesInValue() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice And Ben");

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Alice And Ben", storage.get("name").get());
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act

        //Assert
        assertTrue(storage.get("name").isEmpty());
    }

    @Test
    void shouldReturnValueWhenKeyHasNotExpired() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set("name", "Alice", 1);

        now.addAndGet(999);

        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldReturnEmptyWhenKeyHasExpired() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set("name", "Alice", 1);

        now.addAndGet(1_000);

        assertEquals(Optional.empty(), storage.get("name"));
    }

    @Test
    void shouldReturnValueWhenKeyHasNoExpiration() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set("name", "Alice");

        now.addAndGet(DAYS.toMillis(1));

        assertEquals(Optional.of("Alice"), storage.get("name"));
    }

    @Test
    void shouldClearExpirationWhenExistingKeyIsOverwritten() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set("name", "Alice", 1);
        now.addAndGet(500);

        storage.set("name", "Ben");
        now.addAndGet(1_000);

        assertEquals(Optional.of("Ben"), storage.get("name"));
    }

    @Test
    void shouldReturnZeroWhenExpiredKeyIsDeleted() {
        AtomicLong now = new AtomicLong();
        InMemoryStorage storage = new InMemoryStorage(now::get);
        storage.set("name", "Alice", 1);
        now.addAndGet(1_000);

        assertEquals(0, storage.del("name"));
    }

    @Test
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();
        storage.set("name", "Alice");

        // Act
        int deletedCount = storage.del("name");

        // Assert
        assertEquals(1, deletedCount);
        assertFalse(storage.exists("name"));
    }

    @Test
    void shouldReturnZeroWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        int value = storage.del("hello");

        //Assert
        assertEquals(0, value);
    }

    @Test
    void shouldReturnTrueWhenKeyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        boolean value = storage.exists("name");

        //Assert
        assertTrue(value);
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set("name", "Alice");
        boolean value = storage.exists("hello");

        //Assert
        assertFalse(value);
    }

    @Test
    void shouldStoreEmptyString() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();

        // Act
        storage.set("name", "");

        // Assert
        assertEquals(Optional.of(""), storage.get("name"));
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
                        storage.set("key-" + key, "value-" + key);
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
            assertEquals(Optional.of("value-" + key), storage.get("key-" + key), "key-" + key);
        }
    }

    @Test
    void shouldNotFailWhenGetAndDelRunConcurrently() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        int rounds = 2_000;
        for (int round = 0; round < rounds; round++) {
            storage.set("key-" + round, "value");
        }
        CyclicBarrier startRound = new CyclicBarrier(2);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            Future<Void> reads = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    String key = "key-" + round;
                    startRound.await(10, SECONDS);
                    storage.get(key).ifPresent(value -> assertEquals("value", value));
                }
                return null;
            });
            Future<Void> deletes = executor.submit(() -> {
                for (int round = 0; round < rounds; round++) {
                    String key = "key-" + round;
                    startRound.await(10, SECONDS);
                    assertEquals(1, storage.del(key), key);
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
