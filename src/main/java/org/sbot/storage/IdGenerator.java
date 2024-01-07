package org.sbot.storage;

import java.util.concurrent.atomic.AtomicLong;

public record IdGenerator() {

    private static final AtomicLong counter = new AtomicLong(1);

    public static long newId() {
        return counter.getAndIncrement();
    }
}

