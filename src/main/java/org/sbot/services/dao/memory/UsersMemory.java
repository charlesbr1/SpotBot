package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.User;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public class UsersMemory implements UsersDao {

    private static final Logger LOGGER = LogManager.getLogger(UsersMemory.class);

    private final Map<Long, User> users = new ConcurrentHashMap<>();

    @Override
    public Optional<User> getUser(long userId) {
        LOGGER.debug("getUser {}", userId);
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    @NotNull
    public Map<Long, Locale> getLocales(@NotNull LongStream userIds) {
        var userIdSet = userIds.boxed().collect(toSet());
        LOGGER.debug("getLocales {}", userIdSet);
        return userIdSet.isEmpty() ? emptyMap() :
                users.values().stream().filter(user -> userIdSet.contains(user.id()))
                        .collect(toMap(User::id, User::locale));
    }

    @Override
    public void setUser(@NotNull User user) {
        LOGGER.debug("setUser {}", user);
        users.put(user.id(), user);
    }

    @Override
    public void updateLocale(long userId, @NotNull Locale locale) {
        LOGGER.debug("updateLocale {} {}", userId, locale);
        requireNonNull(locale);
        users.computeIfPresent(userId, (id, u) -> u.withLocale(locale));
    }

    @Override
    public void updateTimezone(long userId, @NotNull ZoneId timezone) {
        LOGGER.debug("updateTimezone {} {}", userId, timezone);
        requireNonNull(timezone);
        users.computeIfPresent(userId, (id, u) -> u.withTimezone(timezone));
    }

    @Override
    public void updateLastAccess(long userId, @NotNull ZonedDateTime lastAccess) {
        LOGGER.debug("updateLastAccess {} {}", userId, lastAccess);
        requireNonNull(lastAccess);
        users.computeIfPresent(userId, (id, u) -> u.withLastAccess(lastAccess));
    }

    @Override
    public void userBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("userBatchDeletes");
        deleter.accept(ids -> users.remove((Long) ids.get("id")));
    }
}
