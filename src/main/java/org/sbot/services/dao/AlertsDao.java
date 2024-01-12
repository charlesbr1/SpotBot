package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.alerts.Alert.Type;
import org.sbot.services.dao.sqlite.jdbi.JDBIRepository.BatchEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface AlertsDao extends TransactionalCtx {

    Optional<Alert> getAlert(long id);

    record UserIdServerIdType(long userId, long serverId, @NotNull Type type) {}
    Optional<UserIdServerIdType> getUserIdAndServerIdAndType(long alertId);

    void fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    @NotNull
    Map<Long, String> getAlertMessages(@NotNull long[] alertIds);

    @NotNull
    Map<String, List<String>> getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange();

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

    void updateMessage(long alertId, @NotNull String message);
    void updateMargin(long alertId, @NotNull BigDecimal margin);
    void updateRepeat(long alertId, short repeat);
    void updateRepeatDelay(long alertId, short repeatDelay);

    void deleteAlert(long alertId);

    void matchedAlertBatchUpdates(@NotNull Consumer<BatchEntry> updater);
    void marginAlertBatchUpdates(@NotNull Consumer<BatchEntry> updater);
    void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter);
}
