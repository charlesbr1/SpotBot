package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.settings.UserSettings;
import org.sbot.entities.alerts.ClientType;
import org.sbot.services.dao.UserSettingsDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.sbot.services.dao.sql.UserSettingsSQLite.SQL.EXPIRATION_DATE_ARGUMENT;
import static org.sbot.services.dao.sql.UserSettingsSQLite.SQL.Fields.*;
import static org.sbot.utils.Dates.parseUtcDateTime;

public class UserSettingsSQLite extends AbstractJDBI implements UserSettingsDao {

    private static final Logger LOGGER = LogManager.getLogger(UserSettingsSQLite.class);

    interface SQL {

        interface Fields {
            String DISCORD_USER_ID = "discord_user_id";
            String LOCALE = "locale";
            String TIMEZONE = "timezone";
            String LAST_ACCESS = "last_access";
        }

        String EXPIRATION_DATE_ARGUMENT = "expirationDate";

        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS user_settings (
                discord_user_id INTEGER PRIMARY KEY,
                locale TEXT NOT NULL,
                timezone TEXT NOT NULL,
                last_access INTEGER NOT NULL) STRICT
                """;

        String CREATE_LAST_ACCESS_INDEX = "CREATE INDEX IF NOT EXISTS user_settings_last_access_index ON user_settings (last_access)";

        String SELECT_BY_DISCORD_USER_ID = "SELECT * FROM user_settings WHERE discord_user_id=:discord_user_id";
        String COUNT_BY_DISCORD_USER_ID = "SELECT count(*) FROM user_settings WHERE discord_user_id=:discord_user_id";
        String SELECT_DISCORD_USER_ID_LOCALE_HAVING_DISCORD_USER_ID_IN = "SELECT discord_user_id,locale FROM user_settings WHERE discord_user_id IN (<ids>)";
        String INSERT_USER = "INSERT INTO user_settings (discord_user_id,locale,timezone,last_access) VALUES (:discord_user_id,:locale,:timezone,:last_access)";
        String UPDATE_LOCALE_OF_DISCORD_USER_ID = "UPDATE user_settings SET locale=:locale WHERE discord_user_id=:discord_user_id";
        String UPDATE_TIMEZONE_OF_DISCORD_USER_ID = "UPDATE user_settings SET timezone=:timezone WHERE discord_user_id=:discord_user_id";
        String UPDATE_LAST_ACCESS_OF_DISCORD_USER_ID = "UPDATE user_settings SET last_access=:last_access WHERE discord_user_id=:discord_user_id";
        String DELETE_DISCORD_USERS_HAVING_LAST_ACCESS_BEFORE_AND_NOT_IN_ALERTS = "DELETE FROM user_settings WHERE last_access<:expirationDate AND discord_user_id NOT IN (SELECT a.user_id FROM alerts a)";
    }

    public static final class UserSettingsMapper implements RowMapper<UserSettings> {
        @Override
        public UserSettings map(ResultSet rs, StatementContext ctx) throws SQLException {
            long discordUserId = rs.getLong(DISCORD_USER_ID);
            var locale = Optional.ofNullable(rs.getString(LOCALE)).map(Locale::forLanguageTag)
                    .orElseThrow(() -> new IllegalArgumentException("Missing field user locale"));
            var timezone = ZoneId.of(rs.getString(TIMEZONE), ZoneId.SHORT_IDS);
            var lastAccess = parseUtcDateTime(rs.getTimestamp(LAST_ACCESS))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field user last_access"));
            return UserSettings.ofDiscordUser(discordUserId, locale, timezone, lastAccess);
        }
    }

    private static void bindUserSettingsFields(@NotNull UserSettings user, @NotNull SqlStatement<?> query) {
        query.bind(DISCORD_USER_ID, user.discordUserId());
        query.bind(LOCALE, user.locale());
        query.bind(TIMEZONE, user.timezone());
        query.bind(LAST_ACCESS, user.lastAccess());
    }

    public UserSettingsSQLite(@NotNull JDBIRepository repository) {
        super(repository, new UserSettingsMapper());
        LOGGER.debug("Loading SQLite storage for user settings");
    }

    UserSettingsSQLite(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
        super(abstractJDBI, transactionHandler);
    }

    @Override
    public UserSettingsSQLite withHandler(@NotNull JDBITransactionHandler transactionHandler) {
        return new UserSettingsSQLite(this, transactionHandler);
    }

    @Override
    protected void setupTable(@NotNull Handle handle) {
        handle.execute(SQL.CREATE_TABLE);
        handle.execute(SQL.CREATE_LAST_ACCESS_INDEX);
    }

    @Override
    public Optional<UserSettings> getUserSettings(@NotNull ClientType clientType, long userId) {
        LOGGER.debug("getUserSettings {} {}", clientType, userId);
        return switch (clientType) {
            case DISCORD -> findOne(SQL.SELECT_BY_DISCORD_USER_ID, UserSettings.class, Map.of(DISCORD_USER_ID, userId));
        };
    }

    @Override
    public boolean userExists(@NotNull ClientType clientType, long userId) {
        LOGGER.debug("userExists {} {}", clientType, userId);
        return switch (clientType) {
            case DISCORD -> 0L < queryOneLong(SQL.COUNT_BY_DISCORD_USER_ID, Map.of(DISCORD_USER_ID, userId));
        };
    }

    @Override
    @NotNull
    public Map<ClientTypeUserId, Locale> getLocales(@NotNull List<ClientTypeUserId> userIds) {
        LOGGER.debug("getLocales {}", userIds);
        if(userIds.isEmpty()) {
            return emptyMap();
        }
        HashMap<ClientTypeUserId, Locale> result = new HashMap<>(userIds.size());
        userIds.stream().collect(groupingBy(ClientTypeUserId::clientType)).forEach((ct, uids) -> {
            switch (ct) {
                case DISCORD: super.<Long, Locale>queryMap(SQL.SELECT_DISCORD_USER_ID_LOCALE_HAVING_DISCORD_USER_ID_IN,
                                new GenericType<>() {},
                                query -> query.bindList("ids", uids.stream().map(ClientTypeUserId::userId).toList()), DISCORD_USER_ID, LOCALE)
                        .forEach((userId, locale) -> result.put(ClientTypeUserId.of(ClientType.DISCORD, userId), locale));
            }
        });
        return result;
    }

    @Override
    public void addSettings(@NotNull UserSettings settings) {
        LOGGER.debug("addSettings {}", settings);
        requireNonNull(settings);
        update(SQL.INSERT_USER, query -> bindUserSettingsFields(settings, query));
    }

    @Override
    public void updateUserLocale(@NotNull ClientType clientType, long userId, @NotNull Locale locale) {
        LOGGER.debug("updateUserLocale {} {} {}", clientType, userId, locale);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_LOCALE_OF_DISCORD_USER_ID, Map.of(DISCORD_USER_ID, userId, LOCALE, locale));
        }
    }

    @Override
    public void updateUserTimezone(@NotNull ClientType clientType, long userId, @NotNull ZoneId timezone) {
        LOGGER.debug("updateUserTimezone {} {} {}", clientType, userId, timezone);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_TIMEZONE_OF_DISCORD_USER_ID, Map.of(DISCORD_USER_ID, userId, TIMEZONE, timezone));
        }
    }

    @Override
    public void updateLastAccess(@NotNull ClientType clientType, long userId, @NotNull ZonedDateTime lastAccess) {
        LOGGER.debug("updateLastAccess {} {} {}", clientType, userId, lastAccess);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_LAST_ACCESS_OF_DISCORD_USER_ID, Map.of(DISCORD_USER_ID, userId, LAST_ACCESS, lastAccess));
        }
    }

    @Override
    public long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ClientType clientType, @NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingLastAccessBeforeAndNotInAlerts {} {}", clientType, expirationDate);
        return switch (clientType) {
            case DISCORD -> update(SQL.DELETE_DISCORD_USERS_HAVING_LAST_ACCESS_BEFORE_AND_NOT_IN_ALERTS,
                    Map.of(EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()));
        };
    }
}
