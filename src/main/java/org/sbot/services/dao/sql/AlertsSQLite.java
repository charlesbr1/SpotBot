package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.entities.alerts.RemainderAlert;
import org.sbot.entities.alerts.TrendAlert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.entities.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.*;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.Fields.*;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.parseUtcDateTime;
import static org.sbot.utils.Dates.parseUtcDateTimeOrNull;

public final class AlertsSQLite extends AbstractJDBI implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsSQLite.class);

    interface SQL {

        interface Fields {
            String ID = "id";
            String CREATION_DATE = "creation_date";
            String LISTENING_DATE = "listening_date";
            String TYPE = "type";
            String USER_ID = "user_id";
            String SERVER_ID = "server_id";
            String EXCHANGE = "exchange";
            String PAIR = "pair";
            String MESSAGE = "message";
            String LAST_TRIGGER = "last_trigger";
            String MARGIN = "margin";
            String REPEAT = "repeat";
            String SNOOZE = "snooze";
            String FROM_PRICE = "from_price";
            String TO_PRICE = "to_price";
            String FROM_DATE = "from_date";
            String TO_DATE = "to_date";
        }

        String NOW_MS_ARGUMENT = "nowMs";
        String PERIOD_MS_ARGUMENT = "periodMs";
        String EXPIRATION_DATE_ARGUMENT = "expirationDate";
        String TICKER_OR_PAIR_ARGUMENT = "tickerOrPair";
        String OFFSET_ARGUMENT = "offset";
        String LIMIT_ARGUMENT = "limit";

        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS alerts (
                id INTEGER PRIMARY KEY,
                creation_date INTEGER NOT NULL,
                listening_date INTEGER,
                type TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                server_id INTEGER NOT NULL,
                exchange TEXT NOT NULL,
                pair TEXT NOT NULL,
                message TEXT NOT NULL,
                last_trigger INTEGER,
                margin TEXT NOT NULL,
                repeat INTEGER NOT NULL,
                snooze INTEGER NOT NULL,
                from_price TEXT,
                to_price TEXT,
                from_date INTEGER,
                to_date INTEGER,
                FOREIGN KEY(user_id) REFERENCES users(id)) STRICT
                """;

        String CREATE_USER_ID_INDEX = "CREATE INDEX IF NOT EXISTS alerts_user_id_index ON alerts (user_id)";
        String CREATE_SERVER_ID_INDEX = "CREATE INDEX IF NOT EXISTS alerts_server_id_index ON alerts (server_id)";
        String CREATE_EXCHANGE_INDEX = "CREATE INDEX IF NOT EXISTS alerts_exchange_index ON alerts (exchange)";
        String CREATE_PAIR_INDEX = "CREATE INDEX IF NOT EXISTS alerts_pair_index ON alerts (pair)";

        String SELECT_MAX_ID = "SELECT MAX(id) FROM alerts";
        String SELECT_BY_ID = "SELECT * FROM alerts WHERE id=:id";
        String SELECT_WITHOUT_MESSAGE_BY_ID = "SELECT id,type,user_id,server_id,creation_date,listening_date,exchange,pair,''AS message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze FROM alerts WHERE id=:id";
        String SELECT_USER_ID_BY_SERVER_ID = "SELECT user_id FROM alerts WHERE server_id=:server_id";

        String PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "listening_date NOT NULL AND listening_date<=(:nowMs+1000) AND " +
                "(type NOT LIKE 'remainder' OR (from_date<(:nowMs+:periodMs))) AND " +
                "(type NOT LIKE 'range' OR (to_date IS NULL OR (to_date>:nowMs)))";
        String SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "SELECT id,type,user_id,server_id,creation_date,listening_date,exchange,pair,''AS message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze FROM alerts " +
                "WHERE exchange=:exchange AND pair=:pair AND " + PAST_LISTENING_DATE_WITH_ACTIVE_RANGE;

        String SELECT_HAVING_REPEAT_ZERO_AND_LAST_TRIGGER_BEFORE_OR_NULL_AND_CREATION_BEFORE = "SELECT * FROM alerts WHERE repeat<=0 AND ((last_trigger IS NOT NULL AND last_trigger<:expirationDate) OR (last_trigger IS NULL AND creation_date<:expirationDate))";
        String SELECT_BY_TYPE_HAVING_TO_DATE_BEFORE = "SELECT * FROM alerts WHERE type LIKE :type AND to_date IS NOT NULL AND to_date<:expirationDate";
        String SELECT_ID_MESSAGE_HAVING_ID_IN = "SELECT id,message FROM alerts WHERE id IN (<ids>)";
        String SELECT_PAIRS_EXCHANGES_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "SELECT DISTINCT exchange,pair FROM alerts WHERE " + PAST_LISTENING_DATE_WITH_ACTIVE_RANGE;
        String COUNT_ALERTS_OF_USER = "SELECT COUNT(*) FROM alerts WHERE user_id=:user_id";
        String ALERTS_OF_USER_ORDER_BY_PAIR_ID = "SELECT * FROM alerts WHERE user_id=:user_id ORDER BY pair,id LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_USER_AND_TICKER_OR_PAIR = "SELECT COUNT(*) FROM alerts WHERE user_id=:user_id AND pair LIKE '%'||:tickerOrPair||'%'";
        String ALERTS_OF_USER_AND_TICKER_OR_PAIR_ORDER_BY_ID = "SELECT * FROM alerts WHERE user_id=:user_id AND pair LIKE '%'||:tickerOrPair||'%' ORDER BY id LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER = "SELECT COUNT(*) FROM alerts WHERE server_id=:server_id";
        String ALERTS_OF_SERVER_ORDER_BY_PAIR_USER_ID_ID = "SELECT * FROM alerts WHERE server_id=:server_id ORDER BY pair,user_id,id LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER = "SELECT COUNT(*) FROM alerts WHERE server_id=:server_id AND user_id=:user_id";
        String ALERTS_OF_SERVER_AND_USER_ORDER_BY_PAIR_ID = "SELECT * FROM alerts WHERE server_id=:server_id AND user_id=:user_id ORDER BY pair,id LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_TICKER_OR_PAIR = "SELECT COUNT(*) FROM alerts WHERE server_id=:server_id AND pair LIKE '%'||:tickerOrPair||'%'";
        String ALERTS_OF_SERVER_AND_TICKER_OR_PAIR_ORDER_BY_USER_ID_ID = "SELECT * FROM alerts WHERE server_id=:server_id AND pair LIKE '%'||:tickerOrPair||'%' ORDER BY user_id,id LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR = "SELECT COUNT(*) FROM alerts WHERE server_id=:server_id AND user_id=:user_id AND pair LIKE '%'||:tickerOrPair||'%'";
        String ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR_ORDER_BY_ID = "SELECT * FROM alerts WHERE server_id=:server_id AND user_id=:user_id AND pair LIKE '%'||:tickerOrPair||'%' ORDER BY id LIMIT :limit OFFSET :offset";
        String DELETE_BY_ID = "DELETE FROM alerts WHERE id=:id";
        String DELETE_BY_USER_ID_AND_SERVER_ID = "DELETE FROM alerts WHERE user_id=:user_id AND server_id=:server_id";
        String DELETE_BY_USER_ID_AND_SERVER_ID_AND_TICKER_OR_PAIR = "DELETE FROM alerts WHERE user_id=:user_id AND server_id=:server_id AND pair LIKE '%'||:tickerOrPair||'%'";
        String INSERT_ALERT_FIELDS_MAPPING = "INSERT INTO alerts (id,type,user_id,server_id,creation_date,listening_date,exchange,pair,message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze) " +
                // class field names arguments (userId and not user_id), for direct alert mapping using query.bindFields
                "VALUES (:id,:type,:userId,:serverId,:creationDate,:listeningDate,:exchange,:pair,:message,:fromPrice,:toPrice,:fromDate,:toDate,:lastTrigger,:margin,:repeat,:snooze)";
        String UPDATE_ALERTS_SERVER_ID_BY_ID = "UPDATE alerts SET server_id=:server_id WHERE id=:id";
        String UPDATE_ALERTS_SERVER_ID_PRIVATE = "UPDATE alerts SET server_id=" + PRIVATE_ALERT + " WHERE server_id=:server_id";
        String UPDATE_ALERTS_SERVER_ID_BY_USER_ID_AND_SERVER_ID = "UPDATE alerts SET server_id=:newServerId WHERE user_id=:user_id AND server_id=:server_id";
        String UPDATE_ALERTS_SERVER_ID_BY_USER_ID_AND_SERVER_ID_TICKER_OR_PAIR = "UPDATE alerts SET server_id=:newServerId WHERE user_id=:user_id AND server_id=:server_id AND pair LIKE '%'||:tickerOrPair||'%'";
        String UPDATE_ALERTS_FROM_PRICE = "UPDATE alerts SET from_price=:from_price WHERE id=:id";
        String UPDATE_ALERTS_TO_PRICE = "UPDATE alerts SET to_price=:to_price WHERE id=:id";
        String UPDATE_ALERTS_FROM_DATE = "UPDATE alerts SET from_date=:from_date WHERE id=:id";
        String UPDATE_ALERTS_TO_DATE = "UPDATE alerts SET to_date=:to_date WHERE id=:id";
        String UPDATE_ALERTS_MESSAGE = "UPDATE alerts SET message=:message WHERE id=:id";
        String UPDATE_ALERTS_MARGIN = "UPDATE alerts SET margin=:margin WHERE id=:id";
        String UPDATE_ALERTS_SET_LAST_TRIGGER_MARGIN_ZERO = "UPDATE alerts SET last_trigger=:last_trigger,margin=0 WHERE id=:id";
        String UPDATE_ALERTS_SET_MARGIN_ZERO_DECREMENT_REPEAT_LISTENING_DATE_LAST_TRIGGER_NOW = "UPDATE alerts SET margin=0,last_trigger=:nowMs,listening_date=CASE WHEN repeat>1 THEN (1000*3600*snooze)+:nowMs ELSE null END,repeat=MAX(0,repeat-1) WHERE id=:id";
        String UPDATE_ALERTS_LISTENING_DATE_FROM_DATE = "UPDATE alerts SET listening_date=:listening_date,from_date=:from_date WHERE id=:id";
        String UPDATE_ALERTS_REPEAT_LISTENING_DATE = "UPDATE alerts SET repeat=:repeat,listening_date=:listening_date WHERE id=:id";
        String UPDATE_ALERTS_SNOOZE = "UPDATE alerts SET snooze=:snooze WHERE id=:id";
    }

    // from jdbi SQL to Alert
    public static final class AlertMapper implements RowMapper<Alert> {
        @Override
        public Alert map(ResultSet rs, StatementContext ctx) throws SQLException {
            Type type = Type.valueOf(rs.getString(TYPE));
            long id = rs.getLong(ID);
            long userId = rs.getLong(USER_ID);
            long serverId = rs.getLong(SERVER_ID);
            ZonedDateTime creationDate = parseUtcDateTime(rs.getTimestamp(CREATION_DATE))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field alert creation_date"));
            ZonedDateTime listeningDate = parseUtcDateTimeOrNull(rs.getTimestamp(LISTENING_DATE));
            String exchange = rs.getString(EXCHANGE);
            String pair = rs.getString(PAIR);
            String message = rs.getString(MESSAGE);
            ZonedDateTime fromDate = parseUtcDateTimeOrNull(rs.getTimestamp(FROM_DATE));

            if(remainder == type) {
                return new RemainderAlert(id, userId, serverId, creationDate, listeningDate, pair, message, requireNonNull(fromDate, "missing from_date on a remainder alert " + id));
            }
            BigDecimal fromPrice = rs.getBigDecimal(FROM_PRICE);
            BigDecimal toPrice = rs.getBigDecimal(TO_PRICE);
            ZonedDateTime toDate = parseUtcDateTimeOrNull(rs.getTimestamp(TO_DATE));

            ZonedDateTime lastTrigger = parseUtcDateTimeOrNull(rs.getTimestamp(LAST_TRIGGER));
            BigDecimal margin = rs.getBigDecimal(MARGIN);
            short repeat = rs.getShort(REPEAT);
            short snooze = rs.getShort(SNOOZE);

            return range == type ?
                    new RangeAlert(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze) :
                    new TrendAlert(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, requireNonNull(fromDate, "missing from_date on trend alert " + id), requireNonNull(toDate, "missing to_date on a trend alert " + id), lastTrigger, margin, repeat, snooze);
        }
    }

    // from Alert to jdbi SQL
    private static void bindAlertFields(@NotNull Alert alert, @NotNull SqlStatement<?> query) {
        query.bindFields(requireNonNull(alert)); // this bind common public fields from class Alert
    }

    private final AtomicLong idGenerator;

    public AlertsSQLite(@NotNull JDBIRepository repository) {
        super(repository, new AlertMapper());
        LOGGER.debug("Loading SQLite storage for alerts");
        this.idGenerator = new AtomicLong(inTransaction(this::getMaxId) + 1); // id starts from 1
    }

    AlertsSQLite(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler, @NotNull AtomicLong idGenerator) {
        super(abstractJDBI, transactionHandler);
        this.idGenerator = requireNonNull(idGenerator);
    }

    @Override
    public AlertsSQLite withHandler(@NotNull JDBITransactionHandler transactionHandler) {
        return new AlertsSQLite(this, transactionHandler, idGenerator);
    }

    @Override
    protected void setupTable(@NotNull Handle handle) {
        handle.execute(SQL.CREATE_TABLE);
        handle.execute(SQL.CREATE_USER_ID_INDEX);
        handle.execute(SQL.CREATE_SERVER_ID_INDEX);
        handle.execute(SQL.CREATE_EXCHANGE_INDEX);
        handle.execute(SQL.CREATE_PAIR_INDEX);
    }

    long getMaxId(@NotNull Handle handle) {
        LOGGER.debug("getMaxId");
        return findOneLong(handle, SQL.SELECT_MAX_ID, emptyMap()).orElse(0L);
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("getAlert {}", alertId);
        return findOne(SQL.SELECT_BY_ID, Alert.class,
                Map.of(ID, alertId));
    }

    @Override
    public Optional<Alert> getAlertWithoutMessage(long alertId) {
        LOGGER.debug("getAlertWithoutMessage {}", alertId);
        return findOne(SQL.SELECT_WITHOUT_MESSAGE_BY_ID, Alert.class,
                Map.of(ID, alertId));
    }

    @Override
    public List<Long> getUserIdsByServerId(long serverId) {
        LOGGER.debug("getUserIdsByServerId {}", serverId);
        return query(SQL.SELECT_USER_ID_BY_SERVER_ID, Long.class, Map.of(SERVER_ID, serverId));
    }

    @Override
    public long fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange {} {} {} {}", exchange, pair, now, checkPeriodMin);
        Long nowMs = now.toInstant().toEpochMilli();
        return fetch(SQL.SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE,
                Alert.class, Map.of(EXCHANGE, exchange, PAIR, pair, NOW_MS_ARGUMENT, nowMs, PERIOD_MS_ARGUMENT, 60_000L * Math.ceilDiv(requirePositive(checkPeriodMin), 2)), alertsConsumer);
    }

    @Override
    public long fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore {}", expirationDate);
        return fetch(SQL.SELECT_HAVING_REPEAT_ZERO_AND_LAST_TRIGGER_BEFORE_OR_NULL_AND_CREATION_BEFORE, Alert.class,
                Map.of(EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()), alertsConsumer);
    }

    @Override
    public long fetchAlertsByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsByTypeHavingToDateBefore {} {}", type, expirationDate);
        return fetch(SQL.SELECT_BY_TYPE_HAVING_TO_DATE_BEFORE, Alert.class,
                Map.of(TYPE, type, EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()), alertsConsumer);
    }

    @Override
    @NotNull
    public Map<Long, String> getAlertMessages(@NotNull LongStream alertIds) {
        var alertIdList = alertIds.boxed().toList();
        LOGGER.debug("getAlertMessages {}", alertIdList);
        if(alertIdList.isEmpty()) {
            return emptyMap();
        }
        return queryMap(SQL.SELECT_ID_MESSAGE_HAVING_ID_IN, new GenericType<>() {}, query -> query.bindList("ids", alertIdList), ID, MESSAGE);
    }

    @Override
    @NotNull
    public Map<String, Set<String>> getPairsByExchangesHavingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin) {
        LOGGER.debug("getPairsByExchangesHavingPastListeningDateWithActiveRange {} {}", now, checkPeriodMin);
        Long nowMs = now.toInstant().toEpochMilli();
        return queryCollect(SQL.SELECT_PAIRS_EXCHANGES_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE,
                Map.of(NOW_MS_ARGUMENT, nowMs, PERIOD_MS_ARGUMENT, 60_000L * Math.ceilDiv(requirePositive(checkPeriodMin), 2)),
                groupingBy(
                        rowView -> rowView.getColumn(EXCHANGE, String.class),
                        mapping(rowView -> rowView.getColumn(PAIR, String.class), toSet())));
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        return queryOneLong(SQL.COUNT_ALERTS_OF_USER, Map.of(USER_ID, userId));
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {}", userId, tickerOrPair);
        return queryOneLong(SQL.COUNT_ALERTS_OF_USER_AND_TICKER_OR_PAIR,
                Map.of(USER_ID, userId, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER, Map.of(SERVER_ID, serverId));
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER_AND_USER,
                Map.of(SERVER_ID, serverId, USER_ID, userId));
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {}", serverId, tickerOrPair);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER_AND_TICKER_OR_PAIR,
                Map.of(SERVER_ID, serverId, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {}", serverId, userId, tickerOrPair);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR,
                Map.of(SERVER_ID, serverId, USER_ID, userId, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserOrderByPairId(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUserOrderByPairId {} {} {}", userId, offset, limit);
        return query(SQL.ALERTS_OF_USER_ORDER_BY_PAIR_ID, Alert.class,
                Map.of(USER_ID, userId, OFFSET_ARGUMENT, offset, LIMIT_ARGUMENT, limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickersOrderById(long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfUserAndTickersOrderById {} {} {} {}", userId, offset, limit, tickerOrPair);
        return query(SQL.ALERTS_OF_USER_AND_TICKER_OR_PAIR_ORDER_BY_ID, Alert.class,
                Map.of(USER_ID, userId, OFFSET_ARGUMENT, offset, LIMIT_ARGUMENT, limit, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerOrderByPairUserIdId(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerOrderByPairUserIdId {} {} {}", serverId, offset, limit);
        return query(SQL.ALERTS_OF_SERVER_ORDER_BY_PAIR_USER_ID_ID, Alert.class,
                Map.of(SERVER_ID, serverId, OFFSET_ARGUMENT, offset, LIMIT_ARGUMENT, limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserOrderByPairId(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUserOrderByPairId {} {} {} {}", serverId, userId, offset, limit);
        return query(SQL.ALERTS_OF_SERVER_AND_USER_ORDER_BY_PAIR_ID, Alert.class,
                Map.of(SERVER_ID, serverId, USER_ID, userId, OFFSET_ARGUMENT, offset, LIMIT_ARGUMENT, limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickersOrderByUserIdId(long serverId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndTickersOrderByUserIdId {} {} {} {}", serverId, offset, limit, tickerOrPair);
        return query(SQL.ALERTS_OF_SERVER_AND_TICKER_OR_PAIR_ORDER_BY_USER_ID_ID, Alert.class,
                Map.of(SERVER_ID, serverId, OFFSET_ARGUMENT, offset, LIMIT_ARGUMENT, limit, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickersOrderById(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickersOrderById {} {} {} {} {}", serverId, userId, offset, limit, tickerOrPair);
        return query(SQL.ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR_ORDER_BY_ID, Alert.class,
                Map.of(SERVER_ID, serverId, USER_ID, userId, OFFSET_ARGUMENT, offset, LIMIT_ARGUMENT, limit, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        var alertWithId = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}, with new id {}", alert, alertWithId.id);
        update(SQL.INSERT_ALERT_FIELDS_MAPPING, query -> bindAlertFields(alertWithId, query));
        return alertWithId.id;
    }

    @Override
    public void updateServerId(long alertId, long serverId) {
        LOGGER.debug("updateServerId {} {}", alertId, serverId);
        update(SQL.UPDATE_ALERTS_SERVER_ID_BY_ID,
                Map.of(ID, alertId, SERVER_ID, serverId));
    }

    @Override
    public long updateServerIdPrivate(long serverId) {
        LOGGER.debug("updateServerIdPrivate {}", serverId);
        return update(SQL.UPDATE_ALERTS_SERVER_ID_PRIVATE,
                Map.of(SERVER_ID, serverId));
    }

    @Override
    public long updateServerIdOfUserAndServerId(long userId, long serverId, long newServerId) {
        LOGGER.debug("updateServerIdOfUserAndServerId {} {} {}", userId, serverId, newServerId);
        return update(SQL.UPDATE_ALERTS_SERVER_ID_BY_USER_ID_AND_SERVER_ID,
                Map.of(USER_ID, userId, SERVER_ID, serverId, "newServerId", newServerId));
    }

    @Override
    public long updateServerIdOfUserAndServerIdAndTickers(long userId, long serverId, @NotNull String tickerOrPair, long newServerId) {
        LOGGER.debug("updateServerIdOfUserAndServerIdAndTickers {} {} {} {}", userId, serverId, tickerOrPair, newServerId);
        return update(SQL.UPDATE_ALERTS_SERVER_ID_BY_USER_ID_AND_SERVER_ID_TICKER_OR_PAIR,
                Map.of(USER_ID, userId, SERVER_ID, serverId, "newServerId", newServerId, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    public void updateFromPrice(long alertId, @NotNull BigDecimal fromPrice) {
        LOGGER.debug("updateFromPrice {} {}", alertId, fromPrice);
        update(SQL.UPDATE_ALERTS_FROM_PRICE,
                Map.of(ID, alertId, FROM_PRICE, fromPrice));
    }

    @Override
    public void updateToPrice(long alertId, @NotNull BigDecimal toPrice) {
        LOGGER.debug("updateToPrice {} {}", alertId, toPrice);
        update(SQL.UPDATE_ALERTS_TO_PRICE,
                Map.of(ID, alertId, TO_PRICE, toPrice));
    }

    @Override
    public void updateFromDate(long alertId, @Nullable ZonedDateTime fromDate) {
        LOGGER.debug("updateFromDate {} {}", alertId, fromDate);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ID, alertId);
        parameters.put(FROM_DATE, fromDate);
        update(SQL.UPDATE_ALERTS_FROM_DATE, parameters);
    }

    @Override
    public void updateToDate(long alertId, @Nullable ZonedDateTime toDate) {
        LOGGER.debug("updateToDate {} {}", alertId, toDate);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ID, alertId);
        parameters.put(TO_DATE, toDate);
        update(SQL.UPDATE_ALERTS_TO_DATE, parameters);
    }

    @Override
    public void updateMessage(long alertId, @NotNull String message) {
        LOGGER.debug("updateMessage {} {}", alertId, message);
        update(SQL.UPDATE_ALERTS_MESSAGE,
                Map.of(ID, alertId, MESSAGE, message));
    }

    @Override
    public void updateMargin(long alertId, @NotNull BigDecimal margin) {
        LOGGER.debug("updateMargin {} {}", alertId, margin);
        update(SQL.UPDATE_ALERTS_MARGIN,
                Map.of(ID, alertId, MARGIN, margin));
    }

    @Override
    public void updateListeningDateFromDate(long alertId, @Nullable ZonedDateTime listeningDate, @Nullable ZonedDateTime fromDate) {
        LOGGER.debug("updateListeningDateFromDate {} {} {}", alertId, listeningDate, fromDate);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ID, alertId);
        parameters.put(LISTENING_DATE, listeningDate);
        parameters.put(FROM_DATE, fromDate);
        update(SQL.UPDATE_ALERTS_LISTENING_DATE_FROM_DATE, parameters);
    }

    @Override
    public void updateListeningDateRepeat(long alertId, @Nullable ZonedDateTime listeningDate, short repeat) {
        LOGGER.debug("updateListeningDateRepeat {} {} {}", alertId, listeningDate, repeat);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ID, alertId);
        parameters.put(LISTENING_DATE, listeningDate);
        parameters.put(REPEAT, repeat);
        update(SQL.UPDATE_ALERTS_REPEAT_LISTENING_DATE, parameters);
    }

    @Override
    public void updateSnooze(long alertId, short snooze) {
        LOGGER.debug("updateSnooze {} {}", alertId, snooze);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ID, alertId);
        parameters.put(SNOOZE, snooze);
        update(SQL.UPDATE_ALERTS_SNOOZE, parameters);
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        update(SQL.DELETE_BY_ID, Map.of(ID, alertId));
    }

    @Override
    public long deleteAlerts(long serverId, long userId) {
        LOGGER.debug("deleteAlerts {} {}", serverId, userId);
        return update(SQL.DELETE_BY_USER_ID_AND_SERVER_ID,
                Map.of(USER_ID, userId, SERVER_ID, serverId));
    }

    @Override
    public long deleteAlerts(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("deleteAlerts {} {} {}", serverId, userId, tickerOrPair);
        return update(SQL.DELETE_BY_USER_ID_AND_SERVER_ID_AND_TICKER_OR_PAIR,
                Map.of(USER_ID, userId, SERVER_ID, serverId, TICKER_OR_PAIR_ARGUMENT, tickerOrPair));
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        Long nowMs = now.toInstant().toEpochMilli();
        batchUpdates(updater, SQL.UPDATE_ALERTS_SET_MARGIN_ZERO_DECREMENT_REPEAT_LISTENING_DATE_LAST_TRIGGER_NOW, Map.of(NOW_MS_ARGUMENT, nowMs));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        Long nowMs = now.toInstant().toEpochMilli();
        batchUpdates(updater, SQL.UPDATE_ALERTS_SET_LAST_TRIGGER_MARGIN_ZERO, Map.of(LAST_TRIGGER, nowMs));
    }

    @Override
    public void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("matchedRemainderAlertBatchDeletes");
        batchUpdates(deleter, SQL.DELETE_BY_ID, emptyMap());
    }
}
