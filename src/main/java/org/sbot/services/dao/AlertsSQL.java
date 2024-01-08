package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.alerts.Alert.Type;
import org.sbot.alerts.RangeAlert;
import org.sbot.alerts.TrendAlert;
import org.sbot.services.dao.jdbi.JDBIRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

public class AlertsSQL extends JDBIRepository implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsSQL.class);


    private interface SQL {
        String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS alerts (" +
                "id INTEGER PRIMARY KEY," +
                "type TEXT NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "server_id INTEGER NOT NULL," +
                "exchange TEXT NOT NULL," +
                "ticker1 TEXT NOT NULL," +
                "ticker2 TEXT NOT NULL," +
                "message TEXT NOT NULL," +
                "last_trigger INTEGER," +
                "margin REAL NOT NULL," +
                "repeat INTEGER NOT NULL," +
                "repeat_delay INTEGER NOT NULL," +
                "number1 REAL," +
                "number2 REAL," +
                "instant1 INTEGER," +
                "instant2 INTEGER)";

        String CREATE_USER_ID_INDEX = "CREATE INDEX IF NOT EXISTS user_id_index " +
                "ON alerts (user_id)";

        String CREATE_SERVER_ID_INDEX = "CREATE INDEX IF NOT EXISTS server_id_index " +
                "ON alerts (server_id)";

        String SELECT_MAX_ID = "SELECT MAX(id) FROM alerts";
        String SELECT_BY_ID = "SELECT * FROM alerts WHERE id=:id";
        String SELECT_USER_ID_AND_SERVER_ID_BY_ID = "SELECT user_id,server_id FROM alerts WHERE id=:id";
        String SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_REPEATS = "SELECT id,type,user_id,server_id,exchange,ticker1,ticker2,''AS message,last_trigger,margin,repeat,repeat_delay,number1,number2,instant1,instant2 FROM alerts WHERE exchange=:exchange AND repeat > 0 AND ((:pair LIKE ticker1 || '%') OR (:pair LIKE '%' || ticker2))";
        String SELECT_ALERT_ID_AND_MESSAGE_BY_ID_IN = "SELECT id,message FROM alerts WHERE id IN (:ids)";
        String SELECT_PAIRS_BY_EXCHANGES = "SELECT DISTINCT exchange,ticker1||'/'||ticker2 AS pair FROM alerts";
        String COUNT_ALERTS_OF_USER = "SELECT COUNT(*) FROM alerts WHERE user_id=:userId";
        String ALERTS_OF_USER = "SELECT * FROM alerts WHERE user_id=:userId LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_USER_AND_TICKER = "SELECT COUNT(*) FROM alerts WHERE user_id=:userId AND (ticker1=:ticker OR ticker2=:ticker) LIMIT :limit OFFSET :offset";
        String ALERTS_OF_USER_AND_TICKER = "SELECT COUNT (*) FROM alerts WHERE user_id=:userId AND (ticker1=:ticker OR ticker2=:ticker) LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_USER_AND_PAIR = "SELECT COUNT(*) FROM alerts WHERE user_id=:userId AND ticker1=:ticker AND ticker2=:ticker2 LIMIT :limit OFFSET :offset";
        String ALERTS_OF_USER_AND_PAIR = "SELECT * FROM alerts WHERE user_id=:userId AND ticker1=:ticker AND ticker2=:ticker2 LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId";
        String ALERTS_OF_SERVER = "SELECT * FROM alerts WHERE server_id=:serverId LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND user_id=:userId";
        String ALERTS_OF_SERVER_AND_USER = "SELECT * FROM alerts WHERE server_id=:serverId AND user_id=:userId LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_TICKER = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND (ticker1=:ticker OR ticker2=:ticker) LIMIT :limit OFFSET :offset";
        String ALERTS_OF_SERVER_AND_TICKER = "SELECT COUNT (*) FROM alerts WHERE server_id=:serverId AND (ticker1=:ticker OR ticker2=:ticker) LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND user_id=:userId AND (ticker1=:ticker OR ticker2=:ticker) LIMIT :limit OFFSET :offset";
        String ALERTS_OF_SERVER_AND_USER_AND_TICKER = "SELECT COUNT (*) FROM alerts WHERE server_id=:serverId AND user_id=:userId AND (ticker1=:ticker OR ticker2=:ticker) LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_PAIR = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND ticker1=:ticker AND ticker2=:ticker2 LIMIT :limit OFFSET :offset";
        String ALERTS_OF_SERVER_AND_PAIR = "SELECT * FROM alerts WHERE server_id=:serverId AND ticker1=:ticker AND ticker2=:ticker2 LIMIT :limit OFFSET :offset";
        String COUNT_ALERTS_OF_SERVER_AND_USER_AND_PAIR = "SELECT COUNT(*) FROM alerts WHERE server_id=:serverId AND user_id=:userId AND ticker1=:ticker AND ticker2=:ticker2 LIMIT :limit OFFSET :offset";
        String ALERTS_OF_SERVER_AND_USER_AND_PAIR = "SELECT * FROM alerts WHERE server_id=:serverId AND user_id=:userId AND ticker1=:ticker AND ticker2=:ticker2 LIMIT :limit OFFSET :offset";
        String DELETE_BY_ID = "DELETE FROM alerts WHERE id=:id";
        String INSERT_ALERT = "INSERT INTO alerts (id,type,user_id,server_id,exchange,ticker1,ticker2,message,last_trigger,margin,repeat,repeat_delay,number1,number2,instant1,instant2) " +
                "VALUES (:id,:type,:userId,:serverId,:exchange,:ticker1,:ticker2,:message,:lastTrigger,:margin,:repeat,:repeatDelay,:number1,:number2,:instant1,:instant2)";
        String UPDATE_ALERTS_MESSAGE = "UPDATE alerts SET message=:message WHERE id=:id";
        String UPDATE_ALERTS_MARGIN = "UPDATE alerts SET margin=:margin WHERE id=:id";
        String UPDATE_ALERTS_REPEAT = "UPDATE alerts SET repeat=:repeat WHERE id=:id";
        String UPDATE_ALERTS_REPEAT_DELAY = "UPDATE alerts SET repeat_delay=:repeatDelay WHERE id=:id";
        String UPDATE_ALERTS_LAST_TRIGGER_MARGIN_REPEAT = "UPDATE alerts SET last_trigger=:lastTrigger,margin=:margin,repeat=:repeat WHERE id=:id";
    }

    private static final class AlertMapper implements RowMapper<Alert> {

        @Nullable
        private static ZonedDateTime parseDateTime(@Nullable Timestamp timestamp) {
            return Optional.ofNullable(timestamp)
                    .map(dateTime -> dateTime.toLocalDateTime().atZone(ZoneOffset.UTC)).orElse(null);
        }
        @Override
        public Alert map(ResultSet rs, StatementContext ctx) throws SQLException {
            Type type = Type.valueOf(rs.getString("type"));
            long id = rs.getLong("id");
            long userId = rs.getLong("user_id");
            long serverId = rs.getLong("server_id");
            String exchange = rs.getString("exchange");
            String ticker1 = rs.getString("ticker1");
            String ticker2 = rs.getString("ticker2");
            String message = rs.getString("message");
            ZonedDateTime lastTrigger = parseDateTime(rs.getTimestamp("last_trigger"));
            BigDecimal margin = rs.getBigDecimal("margin");
            short repeat = rs.getShort("repeat");
            short repeatDelay = rs.getShort("repeat_delay");

            BigDecimal number1 = rs.getBigDecimal("number1");
            BigDecimal number2 = rs.getBigDecimal("number2");
            ZonedDateTime instant1 = parseDateTime(rs.getTimestamp("instant1"));
            ZonedDateTime instant2 = parseDateTime(rs.getTimestamp("instant2"));

            return switch (type) {
                case Range -> new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, number1, number2, message, lastTrigger, margin, repeat, repeatDelay);
                case Trend -> new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, number1, requireNonNull(instant1), number2, requireNonNull(instant2), message, lastTrigger, margin, repeat, repeatDelay);
            };
        }
    }

    private final AtomicLong idGenerator;

    public AlertsSQL(@NotNull String url) {
        super(url);
        LOGGER.debug("Loading SQL storage for alerts {}", url);
        registerRowMapper(new AlertMapper());
        setupTable();
        idGenerator = new AtomicLong(transactional(this::getMaxId) + 1);
    }

    private void setupTable() {
        transactional(() -> {
            getHandle().execute(SQL.CREATE_TABLE);
            getHandle().execute(SQL.CREATE_USER_ID_INDEX);
            getHandle().execute(SQL.CREATE_SERVER_ID_INDEX);
        });
    }

    public long getMaxId() {
        LOGGER.debug("getMaxId");
        try (var query = getHandle().createQuery(SQL.SELECT_MAX_ID)) {
            return query.mapTo(Long.class).findOne()
                    .orElse(-1L); // id starts from 0, so returns -1 if no one is found
        }
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("getAlert {}", alertId);
        try (var query = getHandle().createQuery(SQL.SELECT_BY_ID)) {
            return query.bind("id", alertId)
                    .mapTo(Alert.class)
                    .findOne();
        }
    }

    @Override
    public Optional<UserIdServerId> getUserIdAndServerId(long alertId) {
        LOGGER.debug("getUserIdAndServerId {}", alertId);
        try (var query = getHandle().createQuery(SQL.SELECT_USER_ID_AND_SERVER_ID_BY_ID)) {
            return query.bind("id", alertId)
                    .map((rs, ctx) -> new UserIdServerId(
                                    rs.getLong("user_id"),
                                    rs.getLong("server_id")))
                    .findOne();
        }
    }
    @Override
    public void fetchAlertsWithoutMessageByExchangeAndPairHavingRepeats(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingRepeats {} {}", exchange, pair);
        try (var query = getHandle().createQuery(SQL.SELECT_WITHOUT_MESSAGE_BY_EXCHANGE_AND_PAIR_HAVING_REPEATS)) {
            alertsConsumer.accept(query
                    .bind("exchange", exchange)
                    .bind("pair", pair)
                    .mapTo(Alert.class).stream());
        }
    }

    @Override
    @NotNull
    public Map<Long, String> getAlertMessages(@NotNull long[] alertIds) {
        LOGGER.debug("getAlertMessages {}", alertIds);
        try (var query = getHandle().createQuery(SQL.SELECT_ALERT_ID_AND_MESSAGE_BY_ID_IN)) {
            return query.bind("ids", alertIds)
                    .setMapKeyColumn("id")
                    .setMapValueColumn("message")
                    .collectInto(new GenericType<>() {});
        }
    }

    @Override
    @NotNull
    public Map<String, List<String>> getPairsByExchanges() {
        LOGGER.debug("getPairsByExchanges");
        try (var query = getHandle().createQuery(SQL.SELECT_PAIRS_BY_EXCHANGES)) {
            return query.collectRows(groupingBy(
                    rowView -> rowView.getColumn("exchange", String.class),
                    mapping(rowView -> rowView.getColumn("pair", String.class), toList())));
        }
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        try (var query = getHandle().createQuery(SQL.COUNT_ALERTS_OF_USER)) {
            return query.bind("userId", userId)
                    .mapTo(Long.class)
                    .one();
        }
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {} {}", userId, ticker, ticker2);
        try (var query = (null != ticker2 ? getHandle().createQuery(SQL.COUNT_ALERTS_OF_USER_AND_PAIR)
                                                .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.COUNT_ALERTS_OF_USER_AND_TICKER))) {
            return query.bind("ticker", ticker)
                    .bind("userId", userId)
                    .mapTo(Long.class)
                    .one();
        }
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        try (var query = getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER)) {
            return query.bind("serverId", serverId)
                    .mapTo(Long.class)
                    .one();
        }
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        try (var query = getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_USER)) {
            return query.bind("serverId", serverId)
                    .bind("userId", userId)
                    .mapTo(Long.class)
                    .one();
        }
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {} {}", serverId, ticker, ticker2);
        try (var query = (null != ticker2 ? getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_PAIR)
                                                .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_TICKER))) {
            return query.bind("ticker", ticker)
                    .bind("serverId", serverId)
                    .mapTo(Long.class)
                    .one();
        }
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {} {}", serverId, userId, ticker, ticker2);
        try (var query = (null != ticker2 ? getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_USER_AND_PAIR)
                                                .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER))) {
            return query.bind("ticker", ticker)
                    .bind("serverId", serverId)
                    .bind("userId", userId)
                    .mapTo(Long.class)
                    .one();
        }
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUser(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUser {} {} {}", userId, offset, limit);
        try (var query = getHandle().createQuery(SQL.ALERTS_OF_USER)) {
            return query.bind("userId", userId)
                    .bind("offset", offset)
                    .bind("limit", limit).mapTo(Alert.class).list();
        }
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfUserAndTickers {} {} {} {} {}", userId, offset, limit, ticker, ticker2);
        try (var query = (null != ticker2 ? getHandle().createQuery(SQL.ALERTS_OF_USER_AND_PAIR)
                                            .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.ALERTS_OF_USER_AND_TICKER))) {
            return query.bind("ticker", ticker)
                    .bind("userId", userId)
                    .bind("offset", offset)
                    .bind("limit", limit).mapTo(Alert.class).list();
        }
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServer(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServer {} {} {}", serverId, offset, limit);
        try (var query = getHandle().createQuery(SQL.ALERTS_OF_SERVER)) {
            return query.bind("serverId", serverId)
                    .bind("offset", offset)
                    .bind("limit", limit).mapTo(Alert.class).list();
        }
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUser {} {} {} {}", serverId, userId, offset, limit);
        try (var query = getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_USER)) {
            return query.bind("serverId", serverId)
                    .bind("userId", userId)
                    .bind("offset", offset)
                    .bind("limit", limit).mapTo(Alert.class).list();
        }
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfServerAndTickers {} {} {} {} {}", serverId, offset, limit, ticker, ticker2);
        try (var query = (null != ticker2 ? getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_PAIR)
                                            .bind("ticker2", ticker2) :
                               getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_TICKER))) {
            return query.bind("ticker", ticker)
                    .bind("serverId", serverId)
                    .bind("offset", offset)
                    .bind("limit", limit).mapTo(Alert.class).list();
        }
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickers {} {} {} {} {} {}", serverId, userId, offset, limit, ticker, ticker2);
        try (var query = (null != ticker2 ? getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_USER_AND_PAIR)
                                            .bind("ticker2", ticker2) :
                               getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_USER_AND_TICKER))) {
            return query.bind("ticker", ticker)
                    .bind("serverId", serverId)
                    .bind("userId", userId)
                    .bind("offset", offset)
                    .bind("limit", limit).mapTo(Alert.class).list();
        }
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        alert = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}, with new id {}", alert, alert.id);
        try (var query = getHandle().createUpdate(SQL.INSERT_ALERT)) {
            query.bindFields(alert);
            if(alert instanceof RangeAlert) {
                query.bind("number1", ((RangeAlert) alert).low).bind("number2", ((RangeAlert) alert).high);
                query.bind("instant1", (Long) null).bind("instant2", (Long) null); // no instant fields in RangeAlert
            } else if(alert instanceof TrendAlert) {
                query.bind("number1", ((TrendAlert) alert).fromPrice).bind("number2", ((TrendAlert) alert).toPrice);
                query.bind("instant1", ((TrendAlert) alert).fromDate).bind("instant2", ((TrendAlert) alert).toDate);
            }
            query.execute();
            return alert.id;
        }
    }

    @Override
    public void updateMessage(long alertId, @NotNull String message) {
        LOGGER.debug("updateMessage {} {}", alertId, message);
        try (var query = getHandle().createUpdate(SQL.UPDATE_ALERTS_MESSAGE)) {
            query.bind("id", alertId)
                    .bind("message", message)
                    .execute();
        }
    }

    @Override
    public void updateMargin(long alertId, @NotNull BigDecimal margin) {
        LOGGER.debug("updateMargin {} {}", alertId, margin);
        try (var query = getHandle().createUpdate(SQL.UPDATE_ALERTS_MARGIN)) {
            query.bind("id", alertId)
                    .bind("margin", margin)
                    .execute();
        }
    }

    @Override
    public void updateRepeat(long alertId, short repeat) {
        LOGGER.debug("updateRepeat {} {}", alertId, repeat);
        try (var query = getHandle().createUpdate(SQL.UPDATE_ALERTS_REPEAT)) {
            query.bind("id", alertId)
                    .bind("repeat", repeat)
                    .execute();
        }
    }

    @Override
    public void updateRepeatDelay(long alertId, short repeatDelay) {
        LOGGER.debug("updateRepeatDelay {} {}", alertId, repeatDelay);
        try (var query = getHandle().createUpdate(SQL.UPDATE_ALERTS_REPEAT_DELAY)) {
            query.bind("id", alertId)
                    .bind("repeatDelay", repeatDelay)
                    .execute();
        }
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        try (var query = getHandle().createUpdate(SQL.DELETE_BY_ID)) {
            query.bind("id", alertId).execute();
        }
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull Consumer<MatchedAlertUpdater> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        PreparedBatch[] batch = new PreparedBatch[1];
        try {
            updater.accept((id, lastTrigger, margin, repeat) -> (null != batch[0] ? batch[0] : (batch[0] = getHandle().prepareBatch(SQL.UPDATE_ALERTS_LAST_TRIGGER_MARGIN_REPEAT)))
                    .bind("id", id)
                    .bind("lastTrigger", lastTrigger)
                    .bind("margin", margin)
                    .bind("repeat", repeat).add());
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::execute);
        } finally {
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::close);
        }
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull Consumer<MarginAlertUpdater> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        PreparedBatch[] batch = new PreparedBatch[1];
        try {
            updater.accept((id, margin) -> (null != batch[0] ? batch[0] : (batch[0] = getHandle().prepareBatch(SQL.UPDATE_ALERTS_MARGIN)))
                    .bind("id", id)
                    .bind("margin", margin).add());
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::execute);
        } finally {
            Optional.ofNullable(batch[0]).ifPresent(PreparedBatch::close);
        }
    }
}
