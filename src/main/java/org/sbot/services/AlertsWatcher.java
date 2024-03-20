package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.User;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.Candlestick.CandlestickPeriod;
import org.sbot.entities.chart.TimeFrame;
import org.sbot.entities.notifications.DeletedNotification;
import org.sbot.entities.notifications.MatchingNotification;
import org.sbot.exchanges.Exchange;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.context.Context;
import org.sbot.services.context.TransactionalContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.discord.Discord;
import org.sbot.utils.Dates;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static org.sbot.SpotBot.appProperties;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE;
import static org.sbot.entities.chart.Candlestick.periodSince;
import static org.sbot.services.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.PartitionSpliterator.split;

public final class AlertsWatcher {

    private static final Logger LOGGER = LogManager.getLogger(AlertsWatcher.class);

    private static final int MAX_DBMS_SQL_IN_CLAUSE_VALUES = 1000;
    private static final int STREAM_BUFFER_SIZE = Math.min(MAX_DBMS_SQL_IN_CLAUSE_VALUES, MESSAGE_PAGE_SIZE);

    public static final int DONE_ALERTS_DELAY_WEEKS = Math.max(1, appProperties.getIntOr("alerts.done.drop.delay.weeks", 1));
    private static final int EXPIRED_ALERTS_DELAY_WEEKS = Math.max(1, appProperties.getIntOr("alerts.expired.drop.delay.weeks", 2));
    private static final int NOTIFICATIONS_DELETE_DELAY_MONTHS = Math.max(1, appProperties.getIntOr("notifications.delete.delay.months", 6));
    private static final int USERS_LAST_ACCESS_DELAY_MONTHS = Math.max(1, appProperties.getIntOr("users.last-access.drop.delay.months", 6));

    private final Context context;

    public AlertsWatcher(Context context) {
        this.context = requireNonNull(context);
    }

