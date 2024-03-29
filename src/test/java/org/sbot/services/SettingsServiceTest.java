package org.sbot.services;

import org.junit.jupiter.api.Test;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.dao.ServerSettingsDao;
import org.sbot.services.dao.UserSettingsDao;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.settings.ServerSettings.PRIVATE_SERVER;
import static org.sbot.entities.settings.ServerSettings.ofDiscordServer;
import static org.sbot.entities.settings.UserSettings.*;

class SettingsServiceTest {

    @Test
    void setupSettings() {
        long userId = 123L;
        long serverId = 321L;
        Context context = mock();
        var now = DatesTest.nowUtc();
        Clock clock = Clock.fixed(now.toInstant(), Dates.UTC);
        when(context.clock()).thenReturn(clock);
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        UserSettingsDao userSettingsDao = mock();
        ServerSettingsDao serverSettingsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.userSettingsDao()).thenReturn(v -> userSettingsDao);
        when(dataServices.serverSettingsDao()).thenReturn(v -> serverSettingsDao);
        SettingsService settingsService = new SettingsService(context);

        assertThrows(NullPointerException.class, () -> settingsService.setupSettings(null, userId, serverId, DEFAULT_LOCALE));
        assertThrows(NullPointerException.class, () -> settingsService.setupSettings(DISCORD, userId, serverId, null));

        var settings = settingsService.setupSettings(DISCORD, userId, NO_ID, DEFAULT_LOCALE);
        assertNotNull(settings);
        assertEquals(PRIVATE_SERVER, settings.serverSettings());
        assertEquals(ofDiscordUser(userId, DEFAULT_LOCALE, PRIVATE_SERVER.timezone(), now), settings.userSettings());

        verify(serverSettingsDao, never()).getServerSettings(any(), anyLong());
        verify(serverSettingsDao, never()).addSettings(any());
        verify(serverSettingsDao, never()).updateLastAccess(any(), anyLong(), any());
        verify(userSettingsDao).getUserSettings(DISCORD, userId);
        verify(userSettingsDao).addSettings(ofDiscordUser(userId, DEFAULT_LOCALE, PRIVATE_SERVER.timezone(), now));

        // test server creation / update
        when(serverSettingsDao.getServerSettings(DISCORD, serverId))
                .thenReturn(Optional.of(ofDiscordServer(serverId, now.minusMinutes(10L))));
        settings = settingsService.setupSettings(DISCORD, userId, serverId, DEFAULT_LOCALE);
        assertNotNull(settings);
        assertEquals(ofDiscordServer(serverId, now.minusMinutes(10L)), settings.serverSettings());
        assertEquals(ofDiscordUser(userId, DEFAULT_LOCALE, PRIVATE_SERVER.timezone(), now), settings.userSettings());
        verify(serverSettingsDao, never()).addSettings(any());
        verify(serverSettingsDao).getServerSettings(DISCORD, serverId);
        verify(serverSettingsDao).updateLastAccess(DISCORD, serverId, now);

        when(serverSettingsDao.getServerSettings(DISCORD, serverId)).thenReturn(Optional.empty());
        settings = settingsService.setupSettings(DISCORD, userId, serverId, DEFAULT_LOCALE);
        assertNotNull(settings);
        assertEquals(ofDiscordServer(serverId, now), settings.serverSettings());
        assertEquals(ofDiscordUser(userId, DEFAULT_LOCALE, PRIVATE_SERVER.timezone(), now), settings.userSettings());
        verify(serverSettingsDao, times(2)).getServerSettings(DISCORD, serverId);
        verify(serverSettingsDao).updateLastAccess(DISCORD, serverId, now); // no more calls
        verify(serverSettingsDao).addSettings(ofDiscordServer(serverId, now));

        // test user creation / update
        reset(userSettingsDao);
        when(userSettingsDao.getUserSettings(DISCORD, userId))
                .thenReturn(Optional.of(ofDiscordUser(userId, DEFAULT_LOCALE, Dates.UTC, now.minusMinutes(10L))));
        settings = settingsService.setupSettings(DISCORD, userId, NO_ID, DEFAULT_LOCALE);
        assertNotNull(settings);
        assertEquals(PRIVATE_SERVER, settings.serverSettings());
        assertEquals(ofDiscordUser(userId, DEFAULT_LOCALE, Dates.UTC, now.minusMinutes(10L)), settings.userSettings());
        verify(userSettingsDao, never()).addSettings(any());
        verify(userSettingsDao).getUserSettings(DISCORD, userId);
        verify(userSettingsDao).updateLastAccess(DISCORD, userId, now);

