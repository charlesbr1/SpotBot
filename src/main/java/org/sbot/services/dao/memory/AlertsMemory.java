package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.sbot.SpotBot.ALERTS_CHECK_PERIOD_MIN;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.range;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.utils.Dates.nowUtc;

public final class AlertsMemory implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsMemory.class);

    private final Map<Long, Alert> alerts = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1L);

    {
        LOGGER.debug("Loading memory storage for alerts");
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("getAlert {}", alertId);
        return Optional.ofNullable(alerts.get(alertId));
    }

    @Override
    public Optional<Alert> getAlertWithoutMessage(long alertId) {
        LOGGER.debug("getAlertWithoutMessage {}", alertId);
        return Optional.ofNullable(alerts.get(alertId))
                .map(alert -> alert.withMessage("")); // erase the message to simulate the SQL layer
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
                .filter(alert -> null != alert.lastTrigger && alert.lastTrigger.isBefore(expirationDate))
                .filter(alert -> ++read[0] != 0));
        return read[0];
    }

    @Override
    public long fetchAlertsByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsByTypeHavingToDateBefore {} {}", type, expirationDate);
        long[] read = new long[] {0L};
        alertsConsumer.accept(alerts.values().stream()
                .filter(alert -> alert.type == type)
                .filter(alert -> null != alert.toDate && alert.toDate.isBefore(expirationDate))
                .filter(alert -> ++read[0] != 0));
        return read[0];
    }

    @NotNull
    private static Stream<Alert> havingRepeatAndDelayOverWithActiveRange(@NotNull Stream<Alert> alerts) {
        ZonedDateTime now = nowUtc();
        ZonedDateTime nowPlusOneSecond = now.plusSeconds(1L);
        ZonedDateTime nowPlusDelta = now.plusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 1L);
        long nowPlusDeltaSeconds = nowPlusDelta.toEpochSecond();
        return alerts.filter(alert -> hasRepeat(alert.repeat))
                .filter(alert -> alert.isSnoozeOver(nowPlusDeltaSeconds))
                .filter(alert -> alert.type != remainder || alert.fromDate.isBefore(nowPlusDelta))
                .filter(alert -> alert.type != range ||
                        ((null == alert.fromDate || alert.fromDate.compareTo(nowPlusOneSecond) <= 0) &&
                                (null == alert.toDate || alert.toDate.compareTo(now) > 0)));
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
        return getAlertsOfUserStream(userId, 0, Long.MAX_VALUE).count();
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {}", userId, tickerOrPair);
        return getAlertsOfUserAndTickersStream(userId, 0, Long.MAX_VALUE, tickerOrPair).count();
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        return getAlertsOfServerStream(serverId, 0, Long.MAX_VALUE).count();
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        return getAlertsOfServerAndUserStream(serverId, userId, 0, Long.MAX_VALUE).count();
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {}", serverId, tickerOrPair);
        return getAlertsOfServerAndTickersStream(serverId, 0, Long.MAX_VALUE, tickerOrPair).count();
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {}", serverId, userId, tickerOrPair);
        return getAlertsOfServerAndUserAndTickersStream(serverId, userId, 0, Long.MAX_VALUE, tickerOrPair).count();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUser(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUser {} {} {}", userId, offset, limit);
        return getAlertsOfUserStream(userId, offset, limit).toList();
    }

    private Stream<Alert> getAlertsOfUserStream(long userId, long offset, long limit) {
        return alerts.values().stream()
                .filter(alert -> alert.userId == userId)
                .skip(offset).limit(limit);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickers(long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfUserAndTickers {} {} {} {}", userId, offset, limit, tickerOrPair);
        return getAlertsOfUserAndTickersStream(userId, offset, limit, tickerOrPair).toList();
    }

    private Stream<Alert> getAlertsOfUserAndTickersStream(long userId, long offset, long limit, @NotNull String tickerOrPair) {
        return alerts.values().stream()
                .filter(alert -> alert.userId == userId)
                .filter(alert -> alert.pair.contains(tickerOrPair))
                .skip(offset).limit(limit);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServer(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServer {} {} {}", serverId, offset, limit);
        return getAlertsOfServerStream(serverId, offset, limit).toList();
    }

    private Stream<Alert> getAlertsOfServerStream(long serverId, long offset, long limit) {
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .skip(offset).limit(limit);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUser(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUser {} {} {} {}", serverId, userId, offset, limit);
        return getAlertsOfServerAndUserStream(serverId, userId, offset, limit).toList();
    }

    private Stream<Alert> getAlertsOfServerAndUserStream(long serverId, long userId, long offset, long limit) {
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.userId == userId)
                .skip(offset).limit(limit);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickers(long serverId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndTickers {} {} {} {}", serverId, offset, limit, tickerOrPair);
        return getAlertsOfServerAndTickersStream(serverId, offset, limit, tickerOrPair).toList();
    }

    private Stream<Alert> getAlertsOfServerAndTickersStream(long serverId, long offset, long limit, @NotNull String tickerOrPair) {
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.pair.contains(tickerOrPair))
                .skip(offset).limit(limit);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickers(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickers {} {} {} {} {}", serverId, userId, offset, limit, tickerOrPair);
        return getAlertsOfServerAndUserAndTickersStream(serverId, userId, offset, limit, tickerOrPair).toList();
    }

    private Stream<Alert> getAlertsOfServerAndUserAndTickersStream(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair) {
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .filter(alert -> alert.userId == userId)
                .filter(alert -> alert.pair.contains(tickerOrPair))
                .skip(offset).limit(limit);
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
    public long updateServerIdPrivate(long serverId) {
        LOGGER.debug("updateServerIdPrivate {}", serverId);
        long[] updatedAlerts = new long[] {0L};
        alerts.replaceAll((id, alert) ->
                alert.serverId == serverId &&
                ++updatedAlerts[0] != 0 ? alert.withServerId(PRIVATE_ALERT) : alert);
        return updatedAlerts[0];
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
    public void updateFromPrice(long alertId, @NotNull BigDecimal fromPrice) {
        LOGGER.debug("updateFromPrice {} {}", alertId, fromPrice);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withFromPrice(fromPrice));
    }

    @Override
    public void updateToPrice(long alertId, @NotNull BigDecimal toPrice) {
        LOGGER.debug("updateToPrice {} {}", alertId, toPrice);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withToPrice(toPrice));
    }

    @Override
    public void updateFromDate(long alertId, @Nullable ZonedDateTime fromDate) {
        LOGGER.debug("updateFromDate {} {}", alertId, fromDate);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withFromDate(fromDate));
    }

    @Override
    public void updateToDate(long alertId, @Nullable ZonedDateTime toDate) {
        LOGGER.debug("updateToDate {} {}", alertId, toDate);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withToDate(toDate));
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
    public void updateRepeatAndLastTrigger(long alertId, short repeat, @Nullable ZonedDateTime lastTrigger) {
        LOGGER.debug("updateRepeatAndLastTrigger {} {} {}", alertId, repeat, lastTrigger);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withLastTriggerRepeatSnooze(lastTrigger, repeat, alert.snooze));

    }

    @Override
    public void updateSnoozeAndLastTrigger(long alertId, short snooze, @Nullable ZonedDateTime lastTrigger) {
        LOGGER.debug("updateSnoozeAndLastTrigger {} {} {}", alertId, snooze, lastTrigger);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withLastTriggerRepeatSnooze(lastTrigger, alert.repeat, snooze));

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
                (id, alert) -> alert.withLastTriggerMarginRepeat(nowUtc(), MARGIN_DISABLED, ((short) Math.max(0, alert.repeat - 1)))));
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
