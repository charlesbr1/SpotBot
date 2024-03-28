package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.alerts.ClientType;
import org.sbot.services.dao.ServerSettingsDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;
import org.sbot.utils.Dates;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;
import static org.sbot.entities.settings.ServerSettings.*;
import static org.sbot.entities.settings.UserSettings.DEFAULT_TIMEZONE;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.services.dao.sql.ServerSettingsSQLite.SQL.EXPIRATION_DATE_ARGUMENT;
import static org.sbot.services.dao.sql.ServerSettingsSQLite.SQL.Fields.*;
import static org.sbot.utils.Dates.parseUtcDateTime;

public class ServerSettingsSQLite extends AbstractJDBI implements ServerSettingsDao {

    private static final Logger LOGGER = LogManager.getLogger(ServerSettingsSQLite.class);

    interface SQL {

        interface Fields {
            String DISCORD_SERVER_ID = "discord_server_id";
            String TIMEZONE = "timezone";
            String SPOTBOT_CHANNEL = "channel";
            String SPOTBOT_ROLE = "role";
            String SPOTBOT_ADMIN_ROLE = "admin_role";
            String LAST_ACCESS = "last_access";
        }

        String EXPIRATION_DATE_ARGUMENT = "expirationDate";

        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS server_settings (
                discord_server_id INTEGER PRIMARY KEY,
                timezone TEXT NOT NULL,
                channel TEXT NOT NULL,
                role TEXT NOT NULL,
                admin_role TEXT NOT NULL,
                last_access INTEGER NOT NULL) STRICT
                """;

        String CREATE_LAST_ACCESS_INDEX = "CREATE INDEX IF NOT EXISTS server_settings_last_access_index ON server_settings (last_access)";

        String SELECT_BY_DISCORD_SERVER_ID = "SELECT * FROM server_settings WHERE discord_server_id=:discord_server_id";
        String EXISTS_PRIVATE_SERVER = "SELECT count(*) FROM server_settings WHERE discord_server_id=" + PRIVATE_MESSAGES;
        String INSERT_PRIVATE_SERVER = "INSERT INTO server_settings (discord_server_id,timezone,channel,role,admin_role,last_access) VALUES (" + PRIVATE_MESSAGES + ",'" + DEFAULT_TIMEZONE + "','" + DEFAULT_BOT_CHANNEL + "','" + DEFAULT_BOT_ROLE + "','" + DEFAULT_BOT_ROLE_ADMIN + "'," + Dates.parseLocalDateTime(Locale.UK, "01/01/3000-00:00").toInstant(UTC).toEpochMilli() + ")";
        String INSERT_SERVER = "INSERT INTO server_settings (discord_server_id,timezone,channel,role,admin_role,last_access) VALUES (:discord_server_id,:timezone,:channel,:role,:admin_role,:last_access)";
        String UPDATE_TIMEZONE_OF_DISCORD_SERVER_ID = "UPDATE server_settings SET timezone=:timezone WHERE discord_server_id=:discord_server_id";
        String UPDATE_CHANNEL_OF_DISCORD_SERVER_ID = "UPDATE server_settings SET channel=:channel WHERE discord_server_id=:discord_server_id";
        String UPDATE_ROLE_OF_DISCORD_SERVER_ID = "UPDATE server_settings SET role=:role WHERE discord_server_id=:discord_server_id";
        String UPDATE_ADMIN_ROLE_OF_DISCORD_SERVER_ID = "UPDATE server_settings SET admin_role=:admin_role WHERE discord_server_id=:discord_server_id";
        String UPDATE_LAST_ACCESS_OF_DISCORD_SERVER_ID = "UPDATE server_settings SET last_access=:last_access WHERE discord_server_id=:discord_server_id";
        String DELETE_DISCORD_SERVERS_HAVING_LAST_ACCESS_BEFORE_AND_NOT_IN_ALERTS = "DELETE FROM server_settings WHERE last_access<:expirationDate AND discord_server_id NOT IN (SELECT a.server_id FROM alerts a)";
    }

    public static final class ServerSettingsMapper implements RowMapper<ServerSettings> {
        @Override
        public ServerSettings map(ResultSet rs, StatementContext ctx) throws SQLException {
            long discordServerId = rs.getLong(DISCORD_SERVER_ID);
            var timezone = ZoneId.of(rs.getString(TIMEZONE), ZoneId.SHORT_IDS);
            var spotBotChannel = rs.getString(SPOTBOT_CHANNEL);
            var spotBotRole = rs.getString(SPOTBOT_ROLE);
            var spotBotAdminRole = rs.getString(SPOTBOT_ADMIN_ROLE);
            var lastAccess = parseUtcDateTime(rs.getTimestamp(LAST_ACCESS))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field server last_access"));
            return ServerSettings.ofDiscordServer(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
        }
    }

    private static void bindServerSettingsFields(@NotNull ServerSettings server, @NotNull SqlStatement<?> query) {
        query.bind(DISCORD_SERVER_ID, server.discordServerId());
        query.bind(TIMEZONE, server.timezone());
        query.bind(SPOTBOT_CHANNEL, server.spotBotChannel());
        query.bind(SPOTBOT_ROLE, server.spotBotRole());
        query.bind(SPOTBOT_ADMIN_ROLE, server.spotBotAdminRole());
        query.bind(LAST_ACCESS, server.lastAccess());
    }

    public ServerSettingsSQLite(@NotNull JDBIRepository repository) {
        super(repository, new ServerSettingsMapper());
        LOGGER.debug("Loading SQLite storage for server settings");
    }

    ServerSettingsSQLite(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
        super(abstractJDBI, transactionHandler);
    }

    @Override
    public ServerSettingsSQLite withHandler(@NotNull JDBITransactionHandler transactionHandler) {
        return new ServerSettingsSQLite(this, transactionHandler);
    }

    @Override
    protected void setupTable(@NotNull Handle handle) {
        handle.execute(SQL.CREATE_TABLE);
        handle.execute(SQL.CREATE_LAST_ACCESS_INDEX);
        try (var query = handle.createQuery(SQL.EXISTS_PRIVATE_SERVER)) {
            if(query.mapTo(Long.class).one() < 1L) {
                handle.execute(SQL.INSERT_PRIVATE_SERVER); // fulfill alerts server_id constraint on private channel
            }
        }
    }

    @Override
    public Optional<ServerSettings> getServerSettings(@NotNull ClientType clientType, long serverId) {
        LOGGER.debug("getServerSettings {} {}", clientType, serverId);
        return switch (clientType) {
            case DISCORD -> findOne(SQL.SELECT_BY_DISCORD_SERVER_ID, ServerSettings.class, Map.of(DISCORD_SERVER_ID, serverId));
        };
    }

    @Override
    public void addSettings(@NotNull ServerSettings settings) {
        LOGGER.debug("addSettings {}", settings);
        requireNonNull(settings);
        update(SQL.INSERT_SERVER, query -> bindServerSettingsFields(settings, query));
    }

    @Override
    public void updateServerTimezone(@NotNull ClientType clientType, long serverId, @NotNull ZoneId timezone) {
        LOGGER.debug("updateServerTimezone {} {} {}", clientType, serverId, timezone);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_TIMEZONE_OF_DISCORD_SERVER_ID, Map.of(DISCORD_SERVER_ID, serverId, TIMEZONE, timezone));
        }
    }

    @Override
    public void updateServerSpotBotChannel(@NotNull ClientType clientType, long serverId, @NotNull String spotBotChannel) {
        LOGGER.debug("updateServerSpotBotChannel {} {} {}", clientType, serverId, spotBotChannel);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_CHANNEL_OF_DISCORD_SERVER_ID, Map.of(DISCORD_SERVER_ID, serverId, SPOTBOT_CHANNEL, spotBotChannel));
        }
    }

    @Override
    public void updateServerSpotBotRole(@NotNull ClientType clientType, long serverId, @NotNull String spotBotRole) {
        LOGGER.debug("updateServerSpotBotRole {} {} {}", clientType, serverId, spotBotRole);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_ROLE_OF_DISCORD_SERVER_ID, Map.of(DISCORD_SERVER_ID, serverId, SPOTBOT_ROLE, spotBotRole));
        }
    }

    @Override
    public void updateServerSpotBotAdminRole(@NotNull ClientType clientType, long serverId, @NotNull String spotBotAdminRole) {
        LOGGER.debug("updateServerSpotBotAdminRole {} {} {}", clientType, serverId, spotBotAdminRole);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_ADMIN_ROLE_OF_DISCORD_SERVER_ID, Map.of(DISCORD_SERVER_ID, serverId, SPOTBOT_ADMIN_ROLE, spotBotAdminRole));
        }
    }

    @Override
    public void updateLastAccess(@NotNull ClientType clientType, long serverId, @NotNull ZonedDateTime lastAccess) {
        LOGGER.debug("updateLastAccess {} {} {}", clientType, serverId, lastAccess);
        switch (clientType) {
            case DISCORD:
                update(SQL.UPDATE_LAST_ACCESS_OF_DISCORD_SERVER_ID, Map.of(DISCORD_SERVER_ID, serverId, LAST_ACCESS, lastAccess));
        }
    }

    @Override
    public long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ClientType clientType, @NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingLastAccessBeforeAndNotInAlerts {} {}", clientType, expirationDate);
        return switch (clientType) {
            case DISCORD -> update(SQL.DELETE_DISCORD_SERVERS_HAVING_LAST_ACCESS_BEFORE_AND_NOT_IN_ALERTS,
                    Map.of(EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()));
        };
    }
}
