package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public interface AlertsDao {

    Optional<Alert> getAlert(long id);

    Optional<Alert> getAlertWithoutMessage(long alertId);
    List<Long> getUserIdsByServerId(long serverId);

    long fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);

    @NotNull
    Map<Long, String> getAlertMessages(@NotNull LongStream alertIds);

    @NotNull
    Map<String, Set<String>> getPairsByExchangesHavingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin);

    long countAlertsOfUser(long userId);
    long countAlertsOfServer(long serverId);
    long countAlertsOfServerAndUser(long serverId, long user);
    long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair);
    long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair);
    long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair);


    @NotNull
    List<Alert> getAlertsOfUserOrderByPairId(long userId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfUserAndTickersOrderById(long userId, long offset, long limit, @NotNull String tickerOrPair);
    @NotNull
    List<Alert> getAlertsOfServerOrderByPairUserIdId(long serverId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfServerAndUserOrderByPairId(long serverId, long userId, long offset, long limit);
    @NotNull
    List<Alert> getAlertsOfServerAndTickersOrderByUserIdId(long serverId, long offset, long limit, @NotNull String tickerOrPair);
    @NotNull
    List<Alert> getAlertsOfServerAndUserAndTickersOrderById(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair);


    long addAlert(@NotNull Alert alert);

    void updateServerId(long alertId, long serverId);
    long updateServerIdPrivate(long serverId);
    long updateServerIdOfUserAndServerId(long userId, long serverId, long newServerId);
    long updateServerIdOfUserAndServerIdAndTickers(long userId, long serverId, @NotNull String tickerOrPair, long newServerId);
    void updateFromPrice(long alertId, @NotNull BigDecimal fromPrice);
    void updateToPrice(long alertId, @NotNull BigDecimal toPrice);
    void updateFromDate(long alertId, @Nullable ZonedDateTime fromDate);
    void updateToDate(long alertId, @Nullable ZonedDateTime toDate);
    void updateMessage(long alertId, @NotNull String message);
    void updateMargin(long alertId, @NotNull BigDecimal margin);
    void updateListeningDateFromDate(long alertId, @Nullable ZonedDateTime listeningDate, @Nullable ZonedDateTime fromDate);
    void updateListeningDateRepeat(long alertId, @Nullable ZonedDateTime listeningDate, short repeat);
    void updateSnooze(long alertId, short snooze);

    void deleteAlert(long alertId);
    long deleteAlerts(long serverId, long userId);
    long deleteAlerts(long serverId, long userId, @NotNull String tickerOrPair);

    // this set the alert' margin to MARGIN_DISABLED, lastTrigger to now, and decrease repeat if not zero
    void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater);

    // this set the alert' margin to MARGIN_DISABLED
    void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater);

    void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter);
}
