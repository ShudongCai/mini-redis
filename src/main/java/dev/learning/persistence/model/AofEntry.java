package dev.learning.persistence.model;

import dev.learning.storage.model.RedisBytes;

import java.util.List;

public record AofEntry (
        List<RedisBytes> arguments
) {
    public AofEntry {
        arguments = List.copyOf(arguments);
    }
}
