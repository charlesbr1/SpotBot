package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.settings.UserSettings;
import org.sbot.entities.alerts.ClientType;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.UserSettingsDao;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.sbot.entities.settings.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.settings.UserSettings.NO_ID;

public class UserSettingsMemory implements UserSettingsDao {

    private static final Logger LOGGER = LogManager.getLogger(UserSettingsMemory.class);

    private final Map<Long, UserSettings> discordUserSettings = new ConcurrentHashMap<>();

    private final AlertsMemory alerts;

    public UserSettingsMemory(@NotNull AlertsMemory alerts) {
        LOGGER.debug("Loading memory storage for user settings");
        this.alerts = requireNonNull(alerts);
    }

    private Optional<UserSettings> getUserSettings(long userId, @NotNull ClientType clientType) {
        return switch (clientType) {
            case DISCORD -> Optional.ofNullable(discordUserSettings.get(userId));
        };
    }

    private void updateUserSettings(long userId, @NotNull ClientType clientType, BiFunction<Long, UserSettings, UserSettings> remappingFunction) {
        switch (clientType) {
            case DISCORD: discordUserSettings.computeIfPresent(userId, remappingFunction);
        }
    }

    @Override
    public Optional<UserSettings> getUserSettings(@NotNull ClientType clientType, long userId) {
        LOGGER.debug("getUserSettings {} {}", clientType, userId);
        return getUserSettings(userId, clientType);
    }

    @Override
    public boolean userExists(@NotNull ClientType clientType, long userId) {
        LOGGER.debug("userExists {} {}", clientType, userId);
        return getUserSettings(userId, clientType).isPresent();
    }

    @Override
    @NotNull
    public Map<ClientTypeUserId, Locale> getLocales(@NotNull List<ClientTypeUserId> clientTypeUserIds) {
        LOGGER.debug("getLocales {}", clientTypeUserIds);
        return clientTypeUserIds.isEmpty() ? emptyMap() :
                clientTypeUserIds.stream()
                .collect(toMap(identity(), ct -> getUserSettings(ct.userId(), ct.clientType())
                        .map(UserSettings::locale).orElse(DEFAULT_LOCALE))); // unlike SQL dao this set DEFAULT_LOCALE for unknown users
    }

    @Override
    public void addSettings(@NotNull UserSettings settings) {
        LOGGER.debug("addSettings {}", settings);
        if(NO_ID != settings.discordUserId()) {
            if(null != discordUserSettings.putIfAbsent(settings.discordUserId(), settings)) {
                throw new IllegalArgumentException("UserSettings already exist : " + settings);
            }
        } else {
            throw new IllegalArgumentException("Missing settings userId : " + settings);
        }
    }

    @Override
    public void updateUserLocale(@NotNull ClientType clientType, long userId, @NotNull Locale locale) {
        LOGGER.debug("updateUserLocale {} {} {}", clientType, userId, locale);
        requireNonNull(locale);
        updateUserSettings(userId, clientType, (id, settings) -> settings.withLocale(locale));
    }

    @Override
    public void updateUserTimezone(@NotNull ClientType clientType, long userId, @NotNull ZoneId timezone) {
        LOGGER.debug("updateUserTimezone {} {} {}", clientType, userId, timezone);
        requireNonNull(timezone);
        updateUserSettings(userId, clientType, (id, settings) -> settings.withTimezone(timezone));
    }

    @Override
    public void updateLastAccess(@NotNull ClientType clientType, long userId, @NotNull ZonedDateTime lastAccess) {
        LOGGER.debug("updateLastAccess {} {} {}", clientType, userId, lastAccess);
        requireNonNull(clientType);
        requireNonNull(lastAccess);
        updateUserSettings(userId, clientType, (id, settings) -> settings.withLastAccess(lastAccess));
    }

    @Override
    public long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ClientType clientType, @NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingLastAccessBeforeAndNotInAlerts {} {}", clientType, expirationDate);
        requireNonNull(clientType);
        requireNonNull(expirationDate);
        var toDelete = discordUserSettings.values().stream()
                .filter(settings -> settings.lastAccess().isBefore(expirationDate) &&
                alerts.getAlertsStream(SelectionFilter.ofUser(clientType, switch (clientType) {
                            case DISCORD -> settings.discordUserId();
                        }, null)).findFirst().isEmpty()).toList();
        discordUserSettings.values().removeAll(toDelete);
        return toDelete.size();
    }
}
