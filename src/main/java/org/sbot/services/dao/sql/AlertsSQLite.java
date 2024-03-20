package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.entities.alerts.RemainderAlert;
import org.sbot.entities.alerts.TrendAlert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.entities.alerts.Alert.NEW_ALERT_ID;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.*;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.Fields.*;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;
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
        String NEW_SERVER_ID_ARGUMENT = "newServerId";
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
        String SELECT_ID_MESSAGE_HAVING_ID_IN = "SELECT id,message FROM alerts WHERE id IN (<ids>)";
        String SELECT_USER_ID_BY_SERVER_ID = "SELECT user_id FROM alerts WHERE server_id=:server_id";

        String PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "listening_date NOT NULL AND listening_date<=(:nowMs+1000) AND " +
                "(type NOT LIKE 'remainder' OR (from_date<(:nowMs+:periodMs))) AND " +
                "(type NOT LIKE 'range' OR (to_date IS NULL OR (to_date>:nowMs)))";
        String SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "SELECT id,type,user_id,server_id,creation_date,listening_date,exchange,pair,''AS message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze FROM alerts " +
                "WHERE exchange=:exchange AND pair=:pair AND " + PAST_LISTENING_DATE_WITH_ACTIVE_RANGE;

        String SELECT_WITHOUT_MESSAGE_HAVING_REPEAT_NEGATIVE_AND_LAST_TRIGGER_BEFORE_OR_NULL_AND_CREATION_BEFORE = "SELECT id,type,user_id,server_id,creation_date,listening_date,exchange,pair,''AS message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze FROM alerts WHERE repeat<0 AND ((last_trigger IS NOT NULL AND last_trigger<:expirationDate) OR (last_trigger IS NULL AND creation_date<:expirationDate))";
        String SELECT_WITHOUT_MESSAGE_BY_TYPE_HAVING_TO_DATE_BEFORE = "SELECT id,type,user_id,server_id,creation_date,listening_date,exchange,pair,''AS message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze FROM alerts WHERE type LIKE :type AND to_date IS NOT NULL AND to_date<:expirationDate";
        String SELECT_PAIRS_EXCHANGES_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "SELECT DISTINCT exchange,pair FROM alerts WHERE " + PAST_LISTENING_DATE_WITH_ACTIVE_RANGE;
        String COUNT_ALERTS_OF_SELECTION = "SELECT COUNT(*) FROM alerts WHERE ";
        String ALERTS_OF_SELECTION = "SELECT * FROM alerts WHERE ";
        String ORDER_BY_PAIR_USER_ID_ID_WITH_OFFSET_LIMIT = " ORDER BY pair,user_id,id LIMIT :limit OFFSET :offset";
        String ORDER_BY_PAIR_ID_WITH_OFFSET_LIMIT = " ORDER BY pair,id LIMIT :limit OFFSET :offset";
        String DELETE_BY_ID = "DELETE FROM alerts WHERE id=:id";
        String DELETE_BY_SELECTION = "DELETE FROM alerts WHERE ";
        String INSERT_ALERT_FIELDS_MAPPING = "INSERT INTO alerts (id,type,user_id,server_id,creation_date,listening_date,exchange,pair,message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze) " +
                // using class field names arguments (like userId and not user_id), for direct alert mapping using query.bindFields
                "VALUES (:id,:type,:userId,:serverId,:creationDate,:listeningDate,:exchange,:pair,:message,:fromPrice,:toPrice,:fromDate,:toDate,:lastTrigger,:margin,:repeat,:snooze)";
        String UPDATE_ALERT_FIELDS_BY_ID = "UPDATE alerts SET {} WHERE id=:id";
        String UPDATE_ALERTS_SERVER_ID_OF_SELECTION = "UPDATE alerts SET server_id=:newServerId WHERE ";
        String UPDATE_ALERT_SET_LAST_TRIGGER_MARGIN_ZERO = "UPDATE alerts SET last_trigger=:last_trigger,margin=0 WHERE id=:id";
        String UPDATE_ALERT_SET_LISTENING_DATE_LAST_TRIGGER_NOW_MARGIN_ZERO_DECREMENT_REPEAT = "UPDATE alerts SET margin=0,last_trigger=:nowMs,listening_date=CASE WHEN repeat>0 THEN (3600000*snooze)+:nowMs ELSE null END,repeat=repeat-1 WHERE id=:id";
    }

    // from jdbi SQL to Alert
    public static final class AlertMapper implements RowMapper<Alert> {
        @Override
        public Alert map(ResultSet rs, StatementContext ctx) throws SQLException {
            var type = Type.valueOf(rs.getString(TYPE));
            long id = rs.getLong(ID);
            long userId = rs.getLong(USER_ID);
            long serverId = rs.getLong(SERVER_ID);
            var creationDate = parseUtcDateTime(rs.getTimestamp(CREATION_DATE))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field alert creation_date"));
            var listeningDate = parseUtcDateTimeOrNull(rs.getTimestamp(LISTENING_DATE));
            var exchange = rs.getString(EXCHANGE);
            var pair = rs.getString(PAIR);
            var message = rs.getString(MESSAGE);
            var fromDate = parseUtcDateTimeOrNull(rs.getTimestamp(FROM_DATE));
            var lastTrigger = parseUtcDateTimeOrNull(rs.getTimestamp(LAST_TRIGGER));
            short repeat = rs.getShort(REPEAT);
            short snooze = rs.getShort(SNOOZE);

            if(remainder == type) {
                return new RemainderAlert(id, userId, serverId, creationDate, listeningDate, pair, message, requireNonNull(fromDate, "missing from_date on a remainder alert " + id), lastTrigger, repeat, snooze);
            }
            var fromPrice = rs.getBigDecimal(FROM_PRICE);
            var toPrice = rs.getBigDecimal(TO_PRICE);
            var toDate = parseUtcDateTimeOrNull(rs.getTimestamp(TO_DATE));
            var margin = rs.getBigDecimal(MARGIN);

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

    @NotNull
    static Map<String, Object> parametersOf(@NotNull SelectionFilter filter) {
        var parameters = new HashMap<String, Object>();
        if(null != filter.serverId()) parameters.put(SERVER_ID, filter.serverId());
        if(null != filter.userId()) parameters.put(USER_ID, filter.userId());
        if(null != filter.type()) parameters.put(TYPE, filter.type().name());
        if(null != filter.tickerOrPair()) parameters.put(TICKER_OR_PAIR_ARGUMENT, filter.tickerOrPair());
        return parameters;
    }

    @NotNull
    static CharSequence asSearchFilter(@NotNull SelectionFilter filter) {
        var builder = new StringBuilder(64);
        Supplier<StringBuilder> andSeparator = () -> builder.append(builder.isEmpty() ? "" : " AND ");
        if(null != filter.serverId())
            builder.append(SERVER_ID).append("=:").append(SERVER_ID);
        if(null != filter.userId())
            andSeparator.get().append(USER_ID).append("=:").append(USER_ID);
        if(null != filter.type())
            andSeparator.get().append(TYPE).append(" LIKE :").append(TYPE);
        if(null != filter.tickerOrPair())
            andSeparator.get().append(PAIR).append(" LIKE '%'||:").append(TICKER_OR_PAIR_ARGUMENT).append("||'%'");
        return builder.isEmpty() ? "1 = 1" : builder;
    }

    @NotNull
    static Map<String, Object> updateParametersOf(@NotNull Alert alert, @NotNull Set<UpdateField> fields) {
        requireNonNull(alert);
        var parameters = new HashMap<String, Object>();
        fields.forEach(field -> {
            switch (field) {
                case SERVER_ID ->       parameters.put(SERVER_ID, alert.serverId);
                case LISTENING_DATE ->  parameters.put(LISTENING_DATE, alert.listeningDate);
                case FROM_PRICE ->      parameters.put(FROM_PRICE, alert.fromPrice);
                case TO_PRICE ->        parameters.put(TO_PRICE, alert.toPrice);
                case FROM_DATE ->       parameters.put(FROM_DATE, alert.fromDate);
                case TO_DATE ->         parameters.put(TO_DATE, alert.toDate);
                case MESSAGE ->         parameters.put(MESSAGE, alert.message);
                case MARGIN ->          parameters.put(MARGIN, alert.margin);
                case REPEAT ->          parameters.put(REPEAT, alert.repeat);
                case SNOOZE ->          parameters.put(SNOOZE, alert.snooze);
            }
        });
        return parameters;
    }

    @NotNull
    static CharSequence asUpdateQuery(@NotNull Collection<String> fields) {
        if(fields.isEmpty()) {
            throw new IllegalArgumentException("Empty update parameters values");
        }
        var builder = new StringBuilder(fields.size() * 24);
        Supplier<StringBuilder> separator = () -> builder.append(builder.isEmpty() ? "" : ",");
        fields.forEach(field -> separator.get().append(field).append("=:").append(field));
        return builder;
    }

    @Override
    public long fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange {} {} {} {}", exchange, pair, now, checkPeriodMin);
        Long nowMs = now.toInstant().toEpochMilli();
        return fetch(SQL.SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE,
                Alert.class, Map.of(EXCHANGE, exchange, PAIR, pair, NOW_MS_ARGUMENT, nowMs, PERIOD_MS_ARGUMENT, 60_000L * Math.ceilDiv(requirePositive(checkPeriodMin), 2)), alertsConsumer);
    }

    @Override
    public long fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore {}", expirationDate);
        return fetch(SQL.SELECT_WITHOUT_MESSAGE_HAVING_REPEAT_NEGATIVE_AND_LAST_TRIGGER_BEFORE_OR_NULL_AND_CREATION_BEFORE, Alert.class,
                Map.of(EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()), alertsConsumer);
    }

    @Override
    public long fetchAlertsWithoutMessageByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByTypeHavingToDateBefore {} {}", type, expirationDate);
        return fetch(SQL.SELECT_WITHOUT_MESSAGE_BY_TYPE_HAVING_TO_DATE_BEFORE, Alert.class,
                Map.of(TYPE, type, EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()), alertsConsumer);
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
    @NotNull
    public List<Long> getUserIdsByServerId(long serverId) {
        LOGGER.debug("getUserIdsByServerId {}", serverId);
        return query(SQL.SELECT_USER_ID_BY_SERVER_ID, Long.class, Map.of(SERVER_ID, serverId));
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("getAlert {}", alertId);
        return findOne(SQL.SELECT_BY_ID, Alert.class, Map.of(ID, alertId));
    }

    @Override
    public Optional<Alert> getAlertWithoutMessage(long alertId) {
        LOGGER.debug("getAlertWithoutMessage {}", alertId);
        return findOne(SQL.SELECT_WITHOUT_MESSAGE_BY_ID, Alert.class, Map.of(ID, alertId));
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
    public long countAlerts(@NotNull SelectionFilter filter) {
        LOGGER.debug("countAlerts {}", filter);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SELECTION + asSearchFilter(filter), parametersOf(filter));
    }

    @NotNull
    @Override
    public List<Alert> getAlertsOrderByPairUserIdId(@NotNull SelectionFilter filter, long offset, long limit) {
        LOGGER.debug("getAlertsOrderByPairUserIdId {} {} {}", filter, offset, limit);
        var parameters = parametersOf(filter);
        parameters.put(OFFSET_ARGUMENT, requirePositive(offset));
        parameters.put(LIMIT_ARGUMENT, requireStrictlyPositive(limit));
        String sql = SQL.ALERTS_OF_SELECTION + asSearchFilter(filter);
        sql += null != filter.userId() ? ORDER_BY_PAIR_ID_WITH_OFFSET_LIMIT : ORDER_BY_PAIR_USER_ID_ID_WITH_OFFSET_LIMIT;
        return query(sql, Alert.class, parameters);
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        if(NEW_ALERT_ID != alert.id) {
            throw new IllegalArgumentException("Alert id must be new (0) : " + alert);
        }
        var alertWithId = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}", alert);
        update(SQL.INSERT_ALERT_FIELDS_MAPPING, query -> bindAlertFields(alertWithId, query));
        return alertWithId.id;
    }

    @Override
    public void update(@NotNull Alert alert, @NotNull Set<UpdateField> fields) {
        LOGGER.debug("update {} {}", fields, alert);
        var parameters = updateParametersOf(alert, fields);
        String sql = SQL.UPDATE_ALERT_FIELDS_BY_ID.replace("{}", asUpdateQuery(parameters.keySet()));
        parameters.put(ID, alert.id);
        update(sql, parameters);
    }

    @Override
    public long updateServerIdOf(@NotNull SelectionFilter filter, long newServerId) {
        LOGGER.debug("updateServerIdOf {} {}", filter, newServerId);
        var parameters = parametersOf(filter);
        parameters.put(NEW_SERVER_ID_ARGUMENT, newServerId);
        return update(SQL.UPDATE_ALERTS_SERVER_ID_OF_SELECTION + asSearchFilter(filter), parameters);
    }

    @Override
    public void delete(long alertId) {
        LOGGER.debug("delete {}", alertId);
        update(SQL.DELETE_BY_ID, Map.of(ID, alertId));
    }

    @Override
    public long delete(@NotNull SelectionFilter filter) {
        LOGGER.debug("delete {}", filter);
        return update(SQL.DELETE_BY_SELECTION + asSearchFilter(filter), parametersOf(filter));
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        Long nowMs = now.toInstant().toEpochMilli();
        batchUpdates(updater, SQL.UPDATE_ALERT_SET_LISTENING_DATE_LAST_TRIGGER_NOW_MARGIN_ZERO_DECREMENT_REPEAT, Map.of(NOW_MS_ARGUMENT, nowMs));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        Long nowMs = now.toInstant().toEpochMilli();
        batchUpdates(updater, SQL.UPDATE_ALERT_SET_LAST_TRIGGER_MARGIN_ZERO, Map.of(LAST_TRIGGER, nowMs));
    }

    @Override
    public void delete(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("batchDelete");
        batchUpdates(deleter, SQL.DELETE_BY_ID, emptyMap());
    }
}
