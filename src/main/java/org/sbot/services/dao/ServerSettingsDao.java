package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.alerts.ClientType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface ServerSettingsDao {

    Optional<ServerSettings> getServerSettings(@NotNull ClientType clientType, long serverId);

    void addSettings(@NotNull ServerSettings settings);


    void updateServerTimezone(@NotNull ClientType clientType, long serverId, @NotNull ZoneId timezone);
    void updateServerSpotBotChannel(@NotNull ClientType clientType, long serverId, @NotNull String spotBotChannel);
    void updateServerSpotBotRole(@NotNull ClientType clientType, long serverId, @NotNull String spotBotRole);
    void updateServerSpotBotAdminRole(@NotNull ClientType clientType, long serverId, @NotNull String spotBotAdminRole);

    void updateLastAccess(@NotNull ClientType clientType, long serverId, @NotNull ZonedDateTime lastAccess);

    long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ClientType clientType, @NotNull ZonedDateTime expirationDate);
}
