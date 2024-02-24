package org.sbot.services.dao.sql.jdbi;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.dao.BatchEntry;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

// thread safe jdbi dao base class
public abstract class AbstractJDBI {

    private final JDBIRepository repository;
    private final JDBITransactionHandler transactionHandler;

    protected AbstractJDBI(@NotNull JDBIRepository repository, @NotNull RowMapper<?> rowMapper) {
        this.repository = requireNonNull(repository);
        repository.registerRowMapper(rowMapper);
        this.transactionHandler = null;
        inTransaction(handle -> { setupTable(handle); return null; });
    }

    protected AbstractJDBI(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
        this.repository = requireNonNull(abstractJDBI.repository);
        this.transactionHandler = requireNonNull(transactionHandler);
    }

    protected abstract void setupTable(@NotNull Handle handle);
    protected abstract AbstractJDBI withHandler(@NotNull JDBITransactionHandler transactionHandler);

    // should be used for initialisation only, when tx are available
    protected <T> T inTransaction(@NotNull HandleCallback<T, RuntimeException> handleConsumer) {
        if(null == transactionHandler) { // init setupTable or getMaxId call
            return repository.inTransaction(requireNonNull(handleConsumer));
        }
        throw new IllegalStateException("tx context already set");
    }

    protected Optional<Long> findOneLong(@NotNull Handle handle, @NotNull String sql, @NotNull Map<String, ?> parameters) {
        requireNonNull(handle); requireNonNull(sql); requireNonNull(parameters);
        return repository.findOneLong(handle, sql, parameters);
    }

    private <T> T sync(@NotNull Function<Handle, T> synchronizedAccess) {
        return transactionHandler.sync(repository.jdbi, synchronizedAccess);
    }

    protected int update(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        requireNonNull(sql); requireNonNull(parameters);
        return sync(handle -> repository.update(handle, sql, parameters));
    }

    protected int update(@NotNull String sql, @NotNull Consumer<Update> mapper) {
        requireNonNull(sql); requireNonNull(mapper);
        return sync(handle -> repository.update(handle, sql, mapper));
    }

    @NotNull
    protected <T> List<T> query(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        requireNonNull(sql); requireNonNull(type); requireNonNull(parameters);
        return sync(handle -> repository.query(handle, sql, type, parameters));
    }

    @NotNull
    protected <K, V> Map<K, V> queryMap(@NotNull String sql, @NotNull GenericType<Map<K, V>> type, @NotNull Consumer<Query> mapper, @NotNull String key, @NotNull String value) {
        requireNonNull(sql); requireNonNull(mapper); requireNonNull(type); requireNonNull(key); requireNonNull(value);
        return sync(handle -> repository.queryMap(handle, sql, type, mapper, key, value));
    }

    @NotNull
    protected <A, R> R queryCollect(@NotNull String sql, @NotNull Map<String, ?> parameters, @NotNull Collector<RowView, A, R> collector) {
        requireNonNull(sql); requireNonNull(parameters); requireNonNull(collector);
        return sync(handle -> repository.queryCollect(handle, sql, parameters, collector));
    }

    protected <T> T queryOne(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        requireNonNull(sql); requireNonNull(type); requireNonNull(parameters);
        return sync(handle -> repository.queryOne(handle, sql, type, parameters));
    }

    protected long queryOneLong(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        requireNonNull(sql); requireNonNull(parameters);
        return sync(handle -> repository.queryOneLong(handle, sql, parameters));
    }

    protected <T> long fetch(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters, @NotNull Consumer<Stream<T>> streamConsumer) {
        requireNonNull(sql); requireNonNull(type); requireNonNull(parameters); requireNonNull(streamConsumer);
        return sync(handle -> repository.fetch(handle, sql, type, parameters, streamConsumer));
    }

    protected <T> Optional<T> findOne(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        requireNonNull(sql); requireNonNull(type); requireNonNull(parameters);
        return sync(handle -> repository.findOne(handle, sql, type, parameters));
    }

    protected Optional<ZonedDateTime> findOneDateTime(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        requireNonNull(sql); requireNonNull(parameters);
        return sync(handle -> repository.findOneDateTime(handle, sql, parameters));
    }

    protected void batchUpdates(@NotNull Consumer<BatchEntry> updater, @NotNull String sql, @NotNull Map<String, Object> parameters) {
        requireNonNull(updater); requireNonNull(sql); requireNonNull(parameters);
        sync(handle -> { repository.batchUpdates(handle, updater, sql, parameters); return null; });
    }
}
