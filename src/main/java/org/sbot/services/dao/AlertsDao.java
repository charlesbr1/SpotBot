package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface AlertsDao extends TransactionalCtx {

    Optional<Alert> getAlert(long id);

    void fetchAlertsByExchangeAndPairHavingRepeats(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer);

    @NotNull
    Map<String, List<String>> getPairsByExchanges();

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
    interface MatchedAlertUpdater {
        void update(long id, @NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat);
    }

    void matchedAlertBatchUpdates(@NotNull Consumer<MatchedAlertUpdater> updater);

    @FunctionalInterface
    interface MarginAlertUpdater {
        void update(long id, @NotNull BigDecimal margin);
    }

    void marginAlertBatchUpdates(@NotNull Consumer<MarginAlertUpdater> updater);
}
