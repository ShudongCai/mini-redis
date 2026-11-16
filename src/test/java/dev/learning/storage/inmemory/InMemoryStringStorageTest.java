package dev.learning.storage.inmemory;

import dev.learning.command.SetOptions;
import dev.learning.storage.InMemoryStorage;
import dev.learning.storage.StorageTestClock;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.learning.storage.StorageTestSupport.bytes;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStringStorageTest {
    @Test
    void shouldStoreValueWhenKeyIsNew() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice"));

        //Assert
        assertTrue(storage.get(bytes("name")).isPresent());
        assertEquals(bytes("Alice"), storage.get(bytes("name")).get());
    }

    @Test
    void shouldOverwriteValueWhenKeyAlreadyExists() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice"));
        storage.set(bytes("name"), bytes("Ben"));

        //Assert
        assertTrue(storage.get(bytes("name")).isPresent());
        assertEquals(bytes("Ben"), storage.get(bytes("name")).get());
    }

    @Test
    void shouldPreserveSpacesInValue() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(bytes("name"), bytes("Alice And Ben"));

        //Assert
        assertTrue(storage.get(bytes("name")).isPresent());
        assertEquals(bytes("Alice And Ben"), storage.get(bytes("name")).get());
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
        //Arrange
        InMemoryStorage storage = new InMemoryStorage();

        //Act

        //Assert
        assertTrue(storage.get(bytes("name")).isEmpty());
    }

    @Test
    void shouldReturnValueWhenKeyHasNotExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));

        clock.advanceMillis(999);

        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnEmptyWhenKeyHasExpired() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));

        clock.advanceMillis(1_000);

        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldReturnValueWhenKeyHasNoExpiration() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        storage.set(bytes("name"), bytes("Alice"));

        clock.advanceMillis(DAYS.toMillis(1));

        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldClearExpirationWhenExistingKeyIsOverwritten() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(500);

        storage.set(bytes("name"), bytes("Ben"));
        clock.advanceMillis(1_000);

        assertEquals(Optional.of(bytes("Ben")), storage.get(bytes("name")));
    }

    @Test
    void shouldStoreEmptyString() {
        // Arrange
        InMemoryStorage storage = new InMemoryStorage();

        // Act
        storage.set(bytes("name"), bytes(""));

        // Assert
        assertEquals(Optional.of(bytes("")), storage.get(bytes("name")));
    }

    @Test
    void shouldSetValueWhenKeyDoesNotExistWithNx() {
        InMemoryStorage storage = new InMemoryStorage();

        boolean written = storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().nx().build());

        assertTrue(written);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldNotOverwriteValueWhenKeyExistsWithNx() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("name"), bytes("Alice"));

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().nx().build());

        assertFalse(written);
        assertEquals(Optional.of(bytes("Alice")), storage.get(bytes("name")));
    }

    @Test
    void shouldOverwriteValueWhenKeyExistsWithXx() {
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(bytes("name"), bytes("Alice"));

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().xx().build());

        assertTrue(written);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldNotSetValueWhenKeyDoesNotExistWithXx() {
        InMemoryStorage storage = new InMemoryStorage();

        boolean written = storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().xx().build());

        assertFalse(written);
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }

    @Test
    void shouldSetValueWhenKeyHasExpiredWithNx() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().nx().build());

        assertTrue(written);
        assertEquals(Optional.of(bytes("Bob")), storage.get(bytes("name")));
    }

    @Test
    void shouldNotSetValueWhenKeyHasExpiredWithXx() {
        StorageTestClock clock = new StorageTestClock();
        InMemoryStorage storage = clock.newStorage();
        assertTrue(storage.set(bytes("name"), bytes("Alice"), SetOptions.builder().ttlSeconds(1L).build()));
        clock.advanceMillis(1_000);

        boolean written = storage.set(bytes("name"), bytes("Bob"), SetOptions.builder().xx().build());

        assertFalse(written);
        assertEquals(Optional.empty(), storage.get(bytes("name")));
    }
}
