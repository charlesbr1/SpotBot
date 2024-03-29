package org.sbot.services.dao;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.User;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;
import static org.sbot.utils.Dates.UTC;
import static org.sbot.utils.DatesTest.nowUtc;

public abstract class UsersDaoTest {

    @ParameterizedTest
    @MethodSource("provideDao")
    void setupUser(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.setupUser(1L, null, Clock.systemUTC()));
        assertThrows(NullPointerException.class, () -> users.setupUser(1L, Locale.UK, null));

        long userId = 321L;
        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(userId, Locale.FRANCE, null, now);
        assertEquals(user, users.setupUser(userId, Locale.FRANCE, Clock.fixed(now.toInstant(), UTC)));
        assertEquals(user, users.setupUser(userId, Locale.JAPANESE, Clock.fixed(now.toInstant(), UTC)));

        users.deleteHavingLastAccessBeforeAndNotInAlerts(now.plusMinutes(1L));
        assertTrue(users.getUser(userId).isEmpty());

        user = new User(userId, Locale.JAPANESE, null, now);
        assertEquals(user, users.setupUser(userId, Locale.JAPANESE, Clock.fixed(now.toInstant(), UTC)));
        assertEquals(user, users.setupUser(userId, Locale.JAPANESE, Clock.fixed(now.toInstant(), ZoneId.of("Europe/Paris"))));
        assertEquals(now, users.getUser(userId).get().lastAccess());

        // last access update visible on next call
        assertNotEquals(now.plusHours(33L), users.setupUser(userId, Locale.CANADA, Clock.fixed(now.plusHours(33L).toInstant(), UTC)).lastAccess());
        assertEquals(now.plusHours(33L), users.setupUser(userId, Locale.CANADA, Clock.fixed(now.plusHours(33L).toInstant(), UTC)).lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void accessUser(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.accessUser(1L, null));

        assertTrue(users.accessUser(1L, Clock.systemUTC()).isEmpty());

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, null, now);
        users.addUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(now, users.getUser(user.id()).get().lastAccess());

