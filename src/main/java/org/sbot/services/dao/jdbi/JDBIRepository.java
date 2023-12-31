package org.sbot.services.dao.jdbi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.services.dao.TransactionalCtx;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
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

    protected void registerRowMapper(@NotNull RowMapper<?> rowMapper) {
        jdbi.registerRowMapper(rowMapper);
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

    @Nullable
    protected static ZonedDateTime parseDateTime(@Nullable Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(dateTime -> dateTime.toLocalDateTime().atZone(ZoneOffset.UTC)).orElse(null);
    }

    protected void update(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createUpdate(sql)) {
            query.bindMap(parameters).execute();
        }
    }

    @NotNull
    protected List<Alert> queryAlerts(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(Alert.class).list();
        }
    }

    protected long queryOneLong(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(Long.class).one();
        }
    }
}
