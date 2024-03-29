package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.alerts.ClientType;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.ServerSettingsDao;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
import static org.sbot.entities.settings.UserSettings.NO_ID;

public class ServerSettingsMemory implements ServerSettingsDao {

    private static final Logger LOGGER = LogManager.getLogger(ServerSettingsMemory.class);

    private final Map<Long, ServerSettings> discordServerSettings = new ConcurrentHashMap<>();

    private final AlertsMemory alerts;

    public ServerSettingsMemory(@NotNull AlertsMemory alerts) {
        LOGGER.debug("Loading memory storage for server settings");
        this.alerts = requireNonNull(alerts);
    }

    private Optional<ServerSettings> getServerSettings(long serverId, @NotNull ClientType clientType) {
        return switch (clientType) {
            case DISCORD -> Optional.ofNullable(discordServerSettings.get(serverId));
        };
    }

    private void updateServerSettings(long serverId, @NotNull ClientType clientType, BiFunction<Long, ServerSettings, ServerSettings> remappingFunction) {
        switch (clientType) {
            case DISCORD: discordServerSettings.computeIfPresent(serverId, remappingFunction);
        }
    }

    @Override
    public Optional<ServerSettings> getServerSettings(@NotNull ClientType clientType, long serverId) {
        LOGGER.debug("getServerSettings {} {}", clientType, serverId);
        return getServerSettings(serverId, clientType);
    }

    @Override
    public void addSettings(@NotNull ServerSettings settings) {
        LOGGER.debug("addSettings {}", settings);
        for(var clientType : ClientType.values()) {
            switch (clientType) {
                case DISCORD:
                    if(NO_ID != settings.discordServerId() && null != discordServerSettings.putIfAbsent(settings.discordServerId(), settings)) {
                        throw new IllegalArgumentException("ServerSettings already exist : " + settings);
                    }
            }
        }
    }

    @Override
    public void updateServerTimezone(@NotNull ClientType clientType, long serverId, @NotNull ZoneId timezone) {
        LOGGER.debug("updateServerTimezone {} {} {}", clientType, serverId, timezone);
        requireNonNull(timezone);
        updateServerSettings(serverId, clientType, (id, settings) -> settings.withTimezone(timezone));
    }

    @Override
    public void updateServerSpotBotChannel(@NotNull ClientType clientType, long serverId, @NotNull String spotBotChannel) {
        LOGGER.debug("updateServerSpotBotChannel {} {} {}", clientType, serverId, spotBotChannel);
        requireNonNull(spotBotChannel);
        updateServerSettings(serverId, clientType, (id, settings) -> settings.withChannel(spotBotChannel));
    }

    @Override
    public void updateServerSpotBotRole(@NotNull ClientType clientType, long serverId, @NotNull String spotBotRole) {
        LOGGER.debug("updateServerSpotBotRole {} {} {}", clientType, serverId, spotBotRole);
        requireNonNull(spotBotRole);
        updateServerSettings(serverId, clientType, (id, settings) -> settings.withRole(spotBotRole));
    }

    @Override
    public void updateServerSpotBotAdminRole(@NotNull ClientType clientType, long serverId, @NotNull String spotBotAdminRole) {
        LOGGER.debug("updateServerSpotBotAdminRole {} {} {}", clientType, serverId, spotBotAdminRole);
        requireNonNull(spotBotAdminRole);
        updateServerSettings(serverId, clientType, (id, settings) -> settings.withAdminRole(spotBotAdminRole));
    }

    @Override
    public void updateLastAccess(@NotNull ClientType clientType, long serverId, @NotNull ZonedDateTime lastAccess) {
        LOGGER.debug("updateLastAccess {} {} {}", clientType, serverId, lastAccess);
        requireNonNull(lastAccess);
        updateServerSettings(serverId, clientType, (id, settings) -> settings.withLastAccess(lastAccess));
    }

    @Override
    public long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ClientType clientType, @NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingLastAccessBeforeAndNotInAlerts {} {}", clientType, expirationDate);
        requireNonNull(clientType);
        requireNonNull(expirationDate);
        var toDelete = discordServerSettings.values().stream()
                .filter(settings -> settings.lastAccess().isBefore(expirationDate) &&
                alerts.getAlertsStream(SelectionFilter.ofServer(clientType, switch (clientType) {
                            case DISCORD -> settings.discordServerId();
                        }, null)).findFirst().isEmpty()).toList();
        discordServerSettings.values().removeAll(toDelete);
        return toDelete.size();
    }
}