    // this splits in tasks by exchanges and pairs, one rest call must be done by each task to retrieve the candlesticks
    public void checkAlerts() {
        long start = System.currentTimeMillis();
        try {
            ZonedDateTime now = Dates.nowUtc(context.clock());
            context.transaction(txCtx -> expiredNotificationsCleanup(txCtx.notificationsDao(), now));
            context.transaction(txCtx -> expiredUsersCleanup(txCtx.usersDao(), now));
            if(context.transactional(txCtx -> expiredAlertsCleanup(txCtx, now)) > 0) {
                context.notificationService().sendNotifications();
            }

            //TODO check user not on server anymore
            var exchangePairs = context.transactional(txCtx -> {
                var pairs = pairsToCheck(txCtx.alertsDao(), now);
                removeUnusedLastCandlestick(txCtx, pairs);
                return pairs;
            });
            var matchingAlerts = new AtomicLong(0L);
            var tasks = new ArrayList<Callable<Void>>(exchangePairs.size());
            exchangePairs.forEach((xchange, pairs) -> context.exchanges().get(xchange).ifPresentOrElse(exchange -> {
                if (exchange.isVirtual()) {
                    tasks.add(() -> { pairs.forEach(pair -> matchingAlerts.addAndGet(raiseAlerts(now, exchange, pair))); return null; });
                } else { // one task by exchange
                    tasks.add(() -> { pairs.forEach(pair -> matchingAlerts.addAndGet(getPricesAndRaiseAlerts(now, exchange, pair))); return null; });
                }
            }, () -> LOGGER.warn("Unknown exchange : {}", xchange)));
            try(var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.invokeAll(tasks);
                LOGGER.info("Alerts check done, {}ms. Found {} matching alerts", System.currentTimeMillis() - start, matchingAlerts.get());
            } finally {
                if(matchingAlerts.get() > 0) {
                    context.notificationService().sendNotifications();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception thrown while performing check alerts task", e);
        }
    }

    void expiredNotificationsCleanup(@NotNull NotificationsDao notificationsDao, @NotNull ZonedDateTime now) {
        ZonedDateTime expirationDate = now.minusMonths(NOTIFICATIONS_DELETE_DELAY_MONTHS);
        long deleted = notificationsDao.deleteHavingCreationDateBefore(expirationDate);
        LOGGER.debug("Deleting {} notifications with creation date < {}", deleted, expirationDate);
    }

    void expiredUsersCleanup(@NotNull UsersDao usersDao, @NotNull ZonedDateTime now) {
        ZonedDateTime expirationDate = now.minusMonths(USERS_LAST_ACCESS_DELAY_MONTHS);
        long deleted = usersDao.deleteHavingLastAccessBeforeAndNotInAlerts(expirationDate);
        LOGGER.debug("Deleting {} users with last access < {}", deleted, expirationDate);
    }

    long expiredAlertsCleanup(@NotNull TransactionalContext txCtx, @NotNull ZonedDateTime now) {
        var alertsDao = txCtx.alertsDao();
        Consumer<Stream<Alert>> alertsDeleter = alertDeleter(now, alertsDao, txCtx.notificationsDao(), txCtx.usersDao());
        ZonedDateTime expirationDate = now.minusWeeks(DONE_ALERTS_DELAY_WEEKS);
        // filter by repeat < 0 instead of listeningDate null to distinguish case where user disabled the alert but want to keep it
        long total;
        long deleted = total = alertsDao.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(expirationDate, alertsDeleter);
        LOGGER.debug("Deleting {} alerts with repeat < 0 and lastTrigger or creation date < {}", deleted, expirationDate);
        expirationDate = now.minusWeeks(EXPIRED_ALERTS_DELAY_WEEKS);
        total += deleted = alertsDao.fetchAlertsWithoutMessageByTypeHavingToDateBefore(trend, expirationDate, alertsDeleter);
        LOGGER.debug("Deleting {} trend alerts with toDate < {}", deleted, expirationDate);
        total += deleted = alertsDao.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, expirationDate, alertsDeleter);
        LOGGER.debug("Deleting {} range alerts with toDate < {}", deleted, expirationDate);
        return total;
    }

    @NotNull
    private Consumer<Stream<Alert>> alertDeleter(@NotNull ZonedDateTime now, @NotNull AlertsDao alertsDao, @NotNull NotificationsDao notificationsDao, @NotNull UsersDao usersDao) {
        var userLocales = new HashMap<Long, Locale>();
        var guildNames = new HashMap<Long, String>();
        Function<Long, Locale> userLocale = id -> usersDao.getUser(id).map(User::locale).orElse(DEFAULT_LOCALE);
        Function<Long, String> guildName = id -> context.discord().guildServer(id).map(Discord::guildName).orElse("unknown");
        return alerts -> alertsDao.delete(deleter ->
                        alerts.forEach(alert -> {
                            var locale = userLocales.computeIfAbsent(alert.userId, userLocale);
                            var serverName =  isPrivate(alert.serverId) ? null : guildNames.computeIfAbsent(alert.serverId, guildName);
                            notificationsDao.addNotification(DeletedNotification.of(now, locale, alert.userId, alert.id, alert.type, alert.pair, serverName, 1L, true));
                            deleter.batchId(alert.id);
                        }));
    }

    private Map<String, Set<String>> pairsToCheck(@NotNull AlertsDao alertsDao, @NotNull ZonedDateTime now) {
        return alertsDao.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, context.parameters().checkPeriodMin());
    }

    private void removeUnusedLastCandlestick(@NotNull TransactionalContext txCtx, @NotNull Map<String, Set<String>> activeExchangePairs) {
        var candlesticks = txCtx.lastCandlesticksDao().getPairsByExchanges();
        txCtx.lastCandlesticksService().delete(deleter ->
                candlesticks.forEach((exchange, pairs) -> {
                    var found = activeExchangePairs.get(exchange);
                    pairs.forEach(pair -> {
                        if(null == found || !found.contains(pair)) {
                            deleter.batch(exchange, pair);
                        }
                    });
                }));
    }

