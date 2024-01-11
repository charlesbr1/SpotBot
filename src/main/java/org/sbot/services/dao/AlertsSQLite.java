package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.alerts.Alert.Type;
import org.sbot.alerts.RangeAlert;
import org.sbot.alerts.RemainderAlert;
import org.sbot.alerts.TrendAlert;
import org.sbot.services.dao.jdbi.AbstractJDBI;
import org.sbot.services.dao.jdbi.JDBIRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.services.dao.AlertsSQLite.AlertMapper.bindFields;
import static org.sbot.utils.Dates.parseDateTime;

public final class AlertsSQLite extends AbstractJDBI implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsSQLite.class);


    private interface SQL {
        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS alerts (
                id INTEGER PRIMARY KEY,
                type TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                server_id INTEGER NOT NULL,
                exchange TEXT NOT NULL,
                pair TEXT NOT NULL,
                message TEXT NOT NULL,
                last_trigger INTEGER,
                margin TEXT NOT NULL,
                repeat INTEGER NOT NULL,
                repeat_delay INTEGER NOT NULL,
                from_price TEXT,
                to_price TEXT,
                from_date INTEGER,
                to_date INTEGER) STRICT
                """;

        String CREATE_USER_ID_INDEX = "CREATE INDEX IF NOT EXISTS alerts_user_id_index ON alerts (user_id)";
        String CREATE_SERVER_ID_INDEX = "CREATE INDEX IF NOT EXISTS alerts_server_id_index ON alerts (server_id)";
        String CREATE_PAIR_INDEX = "CREATE INDEX IF NOT EXISTS alerts_pair_index ON alerts (pair)";

        String SELECT_MAX_ID = "SELECT MAX(id) FROM alerts";
        String SELECT_BY_ID = "SELECT * FROM alerts WHERE id=:id";
        String SELECT_USER_ID_AND_SERVER_ID_AND_TYPE_BY_ID = "SELECT user_id,server_id,type FROM alerts WHERE id=:id";
        String SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_REPEATS_AND_DELAY_BEFORE_NOW_WITH_ACTIVE_RANGE = "SELECT id,type,user_id,server_id,exchange,pair,''AS message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,repeat_delay FROM alerts " +
                "WHERE exchange=:exchange AND pair=:pair AND repeat > 0 " +
                "AND (last_trigger IS NULL OR (last_trigger + (3600 * 1000 * repeat_delay)) <= (1000 * (300 + unixepoch('now', 'utc')))) " +
                "AND (type NOT LIKE 'remainder' OR (from_date < (3600 + unixepoch('now', 'utc'))) OR (from_date > (unixepoch('now', 'utc') - 3600))) " +
                "AND (type NOT LIKE 'range' OR from_date IS NULL OR (from_date < (3600 + unixepoch('now', 'utc')) AND (to_date IS NULL OR to_date > (unixepoch('now', 'utc') - 3600))))";
        String SELECT_PAIRS_BY_EXCHANGES_HAVING_REPEATS_AND_DELAY_BEFORE_NOW_WITH_ACTIVE_RANGE = "SELECT DISTINCT exchange,pair AS pair FROM alerts " +
                "WHERE repeat > 0 AND (last_trigger IS NULL OR (last_trigger + (3600 * 1000 * repeat_delay)) <= (1000 * (300 + unixepoch('now', 'utc')))) " +
                "AND (type NOT LIKE 'remainder' OR (from_date < (3600 + unixepoch('now', 'utc'))) OR (from_date > (unixepoch('now', 'utc') - 3600))) " +
                "AND (type NOT LIKE 'range' OR from_date IS NULL OR (from_date < (3600 + unixepoch('now', 'utc')) AND (to_date IS NULL OR to_date > (unixepoch('now', 'utc') - 3600))))";
        String COUNT_ALERTS_OF_USER = "SELECT COUNT(*) FROM alerts WHERE user_id=:userId";
        String ALERTS_OF_USER = "SELECT * FROM alerts WHERE user_id=:userId LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_USER_AND_TICKER_OR_PAIR = "SELECT COUNT(*) FROM alerts WHERE user_id=:userId AND pair LIKE '%:tickerOrPair%' LIMIT :limit OFFSET :offset";
        String ALERTS_OF_USER_AND_TICKER_OR_PAIR = "SELECT COUNT (*) FROM alerts WHERE user_id=:userId AND pair LIKE '%:tickerOrPair%' LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId";
        String ALERTS_OF_SERVER = "SELECT * FROM alerts WHERE server_id=:serverId LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND user_id=:userId";
        String ALERTS_OF_SERVER_AND_USER = "SELECT * FROM alerts WHERE server_id=:serverId AND user_id=:userId LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_TICKER_OR_PAIR = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND pair LIKE '%:tickerOrPair%' LIMIT :limit OFFSET :offset";
        String ALERTS_OF_SERVER_AND_TICKER_OR_PAIR = "SELECT COUNT (*) FROM alerts WHERE server_id=:serverId AND pair LIKE '%:tickerOrPair%' LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND user_id=:userId AND pair LIKE '%:tickerOrPair%' LIMIT :limit OFFSET :offset";
        String ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR = "SELECT COUNT (*) FROM alerts WHERE server_id=:serverId AND user_id=:userId AND pair LIKE '%tickerOrPair%' LIMIT :limit OFFSET :offset";
        String DELETE_BY_ID = "DELETE FROM alerts WHERE id=:id";
        String INSERT_ALERT = "INSERT INTO alerts (id,type,user_id,server_id,exchange,pair,message,from_price,to_price,from_date,to_date,last_trigger,margin,repeat,repeat_delay) VALUES (:id,:type,:userId,:serverId,:exchange,:pair,:message,:fromPrice,:toPrice,:fromDate,:toDate,:lastTrigger,:margin,:repeat,:repeatDelay)";
        String UPDATE_ALERTS_MESSAGE = "UPDATE alerts SET message=:message WHERE id=:id";
        String UPDATE_ALERTS_MARGIN = "UPDATE alerts SET margin=:margin WHERE id=:id";
        String UPDATE_ALERTS_SET_MARGIN_ZERO = "UPDATE alerts SET margin = O WHERE id=:id";
        String UPDATE_ALERTS_SET_MARGIN_ZERO_DECREMENT_REPEAT_SET_LAST_TRIGGER_NOW = "UPDATE alerts SET margin = 0, repeat = MAX(0, repeat - 1) , last_trigger = 1000 * unixepoch('now', 'utc') WHERE id=:id";
        String UPDATE_ALERTS_REPEAT = "UPDATE alerts SET repeat=:repeat WHERE id=:id";
        String UPDATE_ALERTS_REPEAT_DELAY = "UPDATE alerts SET repeat_delay=:repeatDelay WHERE id=:id";
    }

    static final class AlertMapper implements RowMapper<Alert> {

        @Override // from SQL to Alert
        public Alert map(ResultSet rs, StatementContext ctx) throws SQLException {
            Type type = Type.valueOf(rs.getString("type"));
            long id = rs.getLong("id");
            long userId = rs.getLong("user_id");
            long serverId = rs.getLong("server_id");
            String exchange = rs.getString("exchange");
            String pair = rs.getString("pair");
            String message = rs.getString("message");
            ZonedDateTime lastTrigger = parseDateTime(rs.getTimestamp("last_trigger"));
            BigDecimal margin = rs.getBigDecimal("margin");
            short repeat = rs.getShort("repeat");
            short repeatDelay = rs.getShort("repeat_delay");

            BigDecimal fromPrice = rs.getBigDecimal("from_price");
            BigDecimal toPrice = rs.getBigDecimal("to_price");
            ZonedDateTime fromDate = parseDateTime(rs.getTimestamp("from_date"));
            ZonedDateTime toDate = parseDateTime(rs.getTimestamp("to_date"));

            return switch (type) {
                case range -> new RangeAlert(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
                case trend -> new TrendAlert(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, requireNonNull(fromDate, "missing from_date on trend alert " + id), requireNonNull(toDate, "missing to_date on a trend alert " + id), lastTrigger, margin, repeat, repeatDelay);
                case remainder -> new RemainderAlert(id, userId, serverId, pair, message, requireNonNull(fromDate, "missing from_date on a remainder alert " + id), lastTrigger, margin, repeat);
            };
        }

        // from Alert to SQL
        static void bindFields(@NotNull Alert alert, @NotNull SqlStatement<?> query) {
            query.bindFields(alert); // this bind common public fields from class Alert
        }
    }

    private final AtomicLong idGenerator;

    public AlertsSQLite(@NotNull JDBIRepository repository) {
        super(repository, new AlertMapper());
        idGenerator = new AtomicLong(transactional(this::getMaxId) + 1);
        LOGGER.debug("Loading SQLite storage for alerts");
    }

    @Override
    protected void setupTable() {
        transactional(() -> {
            getHandle().execute(SQL.CREATE_TABLE);
            getHandle().execute(SQL.CREATE_USER_ID_INDEX);
            getHandle().execute(SQL.CREATE_SERVER_ID_INDEX);
            getHandle().execute(SQL.CREATE_PAIR_INDEX);
        });
    }

    public long getMaxId() {
        LOGGER.debug("getMaxId");
        return findOneLong(SQL.SELECT_MAX_ID, emptyMap())
                    .orElse(-1L); // id starts from 0, so returns -1 if no one is found
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("getAlert {}", alertId);
        return findOne(SQL.SELECT_BY_ID, Alert.class, Map.of("id", alertId));
    }

    @Override
    public Optional<UserIdServerIdType> getUserIdAndServerIdAndType(long alertId) {
        LOGGER.debug("getUserIdAndServerId {}", alertId);
        try (var query = getHandle().createQuery(SQL.SELECT_USER_ID_AND_SERVER_ID_AND_TYPE_BY_ID)) {
            return query.bind("id", alertId)
                    .map((rs, ctx) -> new UserIdServerIdType(
                                    rs.getLong("user_id"),
                                    rs.getLong("server_id"),
                                    Type.valueOf(rs.getString("type"))))
                    .findOne();
        }
    }

    @Override
    public void fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingRepeats {} {}", exchange, pair);
        fetch(SQL.SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_REPEATS_AND_DELAY_BEFORE_NOW_WITH_ACTIVE_RANGE,
                Alert.class, Map.of("exchange", exchange, "pair", pair), alertsConsumer);
    }

    @Override
    @NotNull
    public Map<Long, String> getAlertMessages(@NotNull long[] alertIds) {
        LOGGER.debug("getAlertMessages {}", alertIds);
        try (var query = getHandle().createQuery(selectAlertIdAndMessageByIdIn(alertIds))) {
            return query.setMapKeyColumn("id")
                    .setMapValueColumn("message")
                    .collectInto(new GenericType<>() {});
        }
    }

    // can't manage to get jdbi.registerArrayType(long.class, "long") working with sqlite, to bind the alertIds long[] argument
    private static String selectAlertIdAndMessageByIdIn(long[] alertIds) {
        String sql = "SELECT id,message FROM alerts WHERE id IN (";
        return LongStream.of(alertIds).collect(
                () -> new StringBuilder(sql.length() + 1 + (alertIds.length * 20)),
                (sb, value) -> sb.append(sb.isEmpty() ? "," : sql).append(value),
                StringBuilder::append).append(')').toString();
    }

    @Override
    @NotNull
    public Map<String, List<String>> getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange() {
        LOGGER.debug("getPairsByExchanges");
        try (var query = getHandle().createQuery(SQL.SELECT_PAIRS_BY_EXCHANGES_HAVING_REPEATS_AND_DELAY_BEFORE_NOW_WITH_ACTIVE_RANGE)) {
            return query.collectRows(groupingBy(
                    rowView -> rowView.getColumn("exchange", String.class),
                    mapping(rowView -> rowView.getColumn("pair", String.class), toList())));
        }
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        return queryOneLong(SQL.COUNT_ALERTS_OF_USER, Map.of("userId", userId));
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {}", userId, tickerOrPair);
        return queryOneLong(SQL.COUNT_ALERTS_OF_USER_AND_TICKER_OR_PAIR,
                Map.of("tickerOrPair", tickerOrPair, "userId", userId));
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER, Map.of("serverId", serverId));
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER_AND_USER,
                Map.of("serverId", serverId, "userId", userId));
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {}", serverId, tickerOrPair);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER_AND_TICKER_OR_PAIR,
                Map.of("tickerOrPair", tickerOrPair, "serverId", serverId));
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {}", serverId, userId, tickerOrPair);
        return queryOneLong(SQL.COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR,
                Map.of("tickerOrPair", tickerOrPair, "serverId", serverId, "userId", userId));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUser(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUser {} {} {}", userId, offset, limit);
        return query(SQL.ALERTS_OF_USER, Alert.class,
                Map.of("userId", userId, "offset", offset, "limit", limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfUserAndTickers {} {} {} {}", userId, offset, limit, tickerOrPair);
        return query(SQL.ALERTS_OF_USER_AND_TICKER_OR_PAIR, Alert.class,
                Map.of("tickerOrPair", tickerOrPair, "userId", userId, "offset", offset, "limit", limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServer(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServer {} {} {}", serverId, offset, limit);
        return query(SQL.ALERTS_OF_SERVER, Alert.class,
                Map.of("serverId", serverId, "offset", offset, "limit", limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUser {} {} {} {}", serverId, userId, offset, limit);
        return query(SQL.ALERTS_OF_SERVER_AND_USER, Alert.class,
                Map.of("serverId", serverId, "userId", userId, "offset", offset, "limit", limit));

    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndTickers {} {} {} {}", serverId, offset, limit, tickerOrPair);
        return query(SQL.ALERTS_OF_SERVER_AND_TICKER_OR_PAIR, Alert.class,
                Map.of("tickerOrPair", tickerOrPair, "serverId", serverId, "offset", offset, "limit", limit));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickers {} {} {} {} {}", serverId, userId, offset, limit, tickerOrPair);
        return query(SQL.ALERTS_OF_SERVER_AND_USER_AND_TICKER_OR_PAIR, Alert.class,
                Map.of("tickerOrPair", tickerOrPair, "serverId", serverId, "userId", userId, "offset", offset, "limit", limit));
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        var alertWithId = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}, with new id {}", alert, alertWithId.id);
        update(SQL.INSERT_ALERT, query -> bindFields(alertWithId, query));
        return alertWithId.id;
    }

    @Override
    public void updateMessage(long alertId, @NotNull String message) {
        LOGGER.debug("updateMessage {} {}", alertId, message);
        update(SQL.UPDATE_ALERTS_MESSAGE,
                Map.of("id", alertId, "message", message));
    }

    @Override
    public void updateMargin(long alertId, @NotNull BigDecimal margin) {
        LOGGER.debug("updateMargin {} {}", alertId, margin);
        update(SQL.UPDATE_ALERTS_MARGIN,
                Map.of("id", alertId, "margin", margin));
    }

    @Override
    public void updateRepeat(long alertId, short repeat) {
        LOGGER.debug("updateRepeat {} {}", alertId, repeat);
        update(SQL.UPDATE_ALERTS_REPEAT,
                Map.of("id", alertId, "repeat", repeat));
    }

    @Override
    public void updateRepeatDelay(long alertId, short repeatDelay) {
        LOGGER.debug("updateRepeatDelay {} {}", alertId, repeatDelay);
        update(SQL.UPDATE_ALERTS_REPEAT_DELAY,
                Map.of("id", alertId, "repeatDelay", repeatDelay));
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        update(SQL.DELETE_BY_ID, Map.of("id", alertId));
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull Consumer<MatchingAlertUpdater> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        batchUpdates(updater, SQL.UPDATE_ALERTS_SET_MARGIN_ZERO_DECREMENT_REPEAT_SET_LAST_TRIGGER_NOW);
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull Consumer<MatchingAlertUpdater> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        batchUpdates(updater, SQL.UPDATE_ALERTS_SET_MARGIN_ZERO);
    }

    @Override
    public void matchedRemainderAlertBatchDeletes(@NotNull Consumer<MatchingAlertUpdater> deleter) {
        LOGGER.debug("matchedRemainderAlertBatchDeletes");
        batchUpdates(deleter, SQL.DELETE_BY_ID);
    }

    private void batchUpdates(@NotNull Consumer<MatchingAlertUpdater> updater, @NotNull String sql) {
        PreparedBatch[] batch = new PreparedBatch[1];
        try {
            updater.accept(id -> (null != batch[0] ? batch[0] :
                    (batch[0] = getHandle().prepareBatch(sql)))
                    .bind("id", id).add());
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::execute);
        } finally {
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::close);
        }
    }
}
