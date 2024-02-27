package org.sbot.services;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.Candlestick.CandlestickPeriod;
import org.sbot.entities.chart.TimeFrame;
import org.sbot.exchanges.Exchange;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.services.context.Context;
import org.sbot.services.context.TransactionalContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.UsersDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static org.sbot.SpotBot.appProperties;
import static org.sbot.commands.CommandAdapter.NOTIFICATION_COLOR;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE;
import static org.sbot.entities.chart.Candlestick.periodSince;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.services.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.services.discord.Discord.spotBotRole;
import static org.sbot.utils.PartitionSpliterator.split;

public final class AlertsWatcher {

    private static final Logger LOGGER = LogManager.getLogger(AlertsWatcher.class);

    private static final int MAX_DBMS_SQL_IN_CLAUSE_VALUES = 1000;
    private static final int STREAM_BUFFER_SIZE = Math.min(MAX_DBMS_SQL_IN_CLAUSE_VALUES, MESSAGE_PAGE_SIZE);

    private static final int DONE_DELAY_WEEKS = Math.max(1, appProperties.getIntOr("alerts.done.drop.delay.weeks", 1));
    private static final int EXPIRED_DELAY_WEEKS = Math.max(1, appProperties.getIntOr("alerts.expired.drop.delay.weeks", 2));
    private static final int LAST_ACCESS_DELAY_MONTHS = Math.max(1, appProperties.getIntOr("users.last-access.drop.delay.months", 6));

    private final Context context;

    public AlertsWatcher(Context context) {
        this.context = requireNonNull(context);
    }

