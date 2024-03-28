package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.entities.settings.UserSettings;
import org.sbot.entities.alerts.ClientType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public interface UserSettingsDao {

    record ClientTypeUserId(@NotNull ClientType clientType, long userId) {
        public ClientTypeUserId {
            requireNonNull(clientType);
        }

        public static ClientTypeUserId of(@NotNull ClientType clientType, long userId) {
            return new ClientTypeUserId(clientType, userId);
        }
    }

    Optional<UserSettings> getUserSettings(@NotNull ClientType clientType, long userId);

    boolean userExists(@NotNull ClientType clientType, long userId);

    Map<ClientTypeUserId, Locale> getLocales(@NotNull List<ClientTypeUserId> clientTypeUserIds);

    void addSettings(@NotNull UserSettings settings);

    void updateUserLocale(@NotNull ClientType clientType, long userId, @NotNull Locale locale);

    void updateUserTimezone(@NotNull ClientType clientType, long userId, @NotNull ZoneId timezone);

    void updateLastAccess(@NotNull ClientType clientType, long userId, @NotNull ZonedDateTime lastAccess);

    long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ClientType clientType, @NotNull ZonedDateTime expirationDate);
}
