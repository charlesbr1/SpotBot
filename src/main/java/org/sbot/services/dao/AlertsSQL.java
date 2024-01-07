package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.services.dao.jdbi.JDBIRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.stream.Collectors.groupingBy;
import static org.sbot.alerts.Alert.ALERT_MAX_TICKER_LENGTH;
import static org.sbot.alerts.Alert.ALERT_MESSAGE_ARG_MAX_LENGTH;

public class AlertsSQL extends JDBIRepository implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsSQL.class);

    private interface SQL {
        String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS alerts (" +
                "id INTEGER PRIMARY KEY," +
                "user_id INTEGER NOT NULL," +
                "server_id INTEGER NOT NULL," +
                "exchange varchar(16) NOT NULL," +
                "ticker1 varchar(" + ALERT_MAX_TICKER_LENGTH + ") NOT NULL," +
                "ticker2 varchar(" + ALERT_MAX_TICKER_LENGTH + ") NOT NULL," +
                "message varchar(" + ALERT_MESSAGE_ARG_MAX_LENGTH + ") NOT NULL," +
                "last_trigger DATETIME," +
                "margin NUMBER NOT NULL," +
                "repeat INTEGER NOT NULL," +
                "repeatDelay INTEGER NOT NULL)";

        String CREATE_USER_ID_INDEX = "CREATE INDEX IF NOT EXISTS user_id_index " +
                "ON alerts (user_id)";

        String CREATE_SERVER_ID_INDEX = "CREATE INDEX IF NOT EXISTS server_id_index " +
                "ON alerts (server_id)";

        String SELECT_ALL_ID_PAIR_EXCHANGE = "SELECT id, ticker1 + '/' + ticker2, exchange FROM alerts";
        String SELECT_BY_ID = "SELECT * FROM alerts WHERE id=:id";
        String SELECT_BY_IDS_IN = "SELECT * from alerts WHERE id IN (:ids)";
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
        String UPDATE_ALERTS_MARGIN = "UPDATE alerts SET margin=:margin WHERE id=:id";
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
        return getHandle().createQuery(SQL.SELECT_BY_ID)
                .bind("id", alertId)
                .mapTo(Alert.class)
                .findOne();
    }

    @Override
    @NotNull
    public List<Alert> getAlerts(List<Long> alertIds) {
        LOGGER.debug("getAlerts {}", alertIds);
        return getHandle().createQuery(SQL.SELECT_BY_IDS_IN)
                .bindList("ids", alertIds)
                .mapTo(Alert.class)
                .list();
    }

    @Override
    @NotNull
    public Map<String, Map<String, long[]>> getAlertIdsByPairAndExchange() {
        LOGGER.debug("getAlertIdsByPairAndExchange");
        getHandle().createQuery(SQL.SELECT_ALL_ID_PAIR_EXCHANGE)
                //TODO
                .mapTo(Alert.class)
                .collect(groupingBy(Alert::getExchange, groupingBy(Alert::getSlashPair)));
        return null;
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        return getHandle().createQuery(SQL.COUNT_ALERTS_OF_USER)
                .bind("userId", userId)
                .mapTo(Long.class)
                .one();
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {} {}", userId, ticker, ticker2);
        return (null != ticker2 ? getHandle().createQuery(SQL.COUNT_ALERTS_OF_USER_AND_PAIR)
                                                .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.COUNT_ALERTS_OF_USER_AND_TICKER))
                .bind("ticker", ticker)
                .bind("userId", userId)
                .mapTo(Long.class)
                .one();
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        return getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER)
                .bind("serverId", serverId)
                .mapTo(Long.class)
                .one();
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        return getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_USER)
                .bind("serverId", serverId)
                .bind("userId", userId)
                .mapTo(Long.class)
                .one();
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {} {}", serverId, ticker, ticker2);
        return (null != ticker2 ? getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_PAIR)
                                                .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_TICKER))
                .bind("ticker", ticker)
                .bind("serverId", serverId)
                .mapTo(Long.class)
                .one();
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {} {}", serverId, userId, ticker, ticker2);
        return (null != ticker2 ? getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_USER_AND_PAIR)
                                                .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.COUNT_ALERTS_OF_SERVER_AND_USER_AND_TICKER))
                .bind("ticker", ticker)
                .bind("serverId", serverId)
                .bind("userId", userId)
                .mapTo(Long.class)
                .one();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUser(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUser {} {} {}", userId, offset, limit);
        return getHandle().createQuery(SQL.ALERTS_OF_USER)
                .bind("userId", userId)
                .bind("offset", offset)
                .bind("limit", limit).mapTo(Alert.class).list();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfUserAndTickers {} {} {} {} {}", userId, offset, limit, ticker, ticker2);
        return (null != ticker2 ? getHandle().createQuery(SQL.ALERTS_OF_USER_AND_PAIR)
                                            .bind("ticker2", ticker2) :
                                getHandle().createQuery(SQL.ALERTS_OF_USER_AND_TICKER))
                .bind("ticker", ticker)
                .bind("userId", userId)
                .bind("offset", offset)
                .bind("limit", limit).mapTo(Alert.class).list();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServer(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServer {} {} {}", serverId, offset, limit);
        return getHandle().createQuery(SQL.ALERTS_OF_SERVER)
                .bind("serverId", serverId)
                .bind("offset", offset)
                .bind("limit", limit).mapTo(Alert.class).list();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUser {} {} {} {}", serverId, userId, offset, limit);
        return getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_USER)
                .bind("serverId", serverId)
                .bind("userId", userId)
                .bind("offset", offset)
                .bind("limit", limit).mapTo(Alert.class).list();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfServerAndTickers {} {} {} {} {}", serverId, offset, limit, ticker, ticker2);
        return (null != ticker2 ? getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_PAIR)
                                            .bind("ticker2", ticker2) :
                               getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_TICKER))
                .bind("ticker", ticker)
                .bind("serverId", serverId)
                .bind("offset", offset)
                .bind("limit", limit).mapTo(Alert.class).list();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickers {} {} {} {} {} {}", serverId, userId, offset, limit, ticker, ticker2);
        return (null != ticker2 ? getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_USER_AND_PAIR)
                                            .bind("ticker2", ticker2) :
                               getHandle().createQuery(SQL.ALERTS_OF_SERVER_AND_USER_AND_TICKER))
                .bind("ticker", ticker)
                .bind("serverId", serverId)
                .bind("userId", userId)
                .bind("offset", offset)
                .bind("limit", limit).mapTo(Alert.class).list();
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        LOGGER.debug("addAlert {}", alert);
        return getHandle().createUpdate(SQL.INSERT_ALERT)
                .bindFields(alert)
                .execute();
