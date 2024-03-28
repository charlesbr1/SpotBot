package org.sbot.entities.settings;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.entities.settings.ServerSettings.*;
import static org.sbot.entities.settings.UserSettings.DEFAULT_TIMEZONE;
import static org.sbot.entities.settings.UserSettings.NO_ID;
import static org.sbot.utils.ArgumentValidator.SETTINGS_MAX_LENGTH;

class ServerSettingsTest {

    @Test
    void constructor() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> new ServerSettings(NO_ID, null, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(NullPointerException.class, () -> new ServerSettings(NO_ID, Dates.UTC, null, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(NullPointerException.class, () -> new ServerSettings(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, null, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(NullPointerException.class, () -> new ServerSettings(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, null, now));
        assertThrows(NullPointerException.class, () -> new ServerSettings(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, null));
        var argTooLong = "1".repeat(SETTINGS_MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> new ServerSettings(NO_ID, Dates.UTC, argTooLong, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(IllegalArgumentException.class, () -> new ServerSettings(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, argTooLong, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(IllegalArgumentException.class, () -> new ServerSettings(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, argTooLong, now));

        var serverSettings = new ServerSettings(123L, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        assertEquals(123L, serverSettings.discordServerId());
        assertEquals(Dates.UTC, serverSettings.timezone());
        assertEquals(DEFAULT_BOT_CHANNEL, serverSettings.spotBotChannel());
        assertEquals(DEFAULT_BOT_ROLE, serverSettings.spotBotRole());
        assertEquals(DEFAULT_BOT_ROLE_ADMIN, serverSettings.spotBotAdminRole());
        assertEquals(now, serverSettings.lastAccess());
    }

    @Test
    void ofDiscordServer() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> ServerSettings.ofDiscordServer(NO_ID, null));

        var serverSettings = ServerSettings.ofDiscordServer(321L, now);
        assertEquals(321L, serverSettings.discordServerId());
        assertEquals(DEFAULT_TIMEZONE, serverSettings.timezone());
        assertEquals(DEFAULT_BOT_CHANNEL, serverSettings.spotBotChannel());
        assertEquals(DEFAULT_BOT_ROLE, serverSettings.spotBotRole());
        assertEquals(DEFAULT_BOT_ROLE_ADMIN, serverSettings.spotBotAdminRole());
        assertEquals(now, serverSettings.lastAccess());

        assertThrows(NullPointerException.class, () -> ServerSettings.ofDiscordServer(NO_ID, null, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(NullPointerException.class, () -> ServerSettings.ofDiscordServer(NO_ID, Dates.UTC, null, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(NullPointerException.class, () -> ServerSettings.ofDiscordServer(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, null, DEFAULT_BOT_ROLE_ADMIN, now));
        assertThrows(NullPointerException.class, () -> ServerSettings.ofDiscordServer(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, null, now));
        assertThrows(NullPointerException.class, () -> ServerSettings.ofDiscordServer(NO_ID, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, null));

        serverSettings = ServerSettings.ofDiscordServer(789L, ZoneId.of("GMT"), "botChan", "botRol", "adminRo", now);
        assertEquals(789L, serverSettings.discordServerId());
        assertEquals(ZoneId.of("GMT"), serverSettings.timezone());
        assertEquals("botChan", serverSettings.spotBotChannel());
        assertEquals("botRol", serverSettings.spotBotRole());
        assertEquals("adminRo", serverSettings.spotBotAdminRole());
        assertEquals(now, serverSettings.lastAccess());
    }

    @Test
    void withTimezone() {
        var serverSettings = ServerSettings.ofDiscordServer(321L, DatesTest.nowUtc());
        assertEquals(DEFAULT_TIMEZONE, serverSettings.timezone());
        assertEquals(ZoneId.of("Europe/Paris"), serverSettings.withTimezone(ZoneId.of("Europe/Paris")).timezone());

    }

    @Test
    void withChannel() {
        var serverSettings = ServerSettings.ofDiscordServer(321L, DatesTest.nowUtc());
        assertEquals(DEFAULT_BOT_CHANNEL, serverSettings.spotBotChannel());
        assertEquals("testChan", serverSettings.withChannel("testChan").spotBotChannel());
    }

    @Test
    void withRole() {
        var serverSettings = ServerSettings.ofDiscordServer(321L, DatesTest.nowUtc());
        assertEquals(DEFAULT_BOT_ROLE, serverSettings.spotBotRole());
        assertEquals("testRole", serverSettings.withRole("testRole").spotBotRole());
    }

    @Test
    void withAdminRole() {
        var serverSettings = ServerSettings.ofDiscordServer(321L, DatesTest.nowUtc());
        assertEquals(DEFAULT_BOT_ROLE_ADMIN, serverSettings.spotBotAdminRole());
        assertEquals("testRoleAdmin", serverSettings.withAdminRole("testRoleAdmin").spotBotAdminRole());
    }

    @Test
    void withLastAccess() {
        var now = DatesTest.nowUtc();
        var serverSettings = ServerSettings.ofDiscordServer(321L, now);
        assertEquals(now, serverSettings.lastAccess());
        assertEquals(now.minusMinutes(17L), serverSettings.withLastAccess(now.minusMinutes(17L)).lastAccess());
    }
}