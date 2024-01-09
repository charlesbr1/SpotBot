package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.sbot.alerts.Alert.MARGIN_DISABLED;
import static org.sbot.alerts.Alert.hasRepeat;

public class AlertsMemory implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsMemory.class);

    private final Map<Long, Alert> alerts = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(0L);

    {
        LOGGER.debug("Loading memory storage for alerts");
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("getAlert {}", alertId);
        return Optional.ofNullable(alerts.get(alertId));
    }

    @Override
    public Optional<UserIdServerId> getUserIdAndServerId(long alertId) {
        LOGGER.debug("getUserIdAndServerId {}", alertId);
        return Optional.ofNullable(alerts.get(alertId))
                .map(alert -> new UserIdServerId(alert.userId, alert.serverId));
    }

    @Override
    public void fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOver(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingRepeats {} {}", exchange, pair);
        long nowSeconds = Instant.now().getEpochSecond();
        alertsConsumer.accept(alerts.values().stream()
                .filter(alert -> alert.exchange.equals(exchange))
                .filter(alert -> alert.getSlashPair().equals(pair))
                .filter(alert -> hasRepeat(alert.repeat))
                .filter(alert -> alert.isRepeatDelayOver(nowSeconds + 300))
                .map(alert -> alert.withMessage(""))); // erase the message to simulate the SQL layer
    }

    @Override
    @NotNull
    public Map<Long, String> getAlertMessages(@NotNull long[] alertIds) {
        LOGGER.debug("getAlertMessages {}", alertIds);
        var alertIdSet = Arrays.stream(alertIds).boxed().collect(toSet());
        return alerts.values().stream().filter(alert -> alertIdSet.contains(alert.id))
                .collect(toMap(Alert::getId, Alert::getMessage));
    }

    @Override
    @NotNull
    public Map<String, List<String>> getPairsByExchangesHavingRepeatAndDelayOver() {
        LOGGER.debug("getPairsByExchanges");
        long nowSeconds = Instant.now().getEpochSecond();
        return alerts.values().stream()
                .filter(alert -> hasRepeat(alert.repeat))
                .filter(alert -> alert.isRepeatDelayOver(nowSeconds + 300))
                .collect(groupingBy(Alert::getExchange, mapping(Alert::getSlashPair, toList())));
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        return getAlertsOfUser(userId, 0, Long.MAX_VALUE).size();
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {} {}", userId, ticker, ticker2);
        return getAlertsOfUserAndTickers(userId, 0, Long.MAX_VALUE, ticker, ticker2).size();
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        return getAlertsOfServer(serverId, 0, Long.MAX_VALUE).size();
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        return getAlertsOfServerAndUser(serverId, userId, 0, Long.MAX_VALUE).size();
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {} {}", serverId, ticker, ticker2);
        return getAlertsOfServerAndTickers(serverId, 0, Long.MAX_VALUE, ticker, ticker2).size();
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {} {}", serverId, userId, ticker, ticker2);
        return getAlertsOfServerAndUserAndTickers(serverId, userId, 0, Long.MAX_VALUE, ticker, ticker2).size();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUser(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUser {} {} {}", userId, offset, limit);
        return alerts.values().stream()
                .filter(alert -> alert.userId == userId)
                .skip(offset).limit(limit).toList();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfUserAndTickers {} {} {} {} {}", userId, offset, limit, ticker, ticker2);
        Predicate<Alert> checkTickers = null != ticker2 ?
                alert -> alert.ticker1.equals(ticker) && alert.ticker2.equals(ticker2) :
                alert -> List.of(alert.ticker1, alert.ticker2).contains(ticker);
        return alerts.values().stream()
                .filter(alert -> alert.userId == userId)
                .filter(checkTickers)
                .skip(offset).limit(limit).toList();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServer(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServer {} {} {}", serverId, offset, limit);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .skip(offset).limit(limit).toList();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUser {} {} {} {}", serverId, userId, offset, limit);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.userId == userId)
                .skip(offset).limit(limit).toList();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfServerAndTickers {} {} {} {} {}", serverId, offset, limit, ticker, ticker2);
        Predicate<Alert> checkTickers = null != ticker2 ?
                alert -> alert.ticker1.equals(ticker) && alert.ticker2.equals(ticker2) :
                alert -> List.of(alert.ticker1, alert.ticker2).contains(ticker);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(checkTickers)
                .skip(offset).limit(limit).toList();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickers {} {} {} {} {} {}", serverId, userId, offset, limit, ticker, ticker2);
        Predicate<Alert> checkTickers = null != ticker2 ?
                alert -> alert.ticker1.equals(ticker) && alert.ticker2.equals(ticker2) :
                alert -> List.of(alert.ticker1, alert.ticker2).contains(ticker);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.userId == userId)
                .filter(checkTickers)
                .skip(offset).limit(limit).toList();
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        alert = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}, with new id {}", alert, alert.id);
        alerts.put(alert.id, alert);
        return alert.id;
    }

    @Override
    public void updateMessage(long alertId, @NotNull String message) {
        LOGGER.debug("updateMessage {} {}", alertId, message);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withMessage(message));
    }

    @Override
    public void updateMargin(long alertId, @NotNull BigDecimal margin) {
        LOGGER.debug("updateMargin {} {}", alertId, margin);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withMargin(margin));
    }

    @Override
    public void updateRepeat(long alertId, short repeat) {
        LOGGER.debug("updateRepeat {} {}", alertId, repeat);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withRepeat(repeat));

    }

    @Override
    public void updateRepeatDelay(long alertId, short repeatDelay) {
        LOGGER.debug("updateRepeatDelay {} {}", alertId, repeatDelay);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withRepeatDelay(repeatDelay));

    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        alerts.remove(alertId);
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull Consumer<TriggeredAlertUpdater> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        updater.accept(alertId -> alerts.computeIfPresent(alertId,
                (id, alert) -> alert.withLastTriggerMarginRepeat(ZonedDateTime.now(), MARGIN_DISABLED, ((short) Math.max(0, alert.repeat - 1)))));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull Consumer<TriggeredAlertUpdater> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        updater.accept(alertId -> alerts.computeIfPresent(alertId,
                (id, alert) -> alert.withMargin(MARGIN_DISABLED)));
    }

    @Override
    public <T> T transactional(@NotNull Supplier<T> callback, @NotNull TransactionIsolationLevel isolationLevel) {
        return callback.get(); // no transaction support in memory
    }
}
