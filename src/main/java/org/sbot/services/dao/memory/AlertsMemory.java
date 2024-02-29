package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.UsersDao;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.services.dao.BatchEntry.longId;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class AlertsMemory implements AlertsDao {

    private static final Logger LOGGER = LogManager.getLogger(AlertsMemory.class);

    private final Map<Long, Alert> alerts = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1L);

    public final UsersDao usersDao;

    public AlertsMemory() {
        LOGGER.debug("Loading memory storage for alerts");
        this.usersDao = new UsersMemory(this);
    }

    @NotNull
    static Predicate<Alert> asSearchFilter(@NotNull SelectionFilter filter) {
        return Stream.<Optional<Predicate<Alert>>>of(
                        Optional.ofNullable(filter.serverId()).map(serverId -> alert -> alert.serverId == serverId),
                        Optional.ofNullable(filter.userId()).map(userId -> alert -> alert.userId == userId),
                        Optional.ofNullable(filter.type()).map(type -> alert -> alert.type == type),
                        Optional.ofNullable(filter.tickerOrPair()).map(tickerOrPair -> alert -> alert.pair.contains(tickerOrPair)))
                .flatMap(Optional::stream).reduce(Predicate::and).orElse(alert -> true);
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
    public long fetchAlertsHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(@NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore {}", expirationDate);
        requireNonNull(expirationDate);
        long[] read = new long[] {0L};
        Predicate<Alert> predicate = alert -> alert.repeat < 0 &&
                    ((null != alert.lastTrigger && alert.lastTrigger.isBefore(expirationDate)) ||
                    (null == alert.lastTrigger && alert.creationDate.isBefore(expirationDate))) &&
                ++read[0] != 0;
        alertsConsumer.accept(alerts.values().stream().filter(predicate));
        return read[0];
    }

    @Override
    public long fetchAlertsByTypeHavingToDateBefore(@NotNull Type type, @NotNull ZonedDateTime expirationDate, @NotNull Consumer<Stream<Alert>> alertsConsumer) {
        LOGGER.debug("fetchAlertsByTypeHavingToDateBefore {} {}", type, expirationDate);
        requireNonNull(type); requireNonNull(expirationDate);
        long[] read = new long[] {0L};
        Predicate<Alert> typeAndToDateBefore = alert -> alert.type == type && null != alert.toDate &&
                alert.toDate.isBefore(expirationDate) && ++read[0] != 0;
        alertsConsumer.accept(alerts.values().stream().filter(typeAndToDateBefore));
        return read[0];
    }

    @Override
    @NotNull
    public Map<String, Set<String>> getPairsByExchangesHavingPastListeningDateWithActiveRange(@NotNull ZonedDateTime now, int checkPeriodMin) {
        LOGGER.debug("getPairsByExchangesHavingPastListeningDateWithActiveRange {} {}", now, checkPeriodMin);
        return havingPastListeningDateWithActiveRange(now, checkPeriodMin, alerts.values().stream())
                .collect(groupingBy(Alert::getExchange, mapping(Alert::getPair, toSet())));
    }

    @Override
    @NotNull
    public List<Long> getUserIdsByServerId(long serverId) {
        LOGGER.debug("getUserIdsByServerId {}", serverId);
        return alerts.values().stream()
                .filter(alert -> alert.serverId == serverId)
                .map(Alert::getUserId).toList();
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
    @NotNull
    public Map<Long, String> getAlertMessages(@NotNull LongStream alertIds) {
        var alertIdSet = alertIds.boxed().collect(toSet());
        LOGGER.debug("getAlertMessages {}", alertIdSet);
        return alertIdSet.isEmpty() ? emptyMap() :
                alerts.values().stream().filter(alert -> alertIdSet.contains(alert.id))
                        .collect(toMap(Alert::getId, Alert::getMessage));
    }

    @Override
    public long countAlerts(@NotNull SelectionFilter filter) {
        LOGGER.debug("countAlerts {}", filter);
        return getAlertsStream(filter).count();
    }

    Stream<Alert> getAlertsStream(@NotNull SelectionFilter filter) {
        return alerts.values().stream().filter(asSearchFilter(filter));
    }

    @NotNull
    @Override
    public List<Alert> getAlertsOrderByPairUserIdId(@NotNull SelectionFilter filter, long offset, long limit) {
        var order = null != filter.userId() ?
                comparing(Alert::getPair).thenComparing(Alert::getId) :
                comparing(Alert::getPair).thenComparing(Alert::getUserId).thenComparing(Alert::getId);
        return getAlertsStream(filter).sorted(order).skip(offset).limit(limit).toList();
    }

    @Override
    public long addAlert(@NotNull Alert alert) {
        if(NEW_ALERT_ID != alert.id) {
            throw new IllegalArgumentException("Alert id is not new : " + alert);
        } else if(!usersDao.userExists(alert.userId)) {
            throw new IllegalArgumentException("Alert reference an user not found in userDao : " + alert);
        }
        alert = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}", alert);
        alerts.put(alert.id, alert);
        return alert.id;
    }

    @Override
    // memory dao directly store the provided alert
    public void update(@NotNull Alert alert, @NotNull Set<UpdateField> fields) {
        LOGGER.debug("update {} {}", fields, alert);
        requireNonNull(fields); // unused, simulate sql layer
        alerts.computeIfPresent(alert.id, (id, a) -> alert);
    }

    @Override
    public long updateServerIdOf(@NotNull SelectionFilter filter, long newServerId) {
        LOGGER.debug("updateServerIdOf {} {}", filter, newServerId);
        long[] updated = new long[] {0L};
        var selection = asSearchFilter(filter).and(a -> ++updated[0] != 0);
        alerts.replaceAll((alertId, alert) ->
                selection.test(alert) ? alert.withServerId(newServerId) : alert);
        return updated[0];
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        alerts.remove(alertId);
    }

    @Override
    public long deleteAlerts(@NotNull SelectionFilter filter) {
        LOGGER.debug("deleteAlerts {}", filter);
        long[] deleted = new long[] {0L};
        alerts.values().removeIf(asSearchFilter(filter).and(a -> ++deleted[0] != 0));
        return deleted[0];
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        requireNonNull(now);
        updater.accept(ids -> alerts.computeIfPresent(longId(ids),
                (id, alert) -> alert.withListeningDateLastTriggerMarginRepeat(
                        alert.repeat >= 0 ? now.plusHours(alert.snooze) : null, // listening date
                        now, // last trigger
                        MARGIN_DISABLED, (short) (alert.repeat - 1))));
    }

    @Override
    public void marginAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("marginAlertBatchUpdates");
        requireNonNull(now);
        updater.accept(ids -> alerts.computeIfPresent(longId(ids),
                (id, alert) -> alert.withLastTriggerMargin(now, MARGIN_DISABLED)));
    }

    @Override
    public void alertBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("alertBatchDeletes");
        deleter.accept(ids -> alerts.remove(longId(ids)));
    }
}