        when(userSettingsDao.getUserSettings(DISCORD, userId)).thenReturn(Optional.empty());
        settings = settingsService.setupSettings(DISCORD, userId, NO_ID, DEFAULT_LOCALE);
        assertNotNull(settings);
        assertEquals(PRIVATE_SERVER, settings.serverSettings());
        assertEquals(ofDiscordUser(userId, DEFAULT_LOCALE, PRIVATE_SERVER.timezone(), now), settings.userSettings());
        verify(userSettingsDao, times(2)).getUserSettings(DISCORD, userId);
        verify(userSettingsDao).updateLastAccess(DISCORD, userId, now); // no more calls
        verify(userSettingsDao).addSettings(ofDiscordUser(userId, DEFAULT_LOCALE, PRIVATE_SERVER.timezone(), now));
    }

    @Test
    void accessSettings() {
        long userId = 123L;
        long serverId = 321L;
        Context context = mock();
        var now = DatesTest.nowUtc();
        Clock clock = Clock.fixed(now.toInstant(), Dates.UTC);
        when(context.clock()).thenReturn(clock);
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        UserSettingsDao userSettingsDao = mock();
        ServerSettingsDao serverSettingsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.userSettingsDao()).thenReturn(v -> userSettingsDao);
        when(dataServices.serverSettingsDao()).thenReturn(v -> serverSettingsDao);
        SettingsService settingsService = new SettingsService(context);

        assertThrows(NullPointerException.class, () -> settingsService.accessSettings(null, userId, serverId));

        var settings = settingsService.accessSettings(DISCORD, userId, NO_ID);
        assertNotNull(settings);
        assertEquals(PRIVATE_SERVER, settings.serverSettings());
        assertEquals(NO_USER, settings.userSettings());

        verify(serverSettingsDao, never()).getServerSettings(any(), anyLong());
        verify(serverSettingsDao, never()).addSettings(any());
        verify(serverSettingsDao, never()).updateLastAccess(any(), anyLong(), any());
        verify(userSettingsDao).getUserSettings(DISCORD, userId);
        verify(userSettingsDao, never()).addSettings(any());

        // test server creation / update
        when(serverSettingsDao.getServerSettings(DISCORD, serverId))
                .thenReturn(Optional.of(ofDiscordServer(serverId, now.minusMinutes(10L))));
        settings = settingsService.accessSettings(DISCORD, userId, serverId);
        assertNotNull(settings);
        assertEquals(ofDiscordServer(serverId, now.minusMinutes(10L)), settings.serverSettings());
        assertEquals(NO_USER, settings.userSettings());
        verify(serverSettingsDao, never()).addSettings(any());
        verify(serverSettingsDao).getServerSettings(DISCORD, serverId);
        verify(serverSettingsDao).updateLastAccess(DISCORD, serverId, now);

        when(serverSettingsDao.getServerSettings(DISCORD, serverId)).thenReturn(Optional.empty());
        settings = settingsService.accessSettings(DISCORD, userId, serverId);
        assertNotNull(settings);
        assertEquals(ofDiscordServer(serverId, now), settings.serverSettings());
        assertEquals(NO_USER, settings.userSettings());
        verify(serverSettingsDao, times(2)).getServerSettings(DISCORD, serverId);
        verify(serverSettingsDao).updateLastAccess(DISCORD, serverId, now); // no more calls
        verify(serverSettingsDao).addSettings(ofDiscordServer(serverId, now));

        // test user creation / update, accessSettings never create a user
        reset(userSettingsDao);
        when(userSettingsDao.getUserSettings(DISCORD, userId))
                .thenReturn(Optional.of(ofDiscordUser(userId, DEFAULT_LOCALE, Dates.UTC, now.minusMinutes(10L))));
        settings = settingsService.accessSettings(DISCORD, userId, NO_ID);
        assertNotNull(settings);
        assertEquals(PRIVATE_SERVER, settings.serverSettings());
        assertEquals(ofDiscordUser(userId, DEFAULT_LOCALE, Dates.UTC, now.minusMinutes(10L)), settings.userSettings());
        verify(userSettingsDao, never()).addSettings(any());
        verify(userSettingsDao).getUserSettings(DISCORD, userId);
        verify(userSettingsDao).updateLastAccess(DISCORD, userId, now);

        when(userSettingsDao.getUserSettings(DISCORD, userId)).thenReturn(Optional.empty());
        settings = settingsService.accessSettings(DISCORD, userId, NO_ID);
        assertNotNull(settings);
        assertEquals(PRIVATE_SERVER, settings.serverSettings());
        assertEquals(NO_USER, settings.userSettings());
        verify(userSettingsDao, never()).addSettings(any());
        verify(userSettingsDao, times(2)).getUserSettings(DISCORD, userId);
        verify(userSettingsDao).updateLastAccess(DISCORD, userId, now); // no more calls
    }

    @Test
    void newUserSettings() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> SettingsService.newUserSettings(null, now, DISCORD, 123L, Locale.ENGLISH, Dates.UTC));
        assertThrows(NullPointerException.class, () -> SettingsService.newUserSettings(mock(), null, DISCORD, 123L, Locale.ENGLISH, Dates.UTC));
        assertThrows(NullPointerException.class, () -> SettingsService.newUserSettings(mock(), now, null, 123L, Locale.ENGLISH, Dates.UTC));
        assertThrows(NullPointerException.class, () -> SettingsService.newUserSettings(mock(), now, DISCORD, 123L, Locale.ENGLISH, null));

        UserSettingsDao userSettingsDao = mock();
        var settings = SettingsService.newUserSettings(userSettingsDao, now, DISCORD, 123L, null, Dates.UTC);
        assertEquals(NO_USER, settings);
        verify(userSettingsDao, never()).addSettings(any());

        settings = SettingsService.newUserSettings(userSettingsDao, now, DISCORD, 123L, DEFAULT_LOCALE, ZoneId.of("Europe/Paris"));
        assertEquals(ofDiscordUser(123L, DEFAULT_LOCALE, ZoneId.of("Europe/Paris"), now), settings);
        verify(userSettingsDao).addSettings(ofDiscordUser(123L, DEFAULT_LOCALE, ZoneId.of("Europe/Paris"), now));
    }

    @Test
    void newServerSettings() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> SettingsService.newServerSettings(null, now, DISCORD, 123L));
        assertThrows(NullPointerException.class, () -> SettingsService.newServerSettings(mock(), null, DISCORD, 123L));
        assertThrows(NullPointerException.class, () -> SettingsService.newServerSettings(mock(), now, null, 123L));

        ServerSettingsDao serverSettingsDao = mock();
        var settings = SettingsService.newServerSettings(serverSettingsDao, now, DISCORD, 123L);
        assertNotNull(settings);
        verify(serverSettingsDao).addSettings(ofDiscordServer(123L, now));
    }
}