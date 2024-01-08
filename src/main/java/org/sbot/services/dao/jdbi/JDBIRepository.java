package org.sbot.services.dao.jdbi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.dao.TransactionalCtx;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public abstract class JDBIRepository implements TransactionalCtx {

    private static final Logger LOGGER = LogManager.getLogger(JDBIRepository.class);

    // avoiding use of ThreadLocal for virtual threads
    private static final Map<Long, Handle> transactionalContexts = new ConcurrentHashMap<>();

    private final Jdbi jdbi;

    protected JDBIRepository(@NotNull String url) {
        LOGGER.info("Opening database {}", url);
        this.jdbi = Jdbi.create(url);
    }

    protected  <T> void registerRowMapper(@NotNull Class<T> type) {
        jdbi.registerRowMapper(type, ConstructorMapper.of(type));
    }

    protected Handle getHandle() {
        return Optional.ofNullable(transactionalContexts.get(Thread.currentThread().threadId()))
                .orElseThrow(() -> new IllegalCallerException("Internal error : Missing transactional context. Use transactional(Runnable callback) and run your code inside the callback"));
    }

    @Override
    public <T> T transactional(@NotNull Supplier<T> callback, @NotNull TransactionIsolationLevel isolationLevel) {
        var threadId = Thread.currentThread().threadId();
        LOGGER.debug("New transactional context, thread id {}, isolation level {}", threadId, isolationLevel);
        if(transactionalContexts.containsKey(threadId)) {
            return callback.get();
        } else {
            requireNonNull(callback);
            try {
                return jdbi.inTransaction(isolationLevel, handle -> {
                    transactionalContexts.put(threadId, handle);
                    return callback.get();
                });
            } finally {
                transactionalContexts.remove(threadId);
            }
        }
    }
}
