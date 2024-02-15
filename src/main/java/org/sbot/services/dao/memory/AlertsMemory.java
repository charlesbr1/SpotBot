package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class AlertsMemory implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsMemory.class);

    private final Map<Long, Alert> alerts = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1L);


    public AlertsMemory() {
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
    public long fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(@NotNull String exchange, @NotNull String pair, @NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange {} {} {} {}", exchange, pair, now, checkPeriodMin);
        requireNonNull(exchange); requireNonNull(pair);
        long[] read = new long[] {0L};
        alertsConsumer.accept(havingPastListeningDateWithActiveRange(now, checkPeriodMin, alerts.values().stream()
                    .filter(alert -> alert.exchange.equals(exchange) && alert.pair.equals(pair)))
                .map(alert -> {
                    read[0] += 1;
                    return alert.withMessage(""); // simulate sql layer
                }));
        return read[0];
    }

    @NotNull
    private Stream<Alert> havingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin, @NotNull Stream<Alert> alerts) {
        ZonedDateTime nowPlusOneSecond = now.plusSeconds(1L);
        ZonedDateTime nowPlusDelta = now.plusMinutes(Math.ceilDiv(requirePositive(checkPeriodMin), 2));
        return alerts.filter(alert ->
                (null != alert.listeningDate && alert.listeningDate.compareTo(nowPlusOneSecond) <= 0) &&
                (alert.type != remainder || alert.fromDate.isBefore(nowPlusDelta)) &&
                (alert.type != range || (null == alert.toDate || alert.toDate.compareTo(now) > 0)));
    }

    @Override
    public long fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore {}", expirationDate);
        requireNonNull(expirationDate);
        long[] read = new long[] {0L};
        alertsConsumer.accept(alerts.values().stream()
                .filter(alert -> alert.repeat <= 0 &&
                                ((null != alert.lastTrigger && alert.lastTrigger.isBefore(expirationDate)) ||
                                 (null == alert.lastTrigger && alert.creationDate.isBefore(expirationDate))) &&
                                ++read[0] != 0));
        return read[0];
    }

    @Override
    public long fetchAlertsByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsByTypeHavingToDateBefore {} {}", type, expirationDate);
        requireNonNull(type); requireNonNull(expirationDate);
        long[] read = new long[] {0L};
        alertsConsumer.accept(alerts.values().stream()
                .filter(alert -> alert.type == type &&
                        (null != alert.toDate && alert.toDate.isBefore(expirationDate)) &&
                        ++read[0] != 0));
        return read[0];
    }

    @Override
    @NotNull
    public Map<Long, String> getAlertMessages(@NotNull LongStream alertIds) {
        LOGGER.debug("getAlertMessages {}", alertIds);
        var alertIdSet = alertIds.boxed().collect(toSet());
        return alertIdSet.isEmpty() ? emptyMap() :
                alerts.values().stream().filter(alert -> alertIdSet.contains(alert.id))
                        .collect(toMap(Alert::getId, Alert::getMessage));
    }

    @Override
    @NotNull
    public Map<String, Set<String>> getPairsByExchangesHavingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin) {
        LOGGER.debug("getPairsByExchangesHavingPastListeningDateWithActiveRange {} {}", now, checkPeriodMin);
        return havingPastListeningDateWithActiveRange(now, checkPeriodMin, alerts.values().stream())
                .collect(groupingBy(Alert::getExchange, mapping(Alert::getPair, toSet())));
    }

    @Override
    public long countAlertsOfUser(long userId) {
        LOGGER.debug("countAlertsOfUser {}", userId);
        return getAlertsOfUserStream(userId).count();
    }

    @Override
    public long countAlertsOfUserAndTickers(long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfUserAndTickers {} {}", userId, tickerOrPair);
        return getAlertsOfUserAndTickersStream(userId, tickerOrPair).count();
    }

    @Override
    public long countAlertsOfServer(long serverId) {
        LOGGER.debug("countAlertsOfServer {}", serverId);
        return getAlertsOfServerStream(serverId).count();
    }

    @Override
    public long countAlertsOfServerAndUser(long serverId, long userId) {
        LOGGER.debug("countAlertsOfServerAndUser {} {}", serverId, userId);
        return getAlertsOfServerAndUserStream(serverId, userId).count();
    }

    @Override
    public long countAlertsOfServerAndTickers(long serverId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndTickers {} {}", serverId, tickerOrPair);
        return getAlertsOfServerAndTickersStream(serverId, tickerOrPair).count();
    }

    @Override
    public long countAlertsOfServerAndUserAndTickers(long serverId, long userId, @NotNull String tickerOrPair) {
        LOGGER.debug("countAlertsOfServerAndUserAndTickers {} {} {}", serverId, userId, tickerOrPair);
        return getAlertsOfServerAndUserAndTickersStream(serverId, userId, tickerOrPair).count();
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserOrderByPairId(long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfUserOrderByPairId {} {} {}", userId, offset, limit);
        return getAlertsOfUserStream(userId).sorted(comparing(Alert::getPair).thenComparing(Alert::getId))
                .skip(offset).limit(limit).toList();
    }

    private Stream<Alert> getAlertsOfUserStream(long userId) {
        return alerts.values().stream().filter(alert -> alert.userId == userId);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfUserAndTickersOrderById(long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfUserAndTickersOrderById {} {} {} {}", userId, offset, limit, tickerOrPair);
        return getAlertsOfUserAndTickersStream(userId, tickerOrPair).sorted(comparing(Alert::getId))
                .skip(offset).limit(limit).toList();
    }

    private Stream<Alert> getAlertsOfUserAndTickersStream(long userId, @NotNull String tickerOrPair) {
        requireNonNull(tickerOrPair);
        return alerts.values().stream()
                .filter(alert -> alert.userId == userId &&
                        alert.pair.contains(tickerOrPair));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerOrderByPairUserIdId(long serverId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerOrderByPairUserIdId {} {} {}", serverId, offset, limit);
        return getAlertsOfServerStream(serverId).sorted(comparing(Alert::getPair).thenComparing(Alert::getUserId).thenComparing(Alert::getId))
                .skip(offset).limit(limit).toList();
    }

    private Stream<Alert> getAlertsOfServerStream(long serverId) {
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserOrderByPairId(long serverId, long userId, long offset, long limit) {
        LOGGER.debug("getAlertsOfServerAndUserOrderByPairId {} {} {} {}", serverId, userId, offset, limit);
        return getAlertsOfServerAndUserStream(serverId, userId).sorted(comparing(Alert::getPair).thenComparing(Alert::getId))
                .skip(offset).limit(limit).toList();
    }

    private Stream<Alert> getAlertsOfServerAndUserStream(long serverId, long userId) {
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId &&
                        alert.userId == userId);
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndTickersOrderByUserIdId(long serverId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndTickersOrderByUserIdId {} {} {} {}", serverId, offset, limit, tickerOrPair);
        return getAlertsOfServerAndTickersStream(serverId, tickerOrPair).sorted(comparing(Alert::getUserId).thenComparing(Alert::getId))
                .skip(offset).limit(limit).toList();
    }

    private Stream<Alert> getAlertsOfServerAndTickersStream(long serverId, @NotNull String tickerOrPair) {
        requireNonNull(tickerOrPair);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId &&
                        alert.pair.contains(tickerOrPair));
    }

    @Override
    @NotNull
    public List<Alert> getAlertsOfServerAndUserAndTickersOrderById(long serverId, long userId, long offset, long limit, @NotNull String tickerOrPair) {
        LOGGER.debug("getAlertsOfServerAndUserAndTickersOrderById {} {} {} {} {}", serverId, userId, offset, limit, tickerOrPair);
        return getAlertsOfServerAndUserAndTickersStream(serverId, userId, tickerOrPair).sorted(comparing(Alert::getId))
                .skip(offset).limit(limit).toList();
    }

    private Stream<Alert> getAlertsOfServerAndUserAndTickersStream(long serverId, long userId, @NotNull String tickerOrPair) {
        requireNonNull(tickerOrPair);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId &&
                        alert.userId == userId &&
                        alert.pair.contains(tickerOrPair));
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
        requireNonNull(tickerOrPair);
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
        requireNonNull(fromPrice);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withFromPrice(fromPrice));
    }

    @Override
    public void updateToPrice(long alertId, @NotNull BigDecimal toPrice) {
        LOGGER.debug("updateToPrice {} {}", alertId, toPrice);
        requireNonNull(toPrice);
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
        requireNonNull(message);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withMessage(message));
    }

    @Override
    public void updateMargin(long alertId, @NotNull BigDecimal margin) {
        LOGGER.debug("updateMargin {} {}", alertId, margin);
        requireNonNull(margin);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withMargin(margin));
    }

    @Override
    public void updateListeningDateRepeat(long alertId, @Nullable ZonedDateTime listeningDate, short repeat) {
        LOGGER.debug("updateListeningDateRepeat {} {} {}", alertId, listeningDate, repeat);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withListeningDateRepeat(listeningDate, repeat));
    }

    @Override
    public void updateListeningDateSnooze(long alertId, @Nullable ZonedDateTime listeningDate, short snooze) {
        LOGGER.debug("updateListeningDateSnooze {} {} {}", alertId, listeningDate, snooze);
        alerts.computeIfPresent(alertId, (id, alert) -> alert.withListeningDateSnooze(listeningDate, snooze));
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
        requireNonNull(tickerOrPair);
        long[] removedAlerts = new long[] {0L};
        alerts.entrySet().removeIf(entry ->
                entry.getValue().userId == userId &&
                entry.getValue().serverId == serverId &&
                entry.getValue().pair.contains(tickerOrPair) &&
                ++removedAlerts[0] != 0);
        return removedAlerts[0];
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        requireNonNull(now);
        updater.accept(ids -> alerts.computeIfPresent((Long) ids.get("id"),
                (id, alert) -> alert.withListeningDateLastTriggerMarginRepeat(
                        hasRepeat(alert.repeat - 1) ? now.plusHours(alert.snooze) : null, // listening date
                        now, // last trigger
                        MARGIN_DISABLED, (short) (hasRepeat(alert.repeat) ? alert.repeat - 1 : 0))));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        requireNonNull(now);
        updater.accept(ids -> alerts.computeIfPresent((Long) ids.get("id"),
                (id, alert) -> alert.withLastTriggerMargin(now, MARGIN_DISABLED)));
    }

    @Override
    public void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("alertBatchDeletes");
        deleter.accept(ids -> alerts.remove((Long) ids.get("id")));
    }
}
