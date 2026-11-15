package dev.learning.command;

public enum CommandType {
    PING("ping"),
    SET("set"),
    GET("get"),
    DEL("del"),
    EXISTS("exists"),
    TTL("ttl"),
    EXPIRE("expire"),
    PERSIST("persist"),
    RPUSH("rpush"),
    LPUSH("lpush"),
    RPOP("rpop"),
    LPOP("lpop"),
    LRANGE("lrange"),
    LLEN("llen"),
    SADD("sadd"),
    SREM("srem"),
    SCARD("scard"),
    SMEMBERS("smembers"),
    SISMEMBER("sismember");


    private final String value;

    CommandType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
