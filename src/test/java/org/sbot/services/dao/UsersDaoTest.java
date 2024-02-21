package org.sbot.services.dao;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.User;
import org.sbot.utils.Dates;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.DatesTest.nowUtc;

public abstract class UsersDaoTest {

    @ParameterizedTest
    @MethodSource("provideDao")
    void setupUser(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.setupUser(1L, null, Clock.systemUTC()));
        assertThrows(NullPointerException.class, () -> users.setupUser(1L, Locale.UK, null));

        long userId = 321L;
        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        assertEquals(Locale.FRANCE, users.setupUser(userId, Locale.FRANCE, Clock.fixed(now.toInstant(), Dates.UTC)));
        assertEquals(Locale.FRANCE, users.setupUser(userId, Locale.JAPANESE, Clock.fixed(now.toInstant(), Dates.UTC)));

        users.userBatchDeletes(b -> b.batchId(userId));
        assertTrue(users.getUser(userId).isEmpty());

        assertEquals(Locale.JAPANESE, users.setupUser(userId, Locale.JAPANESE, Clock.fixed(now.toInstant(), Dates.UTC)));
        assertEquals(Locale.JAPANESE, users.setupUser(userId, Locale.UK, Clock.fixed(now.toInstant(), Dates.UTC)));
        assertEquals(now, users.getUser(userId).get().lastAccess());

        assertEquals(Locale.JAPANESE, users.setupUser(userId, Locale.CANADA, Clock.fixed(now.plusHours(33L).toInstant(), Dates.UTC)));
        assertEquals(now.plusHours(33L), users.getUser(userId).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void accessUser(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.accessUser(1L, null));

        assertTrue(users.accessUser(1L, Clock.systemUTC()).isEmpty());

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, now);
        users.setUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(now, users.getUser(user.id()).get().lastAccess());

        assertTrue(users.accessUser(1L, Clock.fixed(now.plusMinutes(33L).toInstant(), Dates.UTC)).isPresent());
        assertEquals(now.plusMinutes(33L), users.getUser(user.id()).get().lastAccess());
        assertTrue(users.accessUser(user.id(), Clock.fixed(now.plusHours(3L).toInstant(), Dates.UTC)).isPresent());
        assertEquals(now.plusHours(3L), users.getUser(user.id()).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getUser(UsersDao users) {
        long userId = 123L;
        assertTrue(users.getUser(userId).isEmpty());
        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(userId, Locale.JAPAN, now);
        users.setUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(user, users.getUser(userId).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLocales(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.getLocales(null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        var user1 = new User(1L, Locale.US, now);
        var user2 = new User(2L, Locale.JAPAN, now);
        var user3 = new User(3L, Locale.FRENCH, now);
        var user4 = new User(4L, Locale.CANADA, now);
        users.setUser(user1);
        users.setUser(user2);
        users.setUser(user3);
        users.setUser(user4);

        var locales = users.getLocales(LongStream.of());
        assertEquals(0, locales.size());

        locales = users.getLocales(LongStream.of(user1.id()));
        assertEquals(1, locales.size());
        assertEquals(user1.locale(), locales.get(user1.id()));

        locales = users.getLocales(LongStream.of(user2.id()));
        assertEquals(1, locales.size());
        assertEquals(user2.locale(), locales.get(user2.id()));

        locales = users.getLocales(LongStream.of(user3.id()));
        assertEquals(1, locales.size());
        assertEquals(user3.locale(), locales.get(user3.id()));

        locales = users.getLocales(LongStream.of(user1.id(), user3.id()));
        assertEquals(2, locales.size());
        assertEquals(user1.locale(), locales.get(user1.id()));
        assertEquals(user3.locale(), locales.get(user3.id()));

        locales = users.getLocales(LongStream.of(user1.id(), user3.id(), user4.id()));
        assertEquals(3, locales.size());
        assertEquals(user1.locale(), locales.get(user1.id()));
        assertEquals(user3.locale(), locales.get(user3.id()));
        assertEquals(user4.locale(), locales.get(user4.id()));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void setUser(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.setUser(null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, now);
        assertTrue(users.getUser(user.id()).isEmpty());
        users.setUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(user, users.getUser(user.id()).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLocale(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.updateLocale(1L, null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, now);
        users.setUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(Locale.JAPAN, users.getUser(user.id()).get().locale());
        users.updateLocale(user.id(), Locale.UK);
        assertEquals(Locale.UK, users.getUser(user.id()).get().locale());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLastAccess(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.updateLastAccess(1L, null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, now);
        users.setUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(now, users.getUser(user.id()).get().lastAccess());
        users.updateLastAccess(user.id(), now.plusMinutes(73L));
        assertEquals(now.plusMinutes(73L), users.getUser(user.id()).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void userBatchDeletes(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.userBatchDeletes(null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        var user1 = new User(1L, Locale.US, now);
        var user2 = new User(2L, Locale.JAPAN, now);
        var user3 = new User(3L, Locale.FRENCH, now);
        var user4 = new User(4L, Locale.CANADA, now);
        users.setUser(user1);
        users.setUser(user2);
        users.setUser(user3);
        users.setUser(user4);

        assertTrue(users.getUser(user1.id()).isPresent());
        assertTrue(users.getUser(user2.id()).isPresent());
        assertTrue(users.getUser(user3.id()).isPresent());
        assertTrue(users.getUser(user4.id()).isPresent());

        users.userBatchDeletes(deleter -> deleter.batchId(user2.id()));
        assertTrue(users.getUser(user2.id()).isEmpty());
        assertTrue(users.getUser(user1.id()).isPresent());
        assertFalse(users.getUser(user2.id()).isPresent());
        assertTrue(users.getUser(user3.id()).isPresent());
        assertTrue(users.getUser(user4.id()).isPresent());

        users.userBatchDeletes(deleter -> {
            deleter.batchId(user1.id());
            deleter.batchId(user3.id());
            deleter.batchId(user4.id());
        });
        assertTrue(users.getUser(user1.id()).isEmpty());
        assertTrue(users.getUser(user2.id()).isEmpty());
        assertTrue(users.getUser(user3.id()).isEmpty());
        assertTrue(users.getUser(user4.id()).isEmpty());
    }
}