package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.entities.User;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;
import org.sbot.utils.Dates;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static java.util.Objects.requireNonNull;

public interface UsersDao {

    @NotNull
    default Locale setupUser(long userId, @NotNull Locale locale, @NotNull Clock clock) {
        requireNonNull(locale);
        return accessUser(userId, clock).map(User::locale).orElseGet(() -> {
            var user = new User(userId, locale, Dates.nowUtc(clock));
            setUser(user);
            return locale;
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

    Map<Long, Locale> getLocales(@NotNull LongStream userIds);

    void setUser(@NotNull User user);

    void updateLocale(long userId, @NotNull Locale locale);

    void updateLastAccess(long userId, @NotNull ZonedDateTime lastAccess);

    void userBatchDeletes(@NotNull Consumer<BatchEntry> deleter);
}
