package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.User;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.UsersDao;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.sbot.entities.alerts.ClientType.DISCORD;

public class UsersMemory implements UsersDao {

    private static final Logger LOGGER = LogManager.getLogger(UsersMemory.class);

    private final Map<Long, User> users = new ConcurrentHashMap<>();

    private final AlertsMemory alerts;

    public UsersMemory(@NotNull AlertsMemory alerts) {
        LOGGER.debug("Loading memory storage for users");
        this.alerts = requireNonNull(alerts);
    }

    @Override
    public Optional<User> getUser(long userId) {
        LOGGER.debug("getUser {}", userId);
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean userExists(long userId) {
        LOGGER.debug("userExists {}", userId);
        return users.containsKey(userId);
    }

    @Override
    @NotNull
    public Map<Long, Locale> getLocales(@NotNull List<Long> userIds) {
        var userIdSet = new HashSet<>(userIds);
        LOGGER.debug("getLocales {}", userIdSet);
        return userIdSet.isEmpty() ? emptyMap() :
                users.values().stream().filter(user -> userIdSet.contains(user.id()))
                        .collect(toMap(User::id, User::locale));
    }

    @Override
    public void addUser(@NotNull User user) {
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
    public long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingLastAccessBeforeAndNotInAlerts {}", expirationDate);
        requireNonNull(expirationDate);
        var toDelete = users.values().stream().filter(u -> u.lastAccess().isBefore(expirationDate) &&
                //TODO select clientType from user (settings) attribute discord_user_id if present
                alerts.getAlertsStream(SelectionFilter.ofUser(DISCORD, u.id(), null)).findFirst().isEmpty()).toList();
        users.values().removeAll(toDelete);
        return toDelete.size();
    }
}
