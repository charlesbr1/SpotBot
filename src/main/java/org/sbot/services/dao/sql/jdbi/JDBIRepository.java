package org.sbot.services.dao.sql.jdbi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.jetbrains.annotations.NotNull;
import org.sbot.utils.Dates;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class JDBIRepository {

    private static final Logger LOGGER = LogManager.getLogger(JDBIRepository.class);

    private static final class LocaleArgumentFactory extends AbstractArgumentFactory<Locale> {
        private LocaleArgumentFactory() {
            super(Types.VARCHAR);
        }
        @Override
        protected Argument build(Locale value, ConfigRegistry config) {
            return (position, statement, ctx) -> statement.setString(position, value.toLanguageTag());
        }
    }

    @FunctionalInterface
    public interface BatchEntry {

        default void batchId(long id) {
            batch(Map.of("id", id));
        }

        void batch(Map<String, Object> ids);
    }

    final Jdbi jdbi;

    public JDBIRepository(@NotNull String url) {
        LOGGER.info("Loading database {}", url);
        jdbi = Jdbi.create(url);
        // register type java.util.Locale
        jdbi.registerArgument(new LocaleArgumentFactory());
        jdbi.registerColumnMapper(Locale.class, (rs, col, ctx) -> Locale.forLanguageTag(rs.getString(col)));
    }

    public void registerRowMapper(@NotNull RowMapper<?> rowMapper) {
        jdbi.registerRowMapper(requireNonNull(rowMapper));
    }

    <T> T inTransaction(@NotNull HandleCallback<T, RuntimeException> handleConsumer) {
        return jdbi.inTransaction(handleConsumer);
    }

    int update(@NotNull Handle handle, @NotNull String sql, @NotNull Map<String, ?> parameters) {
        return update(handle, sql, query -> query.bindMap(parameters));
    }

    int update(@NotNull Handle handle, @NotNull String sql, @NotNull Consumer<Update> mapper) {
        try (var query = handle.createUpdate(sql)) {
            mapper.accept(query);
            return query.execute();
        }
    }

    @NotNull
    <T> List<T> query(@NotNull Handle handle, @NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        try (var query = handle.createQuery(sql)) {
            return query.bindMap(parameters).mapTo(type).list();
        }
    }

    <T> T queryOne(@NotNull Handle handle, @NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        try (var query = handle.createQuery(sql)) {
            return query.bindMap(parameters).mapTo(type).one();
        }
    }

    long queryOneLong(@NotNull Handle handle, @NotNull String sql, @NotNull Map<String, ?> parameters) {
        return queryOne(handle, sql, Long.class, parameters);
    }

    @NotNull
    <K, V> Map<K, V> queryMap(@NotNull Handle handle, @NotNull String sql, @NotNull GenericType<Map<K, V>> type, @NotNull Consumer<Query> mapper, @NotNull String key, @NotNull String value) {
        try (var query = handle.createQuery(sql)) {
            mapper.accept(query);
            return query.setMapKeyColumn(key).setMapValueColumn(value).collectInto(type);
        }
    }

    @NotNull
    <A, R> R queryCollect(@NotNull Handle handle, @NotNull String sql, @NotNull Map<String, ?> parameters, @NotNull Collector<RowView, A, R> collector) {
        try (var query = handle.createQuery(sql)) {
            return query.bindMap(parameters).collectRows(collector);
        }
    }

    <T> long fetch(@NotNull Handle handle, @NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters, @NotNull Consumer<Stream<T>> streamConsumer) {
        try (var query = handle.createQuery(sql)) {
            long[] read = new long[] {0L};
            streamConsumer.accept(query.bindMap(parameters).mapTo(type)
                    .stream().filter(v -> ++read[0] != 0));
            return read[0];
        }
    }

    @NotNull
    <T> Optional<T> findOne(@NotNull Handle handle, @NotNull String sql, @NotNull Class<T> type, @NotNull Map<String, ?> parameters) {
        try (var query = handle.createQuery(sql)) {
            return query.bindMap(parameters).mapTo(type).findOne();
        }
    }

    Optional<Long> findOneLong(@NotNull Handle handle, @NotNull String sql, @NotNull Map<String, ?> parameters) {
        return findOne(handle, sql, Long.class, parameters);
    }

    Optional<ZonedDateTime> findOneDateTime(@NotNull Handle handle, @NotNull String sql, @NotNull Map<String, ?> parameters) {
        return findOne(handle, sql, Timestamp.class, parameters)
                .map(timestamp -> timestamp.toInstant().atZone(Dates.UTC));
    }

    void batchUpdates(@NotNull Handle handle, @NotNull Consumer<BatchEntry> updater, @NotNull String sql, @NotNull Map<String, Object> parameters) {
        PreparedBatch[] batch = new PreparedBatch[1];
        try {
            updater.accept(ids -> {
                var params = new HashMap<>(parameters);
                params.putAll(ids);
                (null != batch[0] ? batch[0] :
                        (batch[0] = handle.prepareBatch(sql)))
                        .bindMap(params).add();
            });
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::execute);
        } finally {
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::close);
        }
    }
}

