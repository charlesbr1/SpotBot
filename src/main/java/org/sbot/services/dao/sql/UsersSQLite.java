package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.User;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.sbot.services.dao.sql.UsersSQLite.SQL.EXPIRATION_DATE_ARGUMENT;
import static org.sbot.services.dao.sql.UsersSQLite.SQL.Fields.*;
import static org.sbot.utils.Dates.parseUtcDateTime;

public class UsersSQLite extends AbstractJDBI implements UsersDao {

    private static final Logger LOGGER = LogManager.getLogger(UsersSQLite.class);

    interface SQL {

        interface Fields {
            String ID = "id";
            String LOCALE = "locale";
            String TIMEZONE = "timezone";
            String LAST_ACCESS = "last_access";
        }

        String EXPIRATION_DATE_ARGUMENT = "expirationDate";

        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY,
                locale TEXT NOT NULL,
                timezone TEXT,
                last_access INTEGER NOT NULL) STRICT
                """;

        String SELECT_BY_ID = "SELECT * FROM users WHERE id=:id";
        String COUNT_BY_ID = "SELECT count(*) FROM users WHERE id=:id";
        String SELECT_ID_LOCALE_HAVING_ID_IN = "SELECT id,locale FROM users WHERE id IN (<ids>)";
        String INSERT_USER = "INSERT INTO users (id,locale,timezone,last_access) VALUES (:id,:locale,:timezone,:last_access)";
        String UPDATE_LOCALE = "UPDATE users SET locale=:locale WHERE id=:id";
        String UPDATE_TIMEZONE = "UPDATE users SET timezone=:timezone WHERE id=:id";
        String UPDATE_LAST_ACCESS = "UPDATE users SET last_access=:last_access WHERE id=:id";
        String DELETE_HAVING_LAST_ACCESS_BEFORE_AND_NOT_IN_ALERTS = "DELETE FROM users WHERE last_access<:expirationDate AND id NOT IN (SELECT a.user_id FROM alerts a)";
    }

    public static final class UserMapper implements RowMapper<User> {
        @Override
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            long userId = rs.getLong(ID);
            Locale locale = Optional.ofNullable(rs.getString(LOCALE)).map(Locale::forLanguageTag)
                    .orElseThrow(() -> new IllegalArgumentException("Missing field user locale"));
            ZoneId timezone = Optional.ofNullable(rs.getString(TIMEZONE)).map(ZoneId::of).orElse(null);
            ZonedDateTime lastAccess = parseUtcDateTime(rs.getTimestamp(LAST_ACCESS))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field user last_access"));
            return new User(userId, locale, timezone, lastAccess);
        }
    }

    private static void bindUserFields(@NotNull User user, @NotNull SqlStatement<?> query) {
        query.bind(ID, user.id());
        query.bind(LOCALE, user.locale());
        query.bind(TIMEZONE, user.timeZone());
        query.bind(LAST_ACCESS, user.lastAccess());
    }

    public UsersSQLite(@NotNull JDBIRepository repository) {
        super(repository, new UserMapper());
        LOGGER.debug("Loading SQLite storage for users");
    }

    UsersSQLite(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
        super(abstractJDBI, transactionHandler);
    }

    @Override
    public UsersSQLite withHandler(@NotNull JDBITransactionHandler transactionHandler) {
        return new UsersSQLite(this, transactionHandler);
    }

    @Override
    protected void setupTable(@NotNull Handle handle) {
        handle.execute(SQL.CREATE_TABLE);
    }

    @Override
    public Optional<User> getUser(long userId) {
        LOGGER.debug("getUser {}", userId);
        return findOne(SQL.SELECT_BY_ID, User.class, Map.of(ID, userId));
    }

    @Override
    public boolean userExists(long userId) {
        LOGGER.debug("userExists {}", userId);
        return 0L < queryOneLong(SQL.COUNT_BY_ID, Map.of(ID, userId));
    }

    @Override
    @NotNull
    public Map<Long, Locale> getLocales(@NotNull LongStream userIds) {
        var userIdList = userIds.boxed().toList();
        LOGGER.debug("getLocales {}", userIdList);
        if(userIdList.isEmpty()) {
            return emptyMap();
        }
        return queryMap(SQL.SELECT_ID_LOCALE_HAVING_ID_IN, new GenericType<>() {}, query -> query.bindList("ids", userIdList), ID, LOCALE);
    }

    @Override
    public void setUser(@NotNull User user) {
        LOGGER.debug("setUser {}", user);
        requireNonNull(user);
        update(SQL.INSERT_USER, query -> bindUserFields(user, query));
    }

    @Override
    public void updateLocale(long userId, @NotNull Locale locale) {
        LOGGER.debug("updateLocale {} {}", userId, locale);
        update(SQL.UPDATE_LOCALE, Map.of(ID, userId, LOCALE, locale));
    }

    @Override
    public void updateTimezone(long userId, @NotNull ZoneId timezone) {
        LOGGER.debug("updateTimezone {} {}", userId, timezone);
        update(SQL.UPDATE_TIMEZONE, Map.of(ID, userId, TIMEZONE, timezone));
    }

    @Override
    public void updateLastAccess(long userId, @NotNull ZonedDateTime lastAccess) {
        LOGGER.debug("updateLastAccess {} {}", userId, lastAccess);
        update(SQL.UPDATE_LAST_ACCESS, Map.of(ID, userId, LAST_ACCESS, lastAccess));
    }

    @Override
    public long deleteHavingLastAccessBeforeAndNotInAlerts(@NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingLastAccessBeforeAndNotInAlerts {}", expirationDate);
        return update(SQL.DELETE_HAVING_LAST_ACCESS_BEFORE_AND_NOT_IN_ALERTS,
                Map.of(EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()));
    }
}
