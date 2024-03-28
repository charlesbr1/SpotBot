package org.sbot.entities.settings;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.entities.settings.UserSettings.*;

class UserSettingsTest {

    @Test
    void constructor() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> new UserSettings(NO_ID, null, DEFAULT_TIMEZONE, now));
        assertThrows(NullPointerException.class, () -> new UserSettings(NO_ID, DEFAULT_LOCALE, null, now));
        assertThrows(NullPointerException.class, () -> new UserSettings(NO_ID, DEFAULT_LOCALE, DEFAULT_TIMEZONE, null));

        var userSettings = new UserSettings(123L, DEFAULT_LOCALE, DEFAULT_TIMEZONE, now);
        assertEquals(123L, userSettings.discordUserId());
        assertEquals(DEFAULT_LOCALE, userSettings.locale());
        assertEquals(DEFAULT_TIMEZONE, userSettings.timezone());
        assertEquals(now, userSettings.lastAccess());
    }

    @Test
    void ofDiscordUser() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> UserSettings.ofDiscordUser(NO_ID, null, DEFAULT_TIMEZONE, now));
        assertThrows(NullPointerException.class, () -> UserSettings.ofDiscordUser(NO_ID, DEFAULT_LOCALE, null, now));
        assertThrows(NullPointerException.class, () -> UserSettings.ofDiscordUser(NO_ID, DEFAULT_LOCALE, DEFAULT_TIMEZONE, null));

        var userSettings = UserSettings.ofDiscordUser(321L, Locale.US, ZoneId.of("CET"), now);
        assertEquals(321L, userSettings.discordUserId());
        assertEquals(Locale.US, userSettings.locale());
        assertEquals(ZoneId.of("CET"), userSettings.timezone());
        assertEquals(now, userSettings.lastAccess());
    }

    @Test
    void withLocale() {
        var serverSettings = UserSettings.ofDiscordUser(321L, Locale.JAPANESE, Dates.UTC, DatesTest.nowUtc());
        assertEquals(Locale.JAPANESE, serverSettings.locale());
        assertEquals(Locale.FRANCE, serverSettings.withLocale(Locale.FRANCE).locale());
    }

    @Test
    void withTimezone() {
        var serverSettings = UserSettings.ofDiscordUser(321L, Locale.JAPANESE, Dates.UTC, DatesTest.nowUtc());
        assertEquals(Dates.UTC, serverSettings.timezone());
        assertEquals(ZoneId.of("Europe/Paris"), serverSettings.withTimezone(ZoneId.of("Europe/Paris")).timezone());
    }

    @Test
    void withLastAccess() {
        var now = DatesTest.nowUtc();
        var serverSettings = UserSettings.ofDiscordUser(321L, Locale.JAPANESE, Dates.UTC, now);
        assertEquals(now, serverSettings.lastAccess());
        assertEquals(now.minusMinutes(57L), serverSettings.withLastAccess(now.minusMinutes(57L)).lastAccess());
    }
}