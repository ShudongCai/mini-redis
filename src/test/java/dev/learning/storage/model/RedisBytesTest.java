package dev.learning.storage.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RedisBytesTest {
    @Test
    void shouldBeEqualWhenByteContentsAreEqual() {
        //Arrange
        byte[] inputA = {10, 35, 77, 24, 82, 102};
        byte[] inputB = {10, 35, 77, 24, 82, 102};

        //Act
        RedisBytes a = new RedisBytes(inputA);
        RedisBytes b = new RedisBytes(inputB);

        //Assert
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    void shouldHaveSameHashCodeWhenByteContentsAreEqual() {
        //Arrange
        byte[] inputA = {10, 84, 77, 98, 66, 109};
        byte[] inputB = {10, 84, 77, 98, 66, 109};

        //Act
        RedisBytes a = new RedisBytes(inputA);
        RedisBytes b = new RedisBytes(inputB);

        //Assert
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void shouldNotChangeWhenInputArrayIsModified() {
        // Arrange
        byte[] input = {10, 84, 77, 24, 82, 102};
        byte[] expected = input.clone();

        RedisBytes redisBytes = new RedisBytes(input);

        // Act
        input[0] = 77;
        input[4] = 45;

        // Assert
        assertArrayEquals(expected, redisBytes.getBytes());
    }

    @Test
    void shouldNotChangeWhenReturnedArrayIsModified() {
        // Arrange
        byte[] expected = {10, 84, 77, 24, 82, 102};
        RedisBytes redisBytes = new RedisBytes(expected);

        // Act
        byte[] returned = redisBytes.getBytes();
        returned[0] = 99;

        // Assert
        assertArrayEquals(expected, redisBytes.getBytes());
    }
}
