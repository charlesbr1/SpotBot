package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public interface AlertsDao {

    enum UpdateField {
        SERVER_ID,
        LISTENING_DATE,
        FROM_PRICE,
        TO_PRICE,
        FROM_DATE,
        TO_DATE,
        MESSAGE,
        MARGIN,
        REPEAT,
        SNOOZE
    }

    record SelectionFilter(@Nullable Long serverId, @Nullable Long userId, @Nullable Type type, @Nullable String tickerOrPair) {

        public static SelectionFilter ofUser(long userId, @Nullable Type type) {
            return new SelectionFilter( null, userId, type, null);
        }
        public static SelectionFilter ofServer(long serverId, @Nullable Type type) {
            return new SelectionFilter(serverId, null, type, null);
        }
        public static SelectionFilter of(long serverId, long userId, @Nullable Type type) {
            return new SelectionFilter(serverId, userId, type, null);
        }

        public SelectionFilter withTickerOrPair(@Nullable String tickerOrPair) {
            return new SelectionFilter(serverId, userId, type, tickerOrPair);
        }
    }

    long fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);

    @NotNull
    Map<String, Set<String>> getPairsByExchangesHavingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin);

    @NotNull
    List<Long> getUserIdsByServerId(long serverId);

    Optional<Alert> getAlert(long id);

    Optional<Alert> getAlertWithoutMessage(long alertId);

    @NotNull
    Map<Long, String> getAlertMessages(@NotNull LongStream alertIds);

    long countAlerts(@NotNull SelectionFilter filter);

    @NotNull
    List<Alert> getAlertsOrderByPairUserIdId(@NotNull SelectionFilter filter, long offset, long limit);

    long addAlert(@NotNull Alert alert);

    void update(long alertId, @NotNull Map<UpdateField, Object> fields);

    long updateServerIdOf(@NotNull SelectionFilter filter, long newServerId);

    void deleteAlert(long alertId);

    long deleteAlerts(@NotNull SelectionFilter filter);

    // this set the alert' margin to MARGIN_DISABLED, lastTrigger to now, and decrease repeat if not zero
    void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater);

    // this set the alert' margin to MARGIN_DISABLED
    void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater);

    void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter);
}
