package org.sbot.services.dao.jdbi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.Update;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.dao.TransactionalCtx;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class JDBIRepository implements TransactionalCtx {

    private static final Logger LOGGER = LogManager.getLogger(JDBIRepository.class);

    // avoiding use of ThreadLocal for virtual threads
    private static final Map<Long, Handle> transactionalContexts = new ConcurrentHashMap<>();

    private final Jdbi jdbi;

    public JDBIRepository(@NotNull String url) {
        LOGGER.info("Opening database {}", url);
        jdbi = Jdbi.create(url);
    }

    public void registerRowMapper(@NotNull RowMapper<?> rowMapper) {
        jdbi.registerRowMapper(rowMapper);
    }

    public Handle getHandle() {
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


    public void update(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createUpdate(sql)) {
            query.bindMap(parameters).execute();
        }
    }

    public void update(@NotNull String sql, @NotNull Consumer<Update> mapper) {
        try (var query = getHandle().createUpdate(sql)) {
            mapper.accept(query);
            query.execute();
        }
    }

    @NotNull
    public <T> List<T> query(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(type).list();
        }
    }

    public <T> void fetch(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters, @NotNull Consumer<Stream<T>> streamConsumer) {
        try (var query = getHandle().createQuery(sql)) {
            streamConsumer.accept(query.bindMap(parameters).mapTo(type).stream());
        }
    }

    @NotNull
    public <T> Optional<T> findOne(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(type).findOne();
        }
    }

    public long queryOneLong(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(Long.class).one();
        }
    }

    public Optional<Long> findOneLong(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(Long.class).findOne();
        }
    }

    public Optional<ZonedDateTime> findOneDateTime(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        try (var query = getHandle().createQuery(sql)) {
            return query.bindMap(parameters).mapTo(ZonedDateTime.class).findOne();
        }
    }
}
