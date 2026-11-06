package dev.learning.Storage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryStorageTest {
    @Test
    void shouldStoreValueWhenKeyIsNew() {
        //Arrange
        String input = "name Alice";
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(input);

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Alice", storage.get("name").get());
    }

    @Test
    void shouldOverwriteValueWhenKeyAlreadyExists() {
        //Arrange
        String inputExists = "name Alice";
        String input = "name Ben";
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(inputExists);
        storage.set(input);

        //Assert
        assertTrue(storage.get("name").isPresent());
        assertEquals("Ben", storage.get("name").get());
    }

    @Test
    void shouldPreserveSpacesWhenValueIsQuoted() {
        //Arrange
        String input = "name \"Alice And Ben\"";
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(input);

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
    void shouldReturnOneWhenExistingKeyIsDeleted() {
        // Arrange
        String input = "name Alice";
        InMemoryStorage storage = new InMemoryStorage();
        storage.set(input);

        // Act
        int deletedCount = storage.del("name");

        // Assert
        assertEquals(1, deletedCount);
        assertFalse(storage.exists("name"));
    }

    @Test
    void shouldReturnZeroWhenDeletingNonExistingKey() {
        //Arrange
        String input = "name Alice";
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(input);
        int value = storage.del("hello");

        //Assert
        assertEquals(0, value);
    }

    @Test
    void shouldReturnTrueWhenKeyExists() {
        //Arrange
        String input = "name Alice";
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(input);
        boolean value = storage.exists("name");

        //Assert
        assertTrue(value);
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        //Arrange
        String input = "name Alice";
        InMemoryStorage storage = new InMemoryStorage();

        //Act
        storage.set(input);
        boolean value = storage.exists("hello");

        //Assert
        assertFalse(value);
    }

    @Test
    void shouldStoreEmptyStringWhenValueIsQuoted() {
        // Arrange
        String input = "name \"\"";
        InMemoryStorage storage = new InMemoryStorage();

        // Act
        storage.set(input);

        // Assert
        assertEquals(Optional.of(""), storage.get("name"));
    }
}