    // this splits in tasks by exchanges and pairs, one rest call must be done by each task to retrieve the candlesticks
    public void checkAlerts() {
        long start = System.currentTimeMillis();
        try {
            ZonedDateTime now = Dates.nowUtc(context.clock());
            context.transaction(txCtx -> expiredUsersCleanup(txCtx.usersDao(), now));
            context.transaction(txCtx -> expiredAlertsCleanup(txCtx.alertsDao(), now));

            //TODO check user not on server anymore
            var exchangePairs = context.transactional(txCtx -> {
                var pairs = pairsToCheck(txCtx.alertsDao(), now);
                removeUnusedLastCandlestick(txCtx, pairs);
                return pairs;
            });
            var tasks = new ArrayList<Callable<Void>>(exchangePairs.size());
            exchangePairs.forEach((xchange, pairs) -> context.exchanges().get(xchange).ifPresentOrElse(exchange -> {
                if (exchange.isVirtual()) {
                    tasks.add(() -> { pairs.forEach(pair -> raiseAlerts(now, exchange, pair)); return null; });
                } else { // one task by exchange
                    tasks.add(() -> { pairs.forEach(pair -> getPricesAndRaiseAlerts(now, exchange, pair)); return null; });
                }
            }, () -> LOGGER.warn("Unknown exchange : " + xchange)));
            try(var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.invokeAll(tasks);
                LOGGER.info("Alerts check done, {}ms.", System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            LOGGER.error("Exception thrown while performing check alerts task", e);
        }
    }

    void expiredUsersCleanup(@NotNull UsersDao usersDao, @NotNull ZonedDateTime now) {
        ZonedDateTime expirationDate = now.minusMonths(LAST_ACCESS_DELAY_MONTHS);
        long deleted = usersDao.deleteHavingLastAccessBeforeAndNotInAlerts(expirationDate);
        LOGGER.debug("Deleted {} users with last access < {}", deleted, expirationDate);
    }

    void expiredAlertsCleanup(@NotNull AlertsDao alertsDao, @NotNull ZonedDateTime now) {
        Consumer<Stream<Alert>> alertsDeleter = alerts -> alertsDao.alertBatchDeletes(deleter ->
                split(STREAM_BUFFER_SIZE, true, alerts
                        .map(alert -> new MatchingAlert(alert, NOT_MATCHING, null)))
                        .flatMap(matchingAlerts -> sendDiscordNotifications(now, matchingAlerts))
                        .forEach(matchingAlert -> deleter.batchId(matchingAlert.alert().id)));

        ZonedDateTime expirationDate = now.minusWeeks(DONE_DELAY_WEEKS);
        long deleted = alertsDao.fetchAlertsHavingRepeatZeroAndLastTriggerBeforeOrNullAndCreationBefore(expirationDate, alertsDeleter);
        LOGGER.debug("Deleted {} alerts with repeat = 0 and lastTrigger or creation date < {}", deleted, expirationDate);
        expirationDate = now.minusWeeks(EXPIRED_DELAY_WEEKS);
        deleted = alertsDao.fetchAlertsByTypeHavingToDateBefore(trend, expirationDate, alertsDeleter);
        LOGGER.debug("Deleted {} trend alerts with toDate < {}", deleted, expirationDate);
        deleted = alertsDao.fetchAlertsByTypeHavingToDateBefore(range, expirationDate, alertsDeleter);
        LOGGER.debug("Deleted {} range alerts with toDate < {}", deleted, expirationDate);
    }

    private Map<String, Set<String>> pairsToCheck(@NotNull AlertsDao alertsDao, @NotNull ZonedDateTime now) {
        return alertsDao.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, context.parameters().checkPeriodMin());
    }

    private void removeUnusedLastCandlestick(@NotNull TransactionalContext txCtx, @NotNull Map<String, Set<String>> activeExchangePairs) {
        var candlesticks = txCtx.lastCandlesticksDao().getPairsByExchanges();
        txCtx.lastCandlesticksService().lastCandlestickBatchDeletes(deleter ->
                candlesticks.forEach((exchange, pairs) -> {
                    var found = activeExchangePairs.get(exchange);
                    pairs.forEach(pair -> {
                        if(null == found || !found.contains(pair)) {
                            deleter.batch(exchange, pair);
                        }
                    });
                }));
    }

    private void raiseAlerts(@NotNull ZonedDateTime now, @NotNull Exchange virtualExchange, @NotNull String pair) {
        LOGGER.debug("Processing pair [{}] on virtual exchange {}{}...", pair,
                virtualExchange.name(), REMAINDER_VIRTUAL_EXCHANGE.equals(virtualExchange.name()) ? " (Remainder Alerts)" : "");
        context.transaction(ctx -> processMatchingAlerts(ctx, now, virtualExchange, pair, emptyList(), null));
    }

    private void getPricesAndRaiseAlerts(@NotNull ZonedDateTime now, @NotNull Exchange exchange, @NotNull String pair) {
        try {
            LOGGER.debug("Retrieving last price for pair [{}] on {}...", pair, exchange);

            // retrieve all the candlesticks since the last check occurred from now, or since the last hour,
            // lastClose database read don't need to be part of the following alerts update transaction
            var lastClose = context.transactional(ctx -> ctx.lastCandlesticksDao().getLastCandlestickCloseTime(exchange.name(), pair).orElse(null));
            List<Candlestick> prices = getCandlesticksSince(lastClose, now, exchange, pair);

            if(!prices.isEmpty()) {
                // load, notify, and update alerts that matches
                context.transaction(txContext -> {
                    var lastCandlesticksService = txContext.lastCandlesticksService();
                    Candlestick previousPrice = lastCandlesticksService.getLastCandlestick(exchange.name(), pair).orElse(null);
                    processMatchingAlerts(txContext, now, exchange, pair, prices, previousPrice);
                    lastCandlesticksService.updateLastCandlestick(exchange.name(), pair, previousPrice, prices.get(prices.size() - 1));
                });
            } else {
                LOGGER.warn("No market data found for {} on exchange {}", pair, exchange);
            }
        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts for " + pair + " on exchange " + exchange, e);
        }
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

    private void processMatchingAlerts(@NotNull TransactionalContext context, @NotNull ZonedDateTime now, @NotNull Exchange exchange, @NotNull String pair, @NotNull List<Candlestick> prices, @Nullable Candlestick previousPrice) {
        var alertsDao = context.alertsDao();
        var matchingService = context.matchingService();
        long read = alertsDao.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(exchange.name(), pair, now, context.parameters().checkPeriodMin(),
                alerts -> batchAlertsUpdates(alertsDao, now,
                        split(STREAM_BUFFER_SIZE, true, alerts
                                .map(alert -> matchingService.match(now, alert, prices, previousPrice))
                                .filter(MatchingAlert::hasMatch))
                        .map(matchingAlerts -> fetchAlertsMessage(alertsDao, matchingAlerts))
                        .flatMap(matchingAlerts -> sendDiscordNotifications(now, matchingAlerts))));
        LOGGER.debug("Processed {} alerts", read);
    }

    private void batchAlertsUpdates(@NotNull AlertsDao alertsDao, @NotNull ZonedDateTime now, @NotNull Stream<MatchingAlert> matchingAlerts) {
        alertsDao.matchedAlertBatchUpdates(now, matchedUpdater ->
                alertsDao.marginAlertBatchUpdates(now, marginUpdater ->
                        alertsDao.alertBatchDeletes(remainderDeleter ->
                            matchingAlerts.forEach(matchingAlert -> {
                                Alert alert = matchingAlert.alert();
                                (switch (matchingAlert.status()) {
                                    case MATCHED -> (alert.type == remainder ? remainderDeleter : matchedUpdater);
                                    case MARGIN -> marginUpdater;
                                    case NOT_MATCHING -> (BatchEntry) Function.identity();
                                }).batchId(alert.id);
                        }))));
    }

    private List<MatchingAlert> fetchAlertsMessage(@NotNull AlertsDao alertsDao, @NotNull List<MatchingAlert> matchingAlerts) {
        LongStream alertIds = matchingAlerts.stream().mapToLong(matchingAlert -> matchingAlert.alert().id);
        var alertIdMessages = alertsDao.getAlertMessages(alertIds);
        return matchingAlerts.stream().map(matchingAlert ->
                matchingAlert.withAlert(matchingAlert.alert()
                        .withMessage(alertIdMessages.getOrDefault(matchingAlert.alert().id, matchingAlert.alert().message))))
                .toList();
    }

    private Stream<MatchingAlert> sendDiscordNotifications(@NotNull ZonedDateTime now, @NotNull List<MatchingAlert> matchingAlerts) {
        matchingAlerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().serverId))
                .forEach((serverId, alerts) -> sendAlerts(now, serverId, alerts));
        return matchingAlerts.stream();
    }