    private long raiseAlerts(@NotNull ZonedDateTime now, @NotNull Exchange virtualExchange, @NotNull String pair) {
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Processing pair [{}] on virtual exchange {}{}...", pair,
                    virtualExchange.name(), REMAINDER_VIRTUAL_EXCHANGE.equals(virtualExchange.name()) ? " (Remainder Alerts)" : "");
        }
        try {
            return context.transactional(ctx -> processMatchingAlerts(ctx, now, virtualExchange, pair, emptyList(), null));
        } catch (RuntimeException e) {
            LOGGER.error("Exception thrown while processing alerts for " + pair + " on virtual exchange " + virtualExchange.name(), e);
            return 0L;
        }
    }

    private long getPricesAndRaiseAlerts(@NotNull ZonedDateTime now, @NotNull Exchange exchange, @NotNull String pair) {
        try {
            LOGGER.debug("Retrieving last price for pair [{}] on {}...", pair, exchange);

            // retrieve all the candlesticks since the last check occurred from now, or since the last hour,
            // lastClose database read don't need to be part of the following alerts update transaction
            var lastClose = context.transactional(ctx -> ctx.lastCandlesticksDao().getLastCandlestickCloseTime(exchange.name(), pair).orElse(null));
            List<Candlestick> prices = getCandlesticksSince(lastClose, now, exchange, pair);

            if(!prices.isEmpty()) {
                // load, notify, and update alerts that matches
                return context.transactional(txContext -> {
                    var lastCandlesticksService = txContext.lastCandlesticksService();
                    Candlestick previousPrice = lastCandlesticksService.getLastCandlestick(exchange.name(), pair).orElse(null);
                    long matching = processMatchingAlerts(txContext, now, exchange, pair, prices, previousPrice);
                    lastCandlesticksService.updateLastCandlestick(exchange.name(), pair, previousPrice, prices.get(prices.size() - 1));
                    return matching;
                });
            } else {
                LOGGER.warn("No market data found for {} on exchange {}", pair, exchange);
            }
        } catch(RuntimeException e) {
            LOGGER.error("Exception thrown while processing alerts for " + pair + " on exchange " + exchange, e);
        }
        return 0L;
    }

    private List<Candlestick> getCandlesticksSince(@Nullable ZonedDateTime previousCloseTime, @NotNull ZonedDateTime now, @NotNull Exchange exchange, @NotNull String pair) {
        CandlestickPeriod period = Optional.ofNullable(previousCloseTime)
                .map(close -> periodSince(close, now))
                .orElse(CandlestickPeriod.ONE_MINUTE); //TODO retrieve since last period
        LOGGER.debug("Computed {} daily, {} hourly and {} one minute candlesticks since the last price close time {}",
                period.daily(), period.hourly(), period.minutes(), previousCloseTime);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Candlestick> prices = new ArrayList<>(period.daily() + period.hourly() + period.minutes());
            Future<List<Candlestick>> daily = completedFuture(emptyList()), hourly = daily;
            if (period.daily() > 0) {
                daily = executor.submit(() -> getCandlesticks(exchange, pair, TimeFrame.DAILY, period.daily()));
            }
            if (period.hourly() > 0) {
                hourly = executor.submit(() -> getCandlesticks(exchange, pair, TimeFrame.HOURLY, period.hourly()));
            }
            var minutes = getCandlesticks(exchange, pair, TimeFrame.ONE_MINUTE, period.minutes());
            // longer timeframe can overlap the shorter, they should not be mixed by sorting all the candlestick once.
            // this is best effort for retrieving quotes since a long shutdown, where longer timeframe are likely to overlap
            // before the alert listening date, in which case they are ignored
            // one minute timeframe quote won't be available for many days in the past
            // Usually only bulks of one minute quote are requested, on following checks, hence no overlap issue.
            // check period should remains below one hour to ensure use of one minute timeframe.
            //
            prices.addAll(daily.get().stream().sorted(comparing(Candlestick::closeTime)).toList());
            prices.addAll(hourly.get().stream().sorted(comparing(Candlestick::closeTime)).toList());
            prices.addAll(minutes.stream().sorted(comparing(Candlestick::closeTime)).toList());
            return prices;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Candlestick> getCandlesticks(@NotNull Exchange exchange, @NotNull String pair, @NotNull TimeFrame timeFrame, int limit) {
        var candlesticks  = exchange.getCandlesticks(pair, timeFrame, limit);
        if(candlesticks.isEmpty()) {
            throw new IllegalStateException("No " + timeFrame.name() + " candlestick found for pair " + pair + " on exchange" + exchange.name() + " with limit " + limit);
        }
        return candlesticks;
    }

    private long processMatchingAlerts(@NotNull TransactionalContext context, @NotNull ZonedDateTime now, @NotNull Exchange exchange, @NotNull String pair, @NotNull List<Candlestick> prices, @Nullable Candlestick previousPrice) {
        var alertsDao = context.alertsDao();
        var notificationsDao = context.notificationsDao();
        var usersDao = context.usersDao();
        var matchingService = context.matchingService();
        long[] matching = new long[1];
        long read = alertsDao.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(exchange.name(), pair, now, context.parameters().checkPeriodMin(),
                alerts -> batchAlertsUpdates(alertsDao, now,
                        split(STREAM_BUFFER_SIZE, true, alerts
                                .map(alert -> matchingService.match(now, alert, prices, previousPrice))
                                .filter(alert -> alert.hasMatch() && ++matching[0] != 0))
                        .map(matchingAlerts -> fetchAlertsMessage(alertsDao, matchingAlerts))
                        .flatMap(matchingAlerts -> sendNotifications(now, notificationsDao, usersDao, matchingAlerts))));
        LOGGER.debug("Processed {} alerts on exchange {} and pair {}, found {} matching", read, exchange.name(), pair, matching[0]);
        return matching[0];
    }

    private void batchAlertsUpdates(@NotNull AlertsDao alertsDao, @NotNull ZonedDateTime now, @NotNull Stream<MatchingAlert> matchingAlerts) {
        alertsDao.matchedAlertBatchUpdates(now, matchedUpdater ->
                alertsDao.marginAlertBatchUpdates(now, marginUpdater ->
                        matchingAlerts.forEach(matchingAlert -> {
                            Alert alert = matchingAlert.alert();
                            (switch (matchingAlert.status()) {
                                case MATCHED -> matchedUpdater;
                                case MARGIN -> marginUpdater;
                                case NOT_MATCHING -> (BatchEntry) Function.identity();
                            }).batchId(alert.id);
                        })));
    }

    private List<MatchingAlert> fetchAlertsMessage(@NotNull AlertsDao alertsDao, @NotNull List<MatchingAlert> matchingAlerts) {
        LongStream alertIds = matchingAlerts.stream().mapToLong(matchingAlert -> matchingAlert.alert().id);
        var alertIdMessages = alertsDao.getAlertMessages(alertIds);
        return matchingAlerts.stream().map(matchingAlert ->
                matchingAlert.withAlert(matchingAlert.alert()
                        .withMessage(alertIdMessages.getOrDefault(matchingAlert.alert().id, matchingAlert.alert().message))))
                .toList();
    }

    private Stream<MatchingAlert> sendNotifications(@NotNull ZonedDateTime now, @NotNull NotificationsDao notificationsDao, @NotNull UsersDao usersDao, @NotNull List<MatchingAlert> matchingAlerts) {
        var userIds = matchingAlerts.stream().map(MatchingAlert::alert).map(Alert::getUserId).toList();
        var userLocales = usersDao.getLocales(userIds);
        matchingAlerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().serverId))
                .forEach((serverId, alertsList) -> // group by serverId
                        alertsList.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().userId))
                                .forEach((userId, alerts) -> // then group by userId
                                        sendNotifications(now, notificationsDao, userLocales.getOrDefault(userId, DEFAULT_LOCALE), alerts)));
        return matchingAlerts.stream();
    }

    private void sendNotifications(@NotNull ZonedDateTime now, @NotNull NotificationsDao notificationsDao, @NotNull Locale locale, @NotNull List<MatchingAlert> matchingAlerts) {
        matchingAlerts.forEach(matchingAlert -> notificationsDao.addNotification(
                MatchingNotification.of(now, locale, matchingAlert.status(), matchingAlert.alert(),
                        Optional.ofNullable(matchingAlert.matchingCandlestick()).map(Candlestick::datedClose).orElse(null))));
    }
}
