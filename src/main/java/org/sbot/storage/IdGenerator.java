package org.sbot.storage;

import java.util.concurrent.atomic.AtomicLong;

public interface IdGenerator {

    static long newId() {
        return MemoryIdGenerator.newId();
    }
}