    private void sendAlerts(@NotNull ZonedDateTime now, @NotNull Long serverId, @NotNull List<MatchingAlert> matchingAlerts) {
        try {
            var userIds = matchingAlerts.stream().map(MatchingAlert::alert).mapToLong(Alert::getUserId);
            var userLocales = context.transactional(txCtx -> txCtx.usersDao().getLocales(userIds));
            if(isPrivate(serverId)) {
                sendPrivateAlerts(now, matchingAlerts, userLocales);
            } else {
                sendServerAlerts(matchingAlerts, userLocales, context.discord().getGuildServer(serverId)
                        .orElseThrow(() -> new IllegalStateException("Failed to get guild server " + serverId +
                                " : should not be connected to this bot")), now);
            }
        } catch (RuntimeException e) {
            String alertIds = matchingAlerts.stream()
                    .map(matchingAlert -> String.valueOf(matchingAlert.alert().id)).collect(joining(","));
            LOGGER.error("Failed to send alerts [" + alertIds + "]", e);
        }
    }

    private void sendServerAlerts(@NotNull List<MatchingAlert> matchingAlerts, @NotNull Map<Long, Locale> userLocales, @NotNull Guild guild, @NotNull ZonedDateTime now) {
        List<String> roles = matchingAlerts.stream().map(MatchingAlert::status).anyMatch(MatchingStatus::notMatching) ? emptyList() : // no @SpotBot mention for a delete notification
                spotBotRole(guild).map(Role::getId).stream().toList();
        var users = matchingAlerts.stream().map(MatchingAlert::alert).map(Alert::getUserId).distinct().map(String::valueOf).toList();
        //TODO user must still be on server check, or else <@123> appears in discord
        // query each user on server ?
        context.discord().sendGuildMessage(guild, toMessage(now, matchingAlerts, roles, users), null);
    }

    private void sendPrivateAlerts(@NotNull ZonedDateTime now, @NotNull List<MatchingAlert> matchingAlerts, @NotNull Map<Long, Locale> userLocales) {
        matchingAlerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().userId))
                .forEach((userId, userAlerts) -> // TODO pass userLocales
                        context.discord().sendPrivateMessage(userId, toMessage(now, userAlerts), null));
    }

    @NotNull
    private Message toMessage(@NotNull ZonedDateTime now, @NotNull List<MatchingAlert> matchingAlerts, @NotNull List<String> roles, @NotNull List<String> users) {
        return Message.of(matchingAlerts.stream().map(alert -> toMessage(now, alert)).toList(), roles, users);
    }

    @NotNull
    private Message toMessage(@NotNull ZonedDateTime now, @NotNull List<MatchingAlert> matchingAlerts) {
        return Message.of(matchingAlerts.stream().map(alert -> toMessage(now, alert)).toList());
    }

    private EmbedBuilder toMessage(@NotNull ZonedDateTime now, @NotNull MatchingAlert matchingAlert) {
        Alert alert = matchingAlert.alert();
        if (matchingAlert.status().notMatching()) { // delete notification
            //TODO separate method, guidName
            var description = alert.descriptionMessage(now, "todo");
            return description.setDescription("Following alert has expired and will be deleted :\n\n" + description.getDescriptionBuilder())
                    .setTitle("Delete notification - alert #" + alert.id).setColor(NOTIFICATION_COLOR);
        }
        return alert.onRaiseMessage(matchingAlert, now);
    }
}