//                .executeAndReturnGeneratedKeys("id")
//                .mapTo(Long.class)
//                .first();  should return new alert id
    }

    @Override
    public void updateMessage(long alertId, @NotNull String message) {
        LOGGER.debug("updateMessage {} {}", alertId, message);

    }

    @Override
    public void updateMargin(long alertId, @NotNull BigDecimal margin) {
        LOGGER.debug("updateMargin {} {}", alertId, margin);

    }

    @Override
    public void updateLastTriggerMarginRepeat(long alertId, @NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        LOGGER.debug("updateLastTriggerMarginRepeat {} {} {} {}", alertId, lastTrigger, margin, repeat);

    }

    @Override
    public void updateRepeat(long alertId, short repeat) {
        LOGGER.debug("updateRepeat {} {}", alertId, repeat);

    }

    @Override
    public void updateRepeatDelay(long alertId, short repeatDelay) {
        LOGGER.debug("updateRepeatDelay {} {}", alertId, repeatDelay);

    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        getHandle().createUpdate(SQL.DELETE_BY_ID)
                .bind("id", alertId)
                .execute();
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull Consumer<MatchedAlertUpdater> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        batch(SQL.UPDATE_ALERTS_LAST_TRIGGER_MARGIN_REPEAT,
                batch -> updater.accept(
                        (id, lastTrigger, margin, repeat) -> batch.accept(
                                statement -> {
                                    LOGGER.debug("New matched alert batch entry, id : {}, lastTrigger : {}, margin : {}, repeat : {}", id, lastTrigger, margin, repeat);
                                    statement.bind("id", id)
                                        .bind("lastTrigger", lastTrigger)
                                        .bind("margin", margin)
                                        .bind("repeat", repeat).add();
                                })));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull Consumer<MarginAlertUpdater> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        batch(SQL.UPDATE_ALERTS_MARGIN,
                batch -> updater.accept(
                        (id, margin) -> batch.accept(
                                statement -> {
                                    LOGGER.debug("New margin alert batch entry, id : {}, margin : {}", id, margin);
                                    statement.bind("id", id).bind("margin", margin).add();
                                })));
    }
}
