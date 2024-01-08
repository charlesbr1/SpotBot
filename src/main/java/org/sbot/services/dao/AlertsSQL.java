package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.services.dao.jdbi.JDBIRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class AlertsSQL extends JDBIRepository implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsSQL.class);


    private interface SQL {
        String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS alerts (" +
                "id INTEGER PRIMARY KEY," +
                "user_id INTEGER NOT NULL," +
                "server_id INTEGER NOT NULL," +
                "exchange TEXT NOT NULL," +
                "ticker1 TEXT NOT NULL," +
                "ticker2 TEXT NOT NULL," +
                "message TEXT NOT NULL," +
                "last_trigger INTEGER," +
                "margin REAL NOT NULL," +
                "repeat INTEGER NOT NULL," +
                "repeat_delay INTEGER NOT NULL)";

        String CREATE_USER_ID_INDEX = "CREATE INDEX IF NOT EXISTS user_id_index " +
                "ON alerts (user_id)";

        String CREATE_SERVER_ID_INDEX = "CREATE INDEX IF NOT EXISTS server_id_index " +
                "ON alerts (server_id)";

        String SELECT_BY_ID = "SELECT * FROM alerts WHERE id=:id";
        String SELECT_BY_EXCHANGE_AND_PAIR_HAVING_REPEATS = "SELECT * FROM alerts WHERE exchange=:exchange AND repeat > 0 AND ((:pair LIKE ticker1 || '%') OR (:pair LIKE '%' || ticker2))";
        String SELECT_PAIRS_BY_EXCHANGES = "SELECT DISTINCT exchange, ticker1 || '/' || ticker2 AS pair FROM alerts";
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
        String INSERT_ALERT = "INSERT INTO alerts (user_id,server_id,exchange,ticker1,ticker2,message,last_trigger,margin,repeat,repeatDelay) " +
                "VALUES (:userId,:serverId,:exchange,:ticker1,:ticker2,:message,:lastTrigger,:margin,:repeat,:repeatDelay)";
        String UPDATE_ALERTS_MESSAGE = "UPDATE alerts SET message=:message WHERE id=:id";
        String UPDATE_ALERTS_MARGIN = "UPDATE alerts SET margin=:margin WHERE id=:id";
        String UPDATE_ALERTS_REPEAT = "UPDATE alerts SET repeat=:repeat WHERE id=:id";
        String UPDATE_ALERTS_REPEAT_DELAY = "UPDATE alerts SET repeat_delay=:repeatDelay WHERE id=:id";
        String UPDATE_ALERTS_LAST_TRIGGER_MARGIN_REPEAT = "UPDATE alerts SET last_trigger=:lastTrigger,margin=:margin,repeat=:repeat WHERE id=:id";
    }


    public AlertsSQL(@NotNull String url) {
        super(url);
        LOGGER.debug("Loading SQL storage for alerts {}", url);
        registerRowMapper(Alert.class);
        setupTable();
    }

    private void setupTable() {
        transactional(() -> {
            getHandle().execute(SQL.CREATE_TABLE);
            getHandle().execute(SQL.CREATE_USER_ID_INDEX);
            getHandle().execute(SQL.CREATE_SERVER_ID_INDEX);
        });
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
    public void fetchAlertsByExchangeAndPairHavingRepeats(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsByExchangeAndPair {} {}", exchange, pair);
        try (var query = getHandle().createQuery(SQL.SELECT_BY_EXCHANGE_AND_PAIR_HAVING_REPEATS)) {
            alertsConsumer.accept(query
                    .bind("exchange", exchange)
                    .bind("pair", pair)
                    .mapTo(Alert.class).stream());
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
        LOGGER.debug("addAlert {}", alert);
        try (var query = getHandle().createQuery(SQL.INSERT_ALERT)) {
                return query.scanResultSet((rs, ctx) -> {
                LOGGER.debug("here");
                rs.get();
                return null;
            });
        }
/*                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .first(); //  should return new alert id
 */
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
                    .bind(repeat, repeat)
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
