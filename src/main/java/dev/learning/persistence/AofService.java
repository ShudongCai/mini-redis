package dev.learning.persistence;

import dev.learning.persistence.model.AofEntry;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class AofService {
    private static final int AOF_QUEUE_CAPACITY = 8192;

    private final BlockingQueue<AofEntry> blockingQueue;

    public AofService() {
        this.blockingQueue = new ArrayBlockingQueue<>(AOF_QUEUE_CAPACITY);
    }

    public void append()
}
