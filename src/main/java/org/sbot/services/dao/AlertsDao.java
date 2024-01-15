package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.alerts.Alert.Type;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface AlertsDao extends TransactionalCtx {

    Optional<Alert> getAlert(long id);

    record UserIdServerIdType(long userId, long serverId, @NotNull Type type) {}
    Optional<UserIdServerIdType> getUserIdAndServerIdAndType(long alertId);
    List<Long> getUserIdsByServerId(long serverId);

    long fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsHavingRepeatZeroAndLastTriggerBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchRangeAlertsHavingToDateBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);

    @NotNull
    Map<Long, String> getAlertMessages(@NotNull long[] alertIds);

    @NotNull
    Map<String, Set<String>> getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange();

    long countAlertsOfUser(long userId);
    long countAlertsOfServer(long serverId);
    long countAlertsOfServerAndUser(long serverId, long user);
    long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair);
    long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair);
    long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair);


    @NotNull
    List<Alert> getAlertsOfUser(long userId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String tickerOrPair);
    @NotNull
    List<Alert> getAlertsOfServer(long serverId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String tickerOrPair);
    @NotNull
    List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair);


    long addAlert(@NotNull Alert alert);

    void updateServerId(long alertId, long serverId);
    long updateServerIdOfUserAndServerId(long userId, long serverId, long newServerId);
    long updateServerIdOfUserAndServerIdAndTickers(long userId, long serverId, @NotNull String tickerOrPair, long newServerId);
    void updateMessage(long alertId, @NotNull String message);
    void updateMargin(long alertId, @NotNull BigDecimal margin);
    void updateRepeat(long alertId, short repeat);
    void updateRepeatDelay(long alertId, short repeatDelay);

    void deleteAlert(long alertId);
    long deleteAlerts(long serverId, long userId);
    long deleteAlerts(long serverId, long userId, @NotNull String tickerOrPair);

    void matchedAlertBatchUpdates(@NotNull Consumer<BatchEntry> updater);
    void marginAlertBatchUpdates(@NotNull Consumer<BatchEntry> updater);
    void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter);
}
