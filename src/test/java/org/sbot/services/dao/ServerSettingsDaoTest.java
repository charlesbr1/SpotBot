package org.sbot.services.dao;

import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.UserSettings;
import org.sbot.services.dao.sql.ServerSettingsSQLite;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.AlertTest.TEST_USER_ID;
import static org.sbot.entities.alerts.AlertTest.createTestAlert;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.settings.ServerSettings.*;
import static org.sbot.utils.DatesTest.nowUtc;

public abstract class ServerSettingsDaoTest {

    @ParameterizedTest
    @MethodSource("provideDao")
    void getServerSettings(ServerSettingsDao serverSettings) {
        assertThrows(NullPointerException.class, () -> serverSettings.getServerSettings(null, 123L));

        long serverId = 123L;
        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isEmpty());
        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(settings, serverSettings.getServerSettings(DISCORD, serverId).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void addSettings(ServerSettingsDao serverSettings) {
        assertThrows(NullPointerException.class, () -> serverSettings.addSettings(null));

        long serverId = 123L;
        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isEmpty());
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(settings, serverSettings.getServerSettings(DISCORD, serverId).get());

        var exClass = serverSettings instanceof ServerSettingsSQLite ?
                UnableToExecuteStatementException.class : IllegalArgumentException.class;
        assertThrows(exClass, () -> serverSettings.addSettings(settings));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerTimezone(ServerSettingsDao serverSettings) {
        long serverId = 567L;
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerTimezone(null, 1L, Dates.UTC));
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerTimezone(DISCORD, 1L, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(Dates.UTC, serverSettings.getServerSettings(DISCORD, serverId).get().timezone());

        serverSettings.updateServerTimezone(DISCORD, serverId, ZoneId.of("Europe/Paris"));
        assertEquals(ZoneId.of("Europe/Paris"), serverSettings.getServerSettings(DISCORD, serverId).get().timezone());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerSpotBotChannel(ServerSettingsDao serverSettings) {
        long serverId = 567L;
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerSpotBotChannel(null, 1L, "spotChannel"));
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerSpotBotChannel(DISCORD, 1L, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(DEFAULT_BOT_CHANNEL, serverSettings.getServerSettings(DISCORD, serverId).get().spotBotChannel());

        serverSettings.updateServerSpotBotChannel(DISCORD, serverId, "newChannel");
        assertEquals("newChannel", serverSettings.getServerSettings(DISCORD, serverId).get().spotBotChannel());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerSpotBotRole(ServerSettingsDao serverSettings) {
        long serverId = 567L;
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerSpotBotRole(null, 1L, "role"));
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerSpotBotRole(DISCORD, 1L, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(DEFAULT_BOT_ROLE, serverSettings.getServerSettings(DISCORD, serverId).get().spotBotRole());

        serverSettings.updateServerSpotBotRole(DISCORD, serverId, "newRole");
        assertEquals("newRole", serverSettings.getServerSettings(DISCORD, serverId).get().spotBotRole());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerSpotBotAdminRole(ServerSettingsDao serverSettings) {
        long serverId = 567L;
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerSpotBotAdminRole(null, 1L, "Adminrole"));
        assertThrows(NullPointerException.class, () -> serverSettings.updateServerSpotBotAdminRole(DISCORD, 1L, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(DEFAULT_BOT_ROLE_ADMIN, serverSettings.getServerSettings(DISCORD, serverId).get().spotBotAdminRole());

        serverSettings.updateServerSpotBotAdminRole(DISCORD, serverId, "newAdminRole");
        assertEquals("newAdminRole", serverSettings.getServerSettings(DISCORD, serverId).get().spotBotAdminRole());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLastAccess(ServerSettingsDao serverSettings) {
        long serverId = 567L;
        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        assertThrows(NullPointerException.class, () -> serverSettings.updateLastAccess(null, 1L, now));
        assertThrows(NullPointerException.class, () -> serverSettings.updateLastAccess(DISCORD, 1L, null));

        ServerSettings settings = new ServerSettings(serverId, Dates.UTC, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, now);
        serverSettings.addSettings(settings);
        assertTrue(serverSettings.getServerSettings(DISCORD, serverId).isPresent());
        assertEquals(now, serverSettings.getServerSettings(DISCORD, serverId).get().lastAccess());

        serverSettings.updateLastAccess(DISCORD, serverId, now.plusMinutes(73L));
        assertEquals(now.plusMinutes(73L), serverSettings.getServerSettings(DISCORD, serverId).get().lastAccess());
    }

    @ParameterizedTest
    @MethodSource("provideAlertWithSettingsDao")
    void deleteHavingLastAccessBeforeAndNotInAlerts(AlertsDao alerts, UserSettingsDao userSettings, ServerSettingsDao serverSettings) {
        assertThrows(NullPointerException.class, () -> serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(null, DatesTest.nowUtc()));
        assertThrows(NullPointerException.class, () -> serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, null));

        ZonedDateTime now = nowUtc().truncatedTo(ChronoUnit.MILLIS); // sqlite save milliseconds and not nanos
        var server1 = new ServerSettings(1L, Dates.UTC, "channel1", "role1", "admin1", now);
        var server2 = new ServerSettings(2L, Dates.UTC, "channel1", "role1", "admin1", now.minusHours(1L));
        var server3 = new ServerSettings(3L, Dates.UTC, "channel1", "role1", "admin1", now.minusDays(1L));
        var server4 = new ServerSettings(4L, Dates.UTC, "channel1", "role1", "admin1", now.minusWeeks(1L));
        var server5 = new ServerSettings(5L, Dates.UTC, "channel1", "role1", "admin1", now.minusDays(1L));
        serverSettings.addSettings(server1);
        serverSettings.addSettings(server2);
        serverSettings.addSettings(server3);
        serverSettings.addSettings(server4);
        serverSettings.addSettings(server5);
        userSettings.addSettings(UserSettings.ofDiscordUser(TEST_USER_ID, Locale.US, Dates.UTC, now));
        alerts.addAlert(createTestAlert().withServerId(server5.discordServerId()));

        assertTrue(serverSettings.getServerSettings(DISCORD, server1.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server2.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server3.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server4.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server5.discordServerId()).isPresent());

        serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusMonths(1L));
        assertTrue(serverSettings.getServerSettings(DISCORD, server1.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server2.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server3.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server4.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server5.discordServerId()).isPresent());

        now = now.plusMinutes(1L);
        serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusWeeks(1L));
        assertTrue(serverSettings.getServerSettings(DISCORD, server1.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server2.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server3.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server4.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server5.discordServerId()).isPresent());

        serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusDays(1L));
        assertTrue(serverSettings.getServerSettings(DISCORD, server1.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server2.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server3.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server4.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server5.discordServerId()).isPresent());

        serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now.minusHours(1L));
        assertTrue(serverSettings.getServerSettings(DISCORD, server1.discordServerId()).isPresent());
        assertTrue(serverSettings.getServerSettings(DISCORD, server2.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server3.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server4.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server5.discordServerId()).isPresent());

        serverSettings.deleteHavingLastAccessBeforeAndNotInAlerts(DISCORD, now);
        assertTrue(serverSettings.getServerSettings(DISCORD, server1.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server2.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server3.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server4.discordServerId()).isEmpty());
        assertTrue(serverSettings.getServerSettings(DISCORD, server5.discordServerId()).isPresent());
    }
}