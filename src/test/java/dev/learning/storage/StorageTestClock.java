package dev.learning.storage;

import java.util.concurrent.atomic.AtomicLong;

public final class StorageTestClock {
    private final AtomicLong currentTimeMillis = new AtomicLong();

    public InMemoryStorage newStorage() {
        return new InMemoryStorage(currentTimeMillis::get);
    }

    public void advanceMillis(long millis) {
        currentTimeMillis.addAndGet(millis);
    }
}
