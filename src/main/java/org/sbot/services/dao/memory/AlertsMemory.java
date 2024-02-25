package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.UsersDao;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
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
    static Predicate<Alert> selectionFilter(@NotNull SelectionFilter filter) {
        var selection = new ArrayList<Predicate<Alert>>(4);
        BiConsumer<BiPredicate<Alert, Object>, Object> put = (predicate, value) -> {
            if (null != value) {
                selection.add(alert -> predicate.test(alert, value));
            }
        };
        put.accept((alert, v) -> alert.serverId == (Long) v, filter.serverId());
        put.accept((alert, v) -> alert.userId == (Long) v, filter.userId());
        put.accept((alert, v) -> alert.type == v, filter.type());
        put.accept((alert, v) -> alert.pair.contains((String) v), filter.tickerOrPair());
        return selection.stream().reduce(Predicate::and).orElse(a -> true);
    }

    @NotNull
    static Function<Alert, Alert> fieldsMapper(@NotNull Map<UpdateField, Object> fields) {
        return fields.entrySet().stream().<Function<Alert, Alert>>map(entry -> switch (entry.getKey()) {
            case SERVER_ID ->  alert -> alert.withServerId((Long) entry.getValue());
            case LISTENING_DATE -> alert -> alert.withListeningDateRepeat((ZonedDateTime) entry.getValue(), alert.repeat);
            case FROM_PRICE -> alert -> alert.withFromPrice((BigDecimal) entry.getValue());
            case TO_PRICE -> alert -> alert.withToPrice((BigDecimal) entry.getValue());
            case FROM_DATE -> alert -> alert.withFromDate((ZonedDateTime) entry.getValue());
            case TO_DATE -> alert -> alert.withToDate((ZonedDateTime) entry.getValue());
            case MESSAGE -> alert -> alert.withMessage((String) entry.getValue());
            case MARGIN -> alert -> alert.withMargin((BigDecimal) entry.getValue());
            case REPEAT -> alert -> alert.withListeningDateRepeat(alert.listeningDate, (short) entry.getValue());
            case SNOOZE -> alert -> alert.withSnooze((short) entry.getValue());
        }).reduce(Function::andThen).orElse(identity());
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
        return alerts.values().stream().filter(selectionFilter(filter));
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
        if(!usersDao.userExists(alert.userId)) {
            throw new IllegalArgumentException("Alert reference an user not found in userDao : " + alert);
        }
        alert = alert.withId(idGenerator::getAndIncrement);
        LOGGER.debug("addAlert {}, with new id {}", alert, alert.id);
        alerts.put(alert.id, alert);
        return alert.id;
    }

    @Override
    public void update(long alertId, @NotNull Map<UpdateField, Object> fields) {
        LOGGER.debug("update {} {}", alertId, fields);
        requireNonNull(fields);
        alerts.computeIfPresent(alertId, (id, alert) -> fieldsMapper(fields).apply(alert));
    }

    @Override
    public long updateServerIdOf(@NotNull SelectionFilter filter, long newServerId) {
        LOGGER.debug("updateServerIdOf {} {}", filter, newServerId);
        var selection = selectionFilter(filter);
        long[] removedAlerts = new long[] {0L};
        alerts.replaceAll((alertId, alert) ->
                selection.test(alert) && ++removedAlerts[0] != 0 ? alert.withServerId(newServerId) : alert);
        return removedAlerts[0];
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("deleteAlert {}", alertId);
        alerts.remove(alertId);
    }

    @Override
    public long deleteAlerts(@NotNull SelectionFilter filter) {
        LOGGER.debug("deleteAlerts {}", filter);
        var selection = selectionFilter(filter);
        long[] removedAlerts = new long[] {0L};
        alerts.values().removeIf(alert -> selection.test(alert) && ++removedAlerts[0] != 0);
        return removedAlerts[0];
    }

    @Override
    public void matchedAlertBatchUpdates(@NotNull ZonedDateTime now, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("matchedAlertBatchUpdates");
        requireNonNull(now);
        updater.accept(ids -> alerts.computeIfPresent(longId(ids),
                (id, alert) -> alert.withListeningDateLastTriggerMarginRepeat(
                        hasRepeat(alert.repeat - 1L) ? now.plusHours(alert.snooze) : null, // listening date
                        now, // last trigger
                        MARGIN_DISABLED, (short) (hasRepeat(alert.repeat) ? alert.repeat - 1 : 0))));
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
