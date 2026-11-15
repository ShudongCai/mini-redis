package dev.learning.exception;

public class InvalidKeyException extends RuntimeException{
        public InvalidKeyException() {
            super("ERR invalid key argument");
        }
}
