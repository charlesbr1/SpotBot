package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.ClientType;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.Settings;
import org.sbot.entities.settings.UserSettings;
import org.sbot.services.context.Context;
import org.sbot.services.dao.ServerSettingsDao;
import org.sbot.services.dao.UserSettingsDao;
import org.sbot.utils.Dates;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static java.util.Objects.requireNonNull;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_UNCOMMITTED;
import static org.sbot.entities.settings.ServerSettings.PRIVATE_SERVER;
import static org.sbot.entities.settings.ServerSettings.ofDiscordServer;
import static org.sbot.entities.settings.UserSettings.*;

public final class SettingsService {

    private static final Logger LOGGER = LogManager.getLogger(SettingsService.class);

    private final Context context;

    public SettingsService(@NotNull Context context) {
        this.context = requireNonNull(context);
    }

    @NotNull
    public Settings setupSettings(@NotNull ClientType clientType, long userId, long serverId, @NotNull Locale locale) {
        var settings = accessSettings(clientType, userId, serverId, requireNonNull(locale));
        LOGGER.debug("setupSettings, clientType {}, userId {}, serverId {}, locale {}, found : {}", clientType, userId, serverId, locale, settings);
        return settings;
    }

    @NotNull
    public Settings accessSettings(@NotNull ClientType clientType, long userId, long serverId) {
        var settings = accessSettings(clientType, userId, serverId, null);
        LOGGER.debug("accessSettings, clientType {}, userId {}, serverId {}, found : {}", clientType, userId, serverId, settings);
        return settings;
    }

    @NotNull
    private Settings accessSettings(@NotNull ClientType clientType, long userId, long serverId, @Nullable Locale locale) {
        requireNonNull(clientType);
        return context.transactional(txCtx ->{
            var now = Dates.nowUtc(context.clock());
            var serversDao = txCtx.serverSettingsDao();
            var serverSettings = NO_ID == serverId ? PRIVATE_SERVER : serversDao.getServerSettings(clientType, serverId).orElse(null);
            if(null == serverSettings) {
                serverSettings = newServerSettings(serversDao, now, clientType, serverId);
            } else if(NO_ID != serverId) {
                serversDao.updateLastAccess(clientType, serverId, now);
            }
            var usersDao = txCtx.userSettingsDao();
            var userSettings = usersDao.getUserSettings(clientType, userId).orElse(null);
            if(null == userSettings) {
                userSettings = newUserSettings(usersDao, now, clientType, userId, locale, serverSettings.timezone());
            } else {
                usersDao.updateLastAccess(clientType, userId, now);
            }
            return new Settings(userSettings, serverSettings);
        }, READ_UNCOMMITTED, false);
    }

    @NotNull
    static UserSettings newUserSettings(@NotNull UserSettingsDao settingsDao, @NotNull ZonedDateTime now, @NotNull ClientType clientType, long userId, @Nullable Locale locale, @NotNull ZoneId timezone) {
        if(null == locale) {
            return NO_USER;
        }
        var user = switch (clientType) {
            case DISCORD -> ofDiscordUser(userId, locale, timezone, now);
        };
        settingsDao.addSettings(user);
        return user;
    }

    @NotNull
    static ServerSettings newServerSettings(@NotNull ServerSettingsDao settingsDao, @NotNull ZonedDateTime now, @NotNull ClientType clientType, long serverId) {
        var server = switch (clientType) {
            case DISCORD -> ofDiscordServer(serverId, now);
        };
        settingsDao.addSettings(server);
        return server;
    }
}
