package org.sbot.storage;

import java.util.concurrent.atomic.AtomicLong;

public enum MemoryIdGenerator {
    ;

    private static final AtomicLong counter = new AtomicLong(0);

    public static long newId() {
        return counter.getAndIncrement();
    }
}
