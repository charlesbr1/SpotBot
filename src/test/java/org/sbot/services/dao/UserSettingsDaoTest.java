package org.sbot.services.dao;

import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.UserSettings;
import org.sbot.services.dao.UserSettingsDao.ClientTypeUserId;
import org.sbot.services.dao.sql.UserSettingsSQLite;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.settings.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.settings.UserSettings.DEFAULT_TIMEZONE;
import static org.sbot.utils.DatesTest.nowUtc;

public abstract class UserSettingsDaoTest {

    @Test
    void ofClientTypeUserId() {
        assertThrows(NullPointerException.class, () -> ClientTypeUserId.of(null, 1L));
        assertEquals(DISCORD, ClientTypeUserId.of(DISCORD, 1L).clientType());
        assertEquals(123L, ClientTypeUserId.of(DISCORD, 123L).userId());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getUserSettings(UserSettingsDao userSettings) {
        assertThrows(NullPointerException.class, () -> userSettings.getUserSettings(null, 123L));

        long userId = 123L;
        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isEmpty());
        UserSettings settings = new UserSettings(userId, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        userSettings.addSettings(settings);
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isPresent());
        assertEquals(settings, userSettings.getUserSettings(DISCORD, userId).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void userExists(UserSettingsDao userSettings) {
        assertThrows(NullPointerException.class, () -> userSettings.userExists(null, 123L));

        long userId = 123L;
        var now = DatesTest.nowUtc();
        assertFalse(userSettings.userExists(DISCORD, userId));
        UserSettings settings = new UserSettings(userId, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        userSettings.addSettings(settings);
        assertTrue(userSettings.userExists(DISCORD, userId));
        userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.plusMinutes(1L));
        assertFalse(userSettings.userExists(DISCORD, userId));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLocales(UserSettingsDao userSettings) {
        assertThrows(NullPointerException.class, () -> userSettings.getLocales(null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        var user1 = new UserSettings(1L, Locale.US, DEFAULT_TIMEZONE, now);
        var user2 = new UserSettings(2L, Locale.JAPAN, DEFAULT_TIMEZONE, now);
        var user3 = new UserSettings(3L, Locale.FRENCH, DEFAULT_TIMEZONE, now);
        var user4 = new UserSettings(4L, Locale.CANADA, DEFAULT_TIMEZONE, now);
        userSettings.addSettings(user1);
        userSettings.addSettings(user2);
        userSettings.addSettings(user3);
        userSettings.addSettings(user4);
        var ct1 = ClientTypeUserId.of(DISCORD, user1.discordUserId());
        var ct2 = ClientTypeUserId.of(DISCORD, user2.discordUserId());
        var ct3 = ClientTypeUserId.of(DISCORD, user3.discordUserId());
        var ct4 = ClientTypeUserId.of(DISCORD, user4.discordUserId());

        var locales = userSettings.getLocales(List.of());
        assertEquals(0, locales.size());

        locales = userSettings.getLocales(List.of(ct1));
        assertEquals(1, locales.size());
        assertEquals(user1.locale(), locales.get(ct1));

        locales = userSettings.getLocales(List.of(ct2));
        assertEquals(1, locales.size());
        assertEquals(user2.locale(), locales.get(ct2));

        locales = userSettings.getLocales(List.of(ct3));
        assertEquals(1, locales.size());
        assertEquals(user3.locale(), locales.get(ct3));

        locales = userSettings.getLocales(List.of(ct1, ct3));
        assertEquals(2, locales.size());
        assertEquals(user1.locale(), locales.get(ct1));
        assertEquals(user3.locale(), locales.get(ct3));

        locales = userSettings.getLocales(List.of(ct1, ct3, ct4));
        assertEquals(3, locales.size());
        assertEquals(user1.locale(), locales.get(ct1));
        assertEquals(user3.locale(), locales.get(ct3));
        assertEquals(user4.locale(), locales.get(ct4));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void addSettings(UserSettingsDao userSettings) {
        assertThrows(NullPointerException.class, () -> userSettings.addSettings(null));

        long userId = 123L;
        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        UserSettings settings = new UserSettings(userId, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isEmpty());
        userSettings.addSettings(settings);
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isPresent());
        assertEquals(settings, userSettings.getUserSettings(DISCORD, userId).get());

        var exClass = userSettings instanceof UserSettingsSQLite ?
                UnableToExecuteStatementException.class : IllegalArgumentException.class;
        assertThrows(exClass, () -> userSettings.addSettings(settings));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateUserLocale(UserSettingsDao userSettings) {
        long userId = 567L;
        assertThrows(NullPointerException.class, () -> userSettings.updateUserLocale(null, 1L, Locale.ENGLISH));
        assertThrows(NullPointerException.class, () -> userSettings.updateUserLocale(DISCORD, 1L, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        UserSettings settings = new UserSettings(userId, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        userSettings.addSettings(settings);
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isPresent());
        assertEquals(DEFAULT_LOCALE, userSettings.getUserSettings(DISCORD, userId).get().locale());

        userSettings.updateUserLocale(DISCORD, userId, Locale.JAPAN);
        assertEquals(Locale.JAPAN, userSettings.getUserSettings(DISCORD, userId).get().locale());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateUserTimezone(UserSettingsDao userSettings) {
        long userId = 567L;
        assertThrows(NullPointerException.class, () -> userSettings.updateUserTimezone(null, 1L, Dates.UTC));
        assertThrows(NullPointerException.class, () -> userSettings.updateUserTimezone(DISCORD, 1L, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        UserSettings settings = new UserSettings(userId, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        userSettings.addSettings(settings);
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isPresent());
        assertEquals(Dates.UTC, userSettings.getUserSettings(DISCORD, userId).get().timezone());

        userSettings.updateUserTimezone(DISCORD, userId, ZoneId.of("Europe/Paris"));
        assertEquals(ZoneId.of("Europe/Paris"), userSettings.getUserSettings(DISCORD, userId).get().timezone());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLastAccess(UserSettingsDao userSettings) {
        long userId = 567L;
        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        assertThrows(NullPointerException.class, () -> userSettings.updateLastAccess(null, 1L, now));
        assertThrows(NullPointerException.class, () -> userSettings.updateLastAccess(DISCORD, 1L, null));

        UserSettings settings = new UserSettings(userId, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        userSettings.addSettings(settings);
        assertTrue(userSettings.getUserSettings(DISCORD, userId).isPresent());
        assertEquals(now, userSettings.getUserSettings(DISCORD, userId).get().lastAccess());

        userSettings.updateLastAccess(DISCORD, userId, now.plusMinutes(73L));
        assertEquals(now.plusMinutes(73L), userSettings.getUserSettings(DISCORD, userId).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideAlertWithSettingsDao")
    void deleteHavingLastAccessBeforeAndNotInAlerts(AlertsDao alerts, UserSettingsDao userSettings, ServerSettingsDao serverSettings) {
        assertThrows(NullPointerException.class, () -> userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(null, DatesTest.nowUtc()));
        assertThrows(NullPointerException.class, () -> userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        var user1 = new UserSettings(1L, Locale.UK, Dates.UTC, now);
        var user2 = new UserSettings(2L, Locale.UK, Dates.UTC, now.minusHours(1L));
        var user3 = new UserSettings(3L, Locale.UK, Dates.UTC, now.minusDays(1L));
        var user4 = new UserSettings(4L, Locale.UK, Dates.UTC, now.minusWeeks(1L));
        var user5 = new UserSettings(5L, Locale.UK, Dates.UTC, now.minusDays(1L));
        userSettings.addSettings(user1);
        userSettings.addSettings(user2);
        userSettings.addSettings(user3);
        userSettings.addSettings(user4);
        userSettings.addSettings(user5);
        serverSettings.addSettings(ServerSettings.ofDiscordServer(TEST_SERVER_ID, Dates.UTC, "", "", "", now));
        alerts.addAlert(createTestAlertWithUserId(user5.discordUserId()));

        assertTrue(userSettings.getUserSettings(DISCORD, user1.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user2.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user3.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user4.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user5.discordUserId()).isPresent());

        userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusMonths(1L));
        assertTrue(userSettings.getUserSettings(DISCORD, user1.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user2.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user3.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user4.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user5.discordUserId()).isPresent());

        now = now.plusMinutes(1L);
        userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusWeeks(1L));
        assertTrue(userSettings.getUserSettings(DISCORD, user1.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user2.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user3.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user4.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user5.discordUserId()).isPresent());

        userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusDays(1L));
        assertTrue(userSettings.getUserSettings(DISCORD, user1.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user2.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user3.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user4.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user5.discordUserId()).isPresent());

        userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusHours(1L));
        assertTrue(userSettings.getUserSettings(DISCORD, user1.discordUserId()).isPresent());
        assertTrue(userSettings.getUserSettings(DISCORD, user2.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user3.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user4.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user5.discordUserId()).isPresent());

        userSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now);
        assertTrue(userSettings.getUserSettings(DISCORD, user1.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user2.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user3.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user4.discordUserId()).isEmpty());
        assertTrue(userSettings.getUserSettings(DISCORD, user5.discordUserId()).isPresent());
    }
}