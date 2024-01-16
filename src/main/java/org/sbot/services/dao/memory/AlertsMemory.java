package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.alerts.Alert.MARGIN_DISABLED;
import static org.sbot.alerts.Alert.Type.range;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.Alert.hasRepeat;

public final class AlertsMemory implements AlertsDao {

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
    public Optional<UserIdServerIdType> getUserIdAndServerIdAndType(long alertId) {
        LOGGER.debug("getUserIdAndServerId {}", alertId);
        return Optional.ofNullable(alerts.get(alertId))
                .map(alert -> new UserIdServerIdType(alert.userId, alert.serverId, alert.type));
    }

    @Override
    public List<Long> getUserIdsByServerId(long serverId) {
        LOGGER.debug("getUserIdsByServerId {}", serverId);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .map(Alert::getUserId).toList();
    }

    @Override
    public long fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingRepeats {} {}", exchange, pair);
        long[] read = new long[] {0L};
        alertsConsumer.accept(havingRepeatAndDelayOverWithActiveRange(
                alerts.values().stream()
                .filter(alert -> alert.exchange.equals(exchange))
                .filter(alert -> alert.pair.equals(pair)))
                .map(alert -> alert.withMessage("")) // erase the message to simulate the SQL layer
                .filter(alert -> ++read[0] != 0));
        return read[0];
    }

    @Override
    public long fetchAlertsHavingRepeatZeroAndLastTriggerBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsHavingRepeatZeroAndLastTriggerBefore {}", expirationDate);
        long[] read = new long[] {0L};
        alertsConsumer.accept(alerts.values().stream()
                .filter(alert -> alert.repeat <= 0)
                .filter(alert -> requireNonNull(alert.lastTrigger).isBefore(expirationDate))
                .filter(alert -> ++read[0] != 0));
        return read[0];
    }

    @Override
    public long fetchRangeAlertsHavingToDateBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchRangeAlertsHavingToDateBefore {}", expirationDate);
        long[] read = new long[] {0L};
        alertsConsumer.accept(alerts.values().stream()
                .filter(alert -> alert.type == range)
                .filter(alert -> requireNonNull(alert.toDate).isBefore(expirationDate))
                .filter(alert -> ++read[0] != 0));
        return read[0];
    }

    @NotNull
    private static Stream<Alert> havingRepeatAndDelayOverWithActiveRange(@NotNull Stream<Alert> alerts) {
        ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
        ZonedDateTime nowMinus60 = now.minusMinutes(60);
        ZonedDateTime nowPlus60 = now.plusMinutes(60);
        long nowSeconds = now.plusMinutes(5).toEpochSecond();
        return alerts.filter(alert -> hasRepeat(alert.repeat))
                .filter(alert -> alert.isSnoozeOver(nowSeconds))
                .filter(alert -> alert.type != remainder || alert.fromDate.isBefore(nowPlus60) ||alert.fromDate.isAfter(nowMinus60))
                .filter(alert -> alert.type != range || null  == alert.fromDate ||
                        (alert.fromDate.isBefore(nowPlus60) && (null == alert.toDate || alert.toDate.isAfter(nowMinus60))));
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
    public Map<String, Set<String>> getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange() {
        LOGGER.debug("getPairsByExchanges");
        return havingRepeatAndDelayOverWithActiveRange(alerts.values().stream())
                .collect(groupingBy(Alert::getExchange, mapping(Alert::getPair, toSet())));
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        return getAlertsOfUser(userId, 0, Long.MAX_VALUE).size();
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {}", userId, tickerOrPair);
        return getAlertsOfUserAndTickers(userId, 0, Long.MAX_VALUE, tickerOrPair).size();
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
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {}", serverId, tickerOrPair);
        return getAlertsOfServerAndTickers(serverId, 0, Long.MAX_VALUE, tickerOrPair).size();
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {}", serverId, userId, tickerOrPair);
        return getAlertsOfServerAndUserAndTickers(serverId, userId, 0, Long.MAX_VALUE, tickerOrPair).size();
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
    public List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfUserAndTickers {} {} {} {}", userId, offset, limit, tickerOrPair);
        return alerts.values().stream()
                .filter(alert -> alert.userId == userId)
                .filter(alert -> alert.pair.contains(tickerOrPair))
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
    public List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndTickers {} {} {} {}", serverId, offset, limit, tickerOrPair);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.pair.contains(tickerOrPair))
                .skip(offset).limit(limit).toList();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickers {} {} {} {} {}", serverId, userId, offset, limit, tickerOrPair);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.userId == userId)
                .filter(alert -> alert.pair.contains(tickerOrPair))
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
    public void updateServerId(long alertId, long serverId) {
        LOGGER.debug("updateServerId {} {}", alertId, serverId);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withServerId(serverId));
    }

    @Override
    public long updateServerIdOfUserAndServerId(long userId, long serverId, long newServerId) {
        LOGGER.debug("updateServerIdOfUserAndServerId {} {} {}", userId, serverId, newServerId);
        long[] updatedAlerts = new long[] {0L};
        alerts.replaceAll((alertId, alert) ->
                alert.userId == userId &&
                alert.serverId == serverId &&
                ++updatedAlerts[0] != 0 ? alert.withServerId(newServerId) : alert);
        return updatedAlerts[0];
    }

    @Override
    public long updateServerIdOfUserAndServerIdAndTickers(long userId, long serverId, @NotNull String tickerOrPair, long newServerId) {
        LOGGER.debug("updateServerIdOfUserAndServerIdAndTickers {} {} {} {}", userId, serverId, tickerOrPair, newServerId);
        long[] updatedAlerts = new long[] {0L};
        alerts.replaceAll((alertId, alert) ->
                alert.userId == userId &&
                alert.serverId == serverId &&
                alert.pair.contains(tickerOrPair) &&
                ++updatedAlerts[0] != 0 ? alert.withServerId(newServerId) : alert);
        return updatedAlerts[0];
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
    public void updateSnooze(long alertId, short snooze) {
        LOGGER.debug("updateSnooze {} {}", alertId, snooze);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withSnooze(snooze));

    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        alerts.remove(alertId);
    }

    @Override
    public long deleteAlerts(long serverId, long userId) {
        LOGGER.debug("deleteAlerts {} {}", serverId, userId);
        long[] removedAlerts = new long[] {0L};
        alerts.entrySet().removeIf(entry ->
                entry.getValue().userId == userId &&
                entry.getValue().serverId == serverId &&
                ++removedAlerts[0] != 0);
        return removedAlerts[0];
    }

    @Override
    public long deleteAlerts(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("deleteAlerts {} {} {}", serverId, userId, tickerOrPair);
        long[] removedAlerts = new long[] {0L};
        alerts.entrySet().removeIf(entry ->
                entry.getValue().userId == userId &&
                entry.getValue().serverId == serverId &&
                entry.getValue().pair.contains(tickerOrPair) &&
                ++removedAlerts[0] != 0);
        return removedAlerts[0];
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        updater.accept(alertId -> alerts.computeIfPresent(alertId,
                (id, alert) -> alert.withLastTriggerMarginRepeat(ZonedDateTime.now(), MARGIN_DISABLED, ((short) Math.max(0, alert.repeat - 1)))));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        updater.accept(alertId -> alerts.computeIfPresent(alertId,
                (id, alert) -> alert.withMargin(MARGIN_DISABLED)));
    }

    @Override
    public void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("matchedRemainderAlertBatchDeletes");
        deleter.accept(alerts::remove);
    }
}
