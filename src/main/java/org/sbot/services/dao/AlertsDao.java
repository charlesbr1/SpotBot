package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.ClientType;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

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

    record SelectionFilter(@NotNull ClientType clientType, @Nullable Long serverId, @Nullable Long userId, @Nullable Type type, @Nullable String tickerOrPair) {

        public SelectionFilter {
            requireNonNull(clientType, "missing SelectionFilter clientType");
        }

        public static SelectionFilter ofUser(@NotNull ClientType clientType, long userId, @Nullable Type type) {
            return new SelectionFilter(clientType,  null, userId, type, null);
        }
        public static SelectionFilter ofServer(@NotNull ClientType clientType, long serverId, @Nullable Type type) {
            return new SelectionFilter(clientType, serverId, null, type, null);
        }
        public static SelectionFilter of(@NotNull ClientType clientType, long serverId, long userId, @Nullable Type type) {
            return new SelectionFilter(clientType, serverId, userId, type, null);
        }

        public SelectionFilter withTickerOrPair(@Nullable String tickerOrPair) {
            return new SelectionFilter(clientType, serverId, userId, type, tickerOrPair);
        }
    }

    long fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);
    long fetchAlertsWithoutMessageByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer);

    @NotNull
    Map<String, Set<String>> getPairsByExchangesHavingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin);

    @NotNull
    List<Long> getUserIdsByServerId(@NotNull ClientType clientType, long serverId);

    Optional<Alert> getAlert(@NotNull ClientType clientType, long id);

    Optional<Alert> getAlertWithoutMessage(@NotNull ClientType clientType,long alertId);

    @NotNull
    Map<Long, String> getAlertMessages(@NotNull LongStream alertIds);

    long countAlerts(@NotNull SelectionFilter filter);

    @NotNull
    List<Alert> getAlertsOrderByPairUserIdId(@NotNull SelectionFilter filter, long offset, long limit);

    long addAlert(@NotNull Alert alert);

    void update(@NotNull Alert alert, @NotNull Set<UpdateField> fields);

    long updateServerIdOf(@NotNull SelectionFilter filter, long newServerId);

    void delete(@NotNull ClientType clientType, long alertId);

    long delete(@NotNull SelectionFilter filter);

    // this update the alert listening date, margin to MARGIN_DISABLED, lastTrigger to now, and decrease repeat if not at zero
    void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater);

    // this set the alert margin to MARGIN_DISABLED
    void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater);

    void delete(@NotNull Consumer<BatchEntry> deleter);
}