        assertTrue(users.accessUser(1L, Clock.fixed(now.plusMinutes(33L).toInstant(), UTC)).isPresent());
        assertEquals(now.plusMinutes(33L), users.getUser(user.id()).get().lastAccess());
        assertTrue(users.accessUser(user.id(), Clock.fixed(now.plusHours(3L).toInstant(), UTC)).isPresent());
        assertEquals(now.plusHours(3L), users.getUser(user.id()).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getUser(UsersDao users) {
        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        long userId = 123L;
        assertTrue(users.getUser(userId).isEmpty());
        User user = new User(userId, Locale.JAPAN, null, now);
        users.addUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(user, users.getUser(userId).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void userExists(UsersDao users) {
        long userId = 123L;
        assertFalse(users.userExists(userId));
        User user = new User(userId, Locale.JAPAN, null, nowUtc());
        users.addUser(user);
        assertTrue(users.userExists(userId));
        users.deleteHavingLastAccessBeforeAndNotInAlerts(nowUtc().plusMinutes(1L));
        assertFalse(users.userExists(userId));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLocales(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.getLocales(null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        var user1 = new User(1L, Locale.US, null, now);
        var user2 = new User(2L, Locale.JAPAN, null, now);
        var user3 = new User(3L, Locale.FRENCH, null, now);
        var user4 = new User(4L, Locale.CANADA, null, now);
        users.addUser(user1);
        users.addUser(user2);
        users.addUser(user3);
        users.addUser(user4);

        var locales = users.getLocales(List.of());
        assertEquals(0, locales.size());

        locales = users.getLocales(List.of(user1.id()));
        assertEquals(1, locales.size());
        assertEquals(user1.locale(), locales.get(user1.id()));

        locales = users.getLocales(List.of(user2.id()));
        assertEquals(1, locales.size());
        assertEquals(user2.locale(), locales.get(user2.id()));

        locales = users.getLocales(List.of(user3.id()));
        assertEquals(1, locales.size());
        assertEquals(user3.locale(), locales.get(user3.id()));

        locales = users.getLocales(List.of(user1.id(), user3.id()));
        assertEquals(2, locales.size());
        assertEquals(user1.locale(), locales.get(user1.id()));
        assertEquals(user3.locale(), locales.get(user3.id()));

        locales = users.getLocales(List.of(user1.id(), user3.id(), user4.id()));
        assertEquals(3, locales.size());
        assertEquals(user1.locale(), locales.get(user1.id()));
        assertEquals(user3.locale(), locales.get(user3.id()));
        assertEquals(user4.locale(), locales.get(user4.id()));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void addUser(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.addUser(null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, UTC, now);
        assertTrue(users.getUser(user.id()).isEmpty());
        users.addUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(user, users.getUser(user.id()).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLocale(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.updateLocale(1L, null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, null, now);
        users.addUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(Locale.JAPAN, users.getUser(user.id()).get().locale());
        users.updateLocale(user.id(), Locale.UK);
        assertEquals(Locale.UK, users.getUser(user.id()).get().locale());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateTimezone(UsersDao users) {
        User user = new User(1L, Locale.JAPAN, null, nowUtc());
        users.addUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertNull(users.getUser(user.id()).get().timeZone());
        users.updateTimezone(user.id(), UTC);
        assertEquals(UTC, users.getUser(user.id()).get().timeZone());
        users.updateTimezone(user.id(), ZoneId.of("Europe/Paris"));
        assertEquals(ZoneId.of("Europe/Paris"), users.getUser(user.id()).get().timeZone());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLastAccess(UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.updateLastAccess(1L, null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        User user = new User(1L, Locale.JAPAN, null, now);
        users.addUser(user);
        assertTrue(users.getUser(user.id()).isPresent());
        assertEquals(now, users.getUser(user.id()).get().lastAccess());
        users.updateLastAccess(user.id(), now.plusMinutes(73L));
        assertEquals(now.plusMinutes(73L), users.getUser(user.id()).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideBothDao")
    void deleteHavingLastAccessBeforeAndNotInAlerts(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> users.deleteHavingLastAccessBeforeAndNotInAlerts(null));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        var user1 = new User(1L, Locale.US, null, now);
        var user2 = new User(2L, Locale.JAPAN, null, now.minusHours(1L));
        var user3 = new User(3L, Locale.FRENCH, null, now.minusDays(1L));
        var user4 = new User(4L, Locale.CANADA, null, now.minusWeeks(1L));
        var user5 = new User(5L, Locale.GERMAN, null, now.minusDays(1L));
        users.addUser(user1);
        users.addUser(user2);
        users.addUser(user3);
        users.addUser(user4);
        users.addUser(user5);
        alerts.addAlert(createTestAlertWithUserId(user5.id()));

        assertTrue(users.getUser(user1.id()).isPresent());
        assertTrue(users.getUser(user2.id()).isPresent());
        assertTrue(users.getUser(user3.id()).isPresent());
        assertTrue(users.getUser(user4.id()).isPresent());
        assertTrue(users.getUser(user5.id()).isPresent());

        users.deleteHavingLastAccessBeforeAndNotInAlerts(now.minusMonths(1L));
        assertTrue(users.getUser(user1.id()).isPresent());
        assertTrue(users.getUser(user2.id()).isPresent());
        assertTrue(users.getUser(user3.id()).isPresent());
        assertTrue(users.getUser(user4.id()).isPresent());
        assertTrue(users.getUser(user5.id()).isPresent());

        now = now.plusMinutes(1L);
        users.deleteHavingLastAccessBeforeAndNotInAlerts(now.minusWeeks(1L));
        assertTrue(users.getUser(user1.id()).isPresent());
        assertTrue(users.getUser(user2.id()).isPresent());
        assertTrue(users.getUser(user3.id()).isPresent());
        assertTrue(users.getUser(user4.id()).isEmpty());
        assertTrue(users.getUser(user5.id()).isPresent());

        users.deleteHavingLastAccessBeforeAndNotInAlerts(now.minusDays(1L));
        assertTrue(users.getUser(user1.id()).isPresent());
        assertTrue(users.getUser(user2.id()).isPresent());
        assertTrue(users.getUser(user3.id()).isEmpty());
        assertTrue(users.getUser(user4.id()).isEmpty());
        assertTrue(users.getUser(user5.id()).isPresent());

        users.deleteHavingLastAccessBeforeAndNotInAlerts(now.minusHours(1L));
        assertTrue(users.getUser(user1.id()).isPresent());
        assertTrue(users.getUser(user2.id()).isEmpty());
        assertTrue(users.getUser(user3.id()).isEmpty());
        assertTrue(users.getUser(user4.id()).isEmpty());
        assertTrue(users.getUser(user5.id()).isPresent());

        users.deleteHavingLastAccessBeforeAndNotInAlerts(now);
        assertTrue(users.getUser(user1.id()).isEmpty());
        assertTrue(users.getUser(user2.id()).isEmpty());
        assertTrue(users.getUser(user3.id()).isEmpty());
        assertTrue(users.getUser(user4.id()).isEmpty());
        assertTrue(users.getUser(user5.id()).isPresent());
    }
}