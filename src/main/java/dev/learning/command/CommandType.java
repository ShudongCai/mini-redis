package dev.learning.command;

public enum CommandType {
    PING("ping"),
    SET("set"),
    GET("get"),
    DEL("del"),
    EXISTS("exists"),
    TTL("ttl"),
    EXPIRE("expire");

    private final String value;

    CommandType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
