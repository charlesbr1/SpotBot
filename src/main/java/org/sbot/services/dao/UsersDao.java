package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.entities.User;
import org.sbot.utils.Dates;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static java.util.Objects.requireNonNull;

public interface UsersDao {

    @NotNull
    default User setupUser(long userId, @NotNull Locale locale, @NotNull Clock clock) {
        requireNonNull(locale);
        return accessUser(userId, clock).orElseGet(() -> {
            var user = new User(userId, locale, null, Dates.nowUtc(clock));
            setUser(user);
            return user;
        });
    }

    default Optional<User> accessUser(long userId, @NotNull Clock clock) {
        requireNonNull(clock);
        var user = getUser(userId);
        if(user.isPresent()) {
            updateLastAccess(userId, Dates.nowUtc(clock));
        }
        return user;
    }

    Optional<User> getUser(long userId);

    boolean userExists(long userId);

    Map<Long, Locale> getLocales(@NotNull LongStream userIds);

    void setUser(@NotNull User user);

    void updateLocale(long userId, @NotNull Locale locale);

    void updateTimezone(long userId, @NotNull ZoneId timezone);

    void updateLastAccess(long userId, @NotNull ZonedDateTime lastAccess);

    long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ZonedDateTime expirationDate);
}
