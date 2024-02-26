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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
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

        String SELECT_HAVING_REPEAT_ZERO_AND_LAST_TRIGGER_BEFORE_OR_NULL_AND_CREATION_BEFORE = "SELECT * FROM alerts WHERE repeat<=0 AND ((last_trigger IS NOT NULL AND last_trigger<:expirationDate) OR (last_trigger IS NULL AND creation_date<:expirationDate))";
        String SELECT_BY_TYPE_HAVING_TO_DATE_BEFORE = "SELECT * FROM alerts WHERE type LIKE :type AND to_date IS NOT NULL AND to_date<:expirationDate";
        String SELECT_PAIRS_EXCHANGES_HAVING_PAST_LISTENING_DATE_WITH_ACTIVE_RANGE =
                "SELECT DISTINCT exchange,pair FROM alerts WHERE " + PAST_LISTENING_DATE_WITH_ACTIVE_RANGE;
        String COUNT_ALERTS_OF_SELECTION = "SELECT COUNT(*) FROM alerts WHERE ";
        String ALERTS_OF_SELECTION = "SELECT * FROM alerts WHERE ";
        String ORDER_BY_PAIR_USER_ID_ID_WITH_OFFSET_LIMIT = " ORDER BY pair,user_id,id LIMIT :limit OFFSET :offset";
        String ORDER_BY_PAIR_ID_WITH_OFFSET_LIMIT = " ORDER BY pair,id LIMIT :limit OFFSET :offset";
        String DELETE_BY_ID = "DELETE FROM alerts WHERE id=:id";
        String DELETE_BY_SELECTION = "DELETE FROM alerts WHERE ";
        String INSERT_ALERT_FIELDS_MAPPING = "INSERT INTO alerts (id,type,user_id,server_id,creation_date,listening_date,exchange,pair,message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,snooze) " +
                // using class field names arguments (userId and not user_id), for direct alert mapping using query.bindFields
                "VALUES (:id,:type,:userId,:serverId,:creationDate,:listeningDate,:exchange,:pair,:message,:fromPrice,:toPrice,:fromDate,:toDate,:lastTrigger,:margin,:repeat,:snooze)";
        String UPDATE_ALERT_FIELDS_BY_ID = "UPDATE alerts SET {} WHERE id=:id";
        String UPDATE_ALERTS_SERVER_ID_OF_SELECTION = "UPDATE alerts SET server_id=:newServerId WHERE ";
        String UPDATE_ALERT_SET_LAST_TRIGGER_MARGIN_ZERO = "UPDATE alerts SET last_trigger=:last_trigger,margin=0 WHERE id=:id";
        String UPDATE_ALERT_SET_MARGIN_ZERO_DECREMENT_REPEAT_LISTENING_DATE_LAST_TRIGGER_NOW = "UPDATE alerts SET margin=0,last_trigger=:nowMs,listening_date=CASE WHEN repeat>1 THEN (3600000*snooze)+:nowMs ELSE null END,repeat=MAX(0,repeat-1) WHERE id=:id";
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

    @NotNull
    static Map<String, Object> selectionFilter(@NotNull SelectionFilter filter) {
        var selection = LinkedHashMap.<String, Object>newLinkedHashMap(6);
        BiConsumer<String, Object> put = (name, value) -> {
            if (null != value) {
                selection.put(name, value);
            }
        };
        put.accept(SERVER_ID, filter.serverId());
        put.accept(USER_ID, filter.userId());
        put.accept(TYPE, Optional.ofNullable(filter.type()).map(Type::name).orElse(null));
        put.accept(TICKER_OR_PAIR_ARGUMENT, filter.tickerOrPair());
        return selection;
    }

    @NotNull
    static String searchFilter(@NotNull Map<String, Object> selection) {
        if(selection.isEmpty()) {
            return "1 = 1"; // no op filter
        }
        return selection.entrySet().stream()
                .map(entry -> {
                    if(TICKER_OR_PAIR_ARGUMENT.equals(entry.getKey())) {
                        return PAIR + " LIKE '%'||:" + TICKER_OR_PAIR_ARGUMENT + "||'%'";
                    }
                    return entry.getValue() instanceof CharSequence ?
                            entry.getKey() + " LIKE :" + entry.getKey() :
                            entry.getKey() + "=:" + entry.getKey();
                })
                .collect(joining(" AND "));
    }

    @NotNull
    static Map<String, Object> updateParameters(@NotNull Alert alert, @NotNull Set<UpdateField> fields) {
        requireNonNull(alert);
        var map = HashMap.<String, Object>newHashMap(10);
        fields.stream().map(field -> switch (field) {
            case SERVER_ID ->  entry(SERVER_ID, alert.serverId);
            case LISTENING_DATE ->  new SimpleImmutableEntry<>(LISTENING_DATE, alert.listeningDate);
            case FROM_PRICE ->  entry(FROM_PRICE, alert.fromPrice);
            case TO_PRICE ->  entry(TO_PRICE, alert.toPrice);
            case FROM_DATE ->  new SimpleImmutableEntry<>(FROM_DATE, alert.fromDate);
            case TO_DATE ->  new SimpleImmutableEntry<>(TO_DATE, alert.toDate);
            case MESSAGE ->  entry(MESSAGE, alert.message);
            case MARGIN ->  entry(MARGIN, alert.margin);
            case REPEAT ->  entry(REPEAT, alert.repeat);
            case SNOOZE ->  entry(SNOOZE, alert.snooze);
        }).forEach(entry -> map.put(entry.getKey(), entry.getValue())); // this accepts entries with null value
        return map;
    }

    @NotNull
    static String updateQuery(@NotNull Collection<String> fields) {
        if(fields.isEmpty()) {
            throw new IllegalArgumentException("Empty update parameters map");
        }
        return fields.stream().map(field -> field + "=:" + field).collect(joining(","));
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
        var selection = selectionFilter(filter);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SELECTION + searchFilter(selection), selection);
    }

    @NotNull
    @Override
    public List<Alert> getAlertsOrderByPairUserIdId(@NotNull SelectionFilter filter, long offset, long limit) {
        LOGGER.debug("getAlertsOrderByPairUserIdId {} {} {}", filter, offset, limit);
        var selection = selectionFilter(filter);
        String sql = SQL.ALERTS_OF_SELECTION + searchFilter(selection);
        selection.put(OFFSET_ARGUMENT, offset);
        selection.put(LIMIT_ARGUMENT, limit);
        sql += null != filter.userId() ? ORDER_BY_PAIR_ID_WITH_OFFSET_LIMIT : ORDER_BY_PAIR_USER_ID_ID_WITH_OFFSET_LIMIT;
        return query(sql, Alert.class, selection);
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        var alertWithId = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}, with new id {}", alert, alertWithId.id);
        update(SQL.INSERT_ALERT_FIELDS_MAPPING, query -> bindAlertFields(alertWithId, query));
        return alertWithId.id;
    }

    @Override
    public void update(@NotNull Alert alert, @NotNull Set<UpdateField> fields) {
        LOGGER.debug("update {} {}", fields, alert);
        var parameters = updateParameters(alert, fields);
        String sql = SQL.UPDATE_ALERT_FIELDS_BY_ID.replace("{}", updateQuery(parameters.keySet()));
        parameters.put(ID, alert.id);
        update(sql, parameters);
    }

    @Override
    public long updateServerIdOf(@NotNull SelectionFilter filter, long newServerId) {
        LOGGER.debug("updateServerIdOf {} {}", filter, newServerId);
        var selection = selectionFilter(filter);
        String sql = SQL.UPDATE_ALERTS_SERVER_ID_OF_SELECTION + searchFilter(selection);
        selection.put(NEW_SERVER_ID_ARGUMENT, newServerId);
        return update(sql, selection);
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        update(SQL.DELETE_BY_ID, Map.of(ID, alertId));
    }

    @Override
    public long deleteAlerts(@NotNull SelectionFilter filter) {
        LOGGER.debug("deleteAlert {}", filter);
        var selection = selectionFilter(filter);
        return update(SQL.DELETE_BY_SELECTION + searchFilter(selection), selection);
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        Long nowMs = now.toInstant().toEpochMilli();
        batchUpdates(updater, SQL.UPDATE_ALERT_SET_MARGIN_ZERO_DECREMENT_REPEAT_LISTENING_DATE_LAST_TRIGGER_NOW, Map.of(NOW_MS_ARGUMENT, nowMs));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        Long nowMs = now.toInstant().toEpochMilli();
        batchUpdates(updater, SQL.UPDATE_ALERT_SET_LAST_TRIGGER_MARGIN_ZERO, Map.of(LAST_TRIGGER, nowMs));
    }

    @Override
    public void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("matchedRemainderAlertBatchDeletes");
        batchUpdates(deleter, SQL.DELETE_BY_ID, emptyMap());
    }
}
