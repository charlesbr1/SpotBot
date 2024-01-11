package org.sbot.services.dao.sqlite.jdbi;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.Update;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.dao.TransactionalCtx;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public abstract class AbstractJDBI implements TransactionalCtx {

    private final JDBIRepository repository;

    protected AbstractJDBI(@NotNull JDBIRepository repository, @NotNull RowMapper<?> rowMapper) {
        this.repository = requireNonNull(repository);
        registerRowMapper(rowMapper);
        setupTable();
    }

    protected abstract void setupTable();


    protected void registerRowMapper(@NotNull RowMapper<?> rowMapper) {
        repository.registerRowMapper(rowMapper);
    }

    @Override
    public <T> T transactional(@NotNull Supplier<T> callback, @NotNull TransactionIsolationLevel isolationLevel) {
        return repository.transactional(callback, isolationLevel);
    }

    @NotNull
    protected Handle getHandle() {
        return repository.getHandle();
    }

    protected void update(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        repository.update(sql, parameters);
    }

    protected void update(@NotNull String sql, @NotNull Consumer<Update> mapper) {
        repository.update(sql, mapper);
    }

    @NotNull
    protected <T> List<T> query(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        return repository.query(sql, type, parameters);
    }

    public <T> void fetch(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters, @NotNull Consumer<Stream<T>> streamConsumer) {
        repository.fetch(sql, type, parameters, streamConsumer);
    }

    public <T> Optional<T> findOne(@NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        return repository.findOne(sql, type, parameters);
    }

    protected long queryOneLong(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        return repository.queryOneLong(sql, parameters);
    }

    public Optional<Long> findOneLong(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        return repository.findOneLong(sql, parameters);
    }

    protected Optional<ZonedDateTime> findOneDateTime(@NotNull String sql, @NotNull Map<String, ?> parameters) {
        return repository.findOneDateTime(sql, parameters);
    }
}
