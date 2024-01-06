package org.sbot.services.jdbi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jetbrains.annotations.NotNull;

public class JDBIRepository {

    private static final Logger LOGGER = LogManager.getLogger(JDBIRepository.class);

    private final Jdbi jdbi;

    public JDBIRepository(@NotNull String url) {
        LOGGER.info("Opening data base {}", url);
        this.jdbi = Jdbi.create(url);
    }

    public <T> void registerRowMapper(@NotNull Class<T> type) {
        jdbi.registerRowMapper(type, ConstructorMapper.of(type));
    }

    public <X extends Exception> void transactional(final HandleConsumer<X> callback) throws X {
        jdbi.useHandle(handle -> handle.useTransaction(callback));
    }
}
