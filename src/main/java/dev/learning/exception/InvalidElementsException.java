package dev.learning.exception;

public class InvalidElementsException extends RuntimeException {
    public InvalidElementsException() {
        super("ERR invalid value argument");
    }
}
