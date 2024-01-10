package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.alerts.Alert.Type;

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
    long countAlertsOfUserAndTickers(long userId, @NotNull String ticker, @Nullable String ticker2);
    long countAlertsOfServerAndTickers(long serverId, @NotNull String ticker, @Nullable String ticker2);
    long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String ticker, @Nullable String ticker2);


    @NotNull
    List<Alert> getAlertsOfUser(long userId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2);
    @NotNull
    List<Alert> getAlertsOfServer(long serverId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2);
    @NotNull
    List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String ticker, @Nullable String ticker2);


    long addAlert(@NotNull Alert alert);

    void updateMessage(long alertId, @NotNull String message);
    void updateMargin(long alertId, @NotNull BigDecimal margin);
    void updateRepeat(long alertId, short repeat);
    void updateRepeatDelay(long alertId, short repeatDelay);

    void deleteAlert(long alertId);

    @FunctionalInterface
    interface MatchingAlertUpdater {
        void update(long id);
    }

    void matchedAlertBatchUpdates(@NotNull Consumer<MatchingAlertUpdater> updater);
    void marginAlertBatchUpdates(@NotNull Consumer<MatchingAlertUpdater> updater);
    void matchedRemainderAlertBatchDeletes(@NotNull Consumer<MatchingAlertUpdater> deleter);
}
