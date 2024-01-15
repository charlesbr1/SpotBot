package org.sbot.services;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.alerts.MatchingAlert;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.discord.Discord;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;
import org.sbot.utils.Dates;
import org.sbot.utils.Dates.DaysHours;

import java.awt.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.entities.MessageEmbed.TITLE_MAX_LENGTH;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.alerts.RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.spotBotRole;
import static org.sbot.utils.PartitionSpliterator.split;

public final class AlertsWatcher {

    private static final Logger LOGGER = LogManager.getLogger(AlertsWatcher.class);

    private static final int MAX_DBMS_SQL_IN_CLAUSE_VALUES = 1000;
    private static final int STREAM_BUFFER_SIZE = Math.min(MAX_DBMS_SQL_IN_CLAUSE_VALUES, MESSAGE_PAGE_SIZE);

    private final Discord discord;
    private final AlertsDao alertDao;
    private final MarketDataService marketDataService;

    public AlertsWatcher(@NotNull Discord discord, @NotNull AlertsDao alertsDao, @NotNull MarketDataService marketDataService) {
        this.discord = requireNonNull(discord);
        this.alertDao = requireNonNull(alertsDao);
        this.marketDataService = requireNonNull(marketDataService);
    }

    // this splits in tasks by exchanges and pairs, one rest call must be done by each task to retrieve the candlesticks
    public void checkAlerts() {
        try {
            expiredAlertsCleanup();

            var exchangePairs = alertDao.transactional(alertDao::getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange);
            exchangePairs.forEach((xchange, pairs) -> {  // one task by exchange / pair
                var pairList = List.copyOf(pairs);
                Exchanges.get(xchange).ifPresent(exchange -> {
                    Thread.ofVirtual().start(() -> pairList.forEach(pair -> {
                        if(exchange.isVirtual()) {
                            raiseAlerts(exchange, pair);
                        } else {
                            Thread.ofVirtual().name('[' + pair + "] SpotBot fetcher")
                                    .start(() -> getPricesAndRaiseAlerts(exchange, pair));
                            LockSupport.parkNanos(pair.equals(pairList.get(pairList.size() - 1)) ? 0L : Duration.ofMillis(300L).toNanos()); // no need to flood the exchanges (or discord)
                        }
                    }));
                    LockSupport.parkNanos(exchange.isVirtual() ? 0L : Duration.ofMillis(300L / (Math.min(10, exchangePairs.size()))).toNanos());
                });
            });
        } catch (RuntimeException e) {
            LOGGER.error("Exception thrown while performing hourly alerts task", e);
        }
    }

    void expiredAlertsCleanup() {
        try {
            Consumer<Stream<Alert>> alertsDeleter = alerts -> alertDao.alertBatchDeletes(deleter ->
                    split(STREAM_BUFFER_SIZE, true, alerts
                            .map(alert -> new MatchingAlert(alert, NOT_MATCHING, null)))
                            .flatMap(this::sendDiscordNotifications)
                            .forEach(matchingAlert -> deleter.batchId(matchingAlert.alert().id)));

            alertDao.transactional(() -> {
                ZonedDateTime expirationDate = ZonedDateTime.now().minusMonths(1L);
                long deleted = alertDao.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(expirationDate, alertsDeleter);
                LOGGER.debug("Deleted {} alerts with repeat = 0 and lastTrigger < {}", deleted, expirationDate);
            });

            alertDao.transactional(() -> {
                ZonedDateTime expirationDate = ZonedDateTime.now().minusWeeks(1L);
                long deleted = alertDao.fetchRangeAlertsHavingToDateBefore(expirationDate, alertsDeleter);
                LOGGER.debug("Deleted {} range alerts with toDate < {}", deleted, expirationDate);
            });
        } catch (RuntimeException e) {
            LOGGER.error("Exception thrown while deleting old alerts", e);
        }
    }

    private void raiseAlerts(@NotNull Exchange virtualExchange, @NotNull String pair) {
        LOGGER.debug("Processing pair [{}] on virtual exchange {}{}...", pair,
                virtualExchange.name(), REMAINDER_VIRTUAL_EXCHANGE.equals(virtualExchange.name()) ? " (Remainder Alerts)" : "");
        alertDao.transactional(() -> processMatchingAlerts(virtualExchange, pair, emptyList(), null));
    }

    private void getPricesAndRaiseAlerts(@NotNull Exchange exchange, @NotNull String pair) {
        try {
            LOGGER.debug("Retrieving last price for pair [{}] on {}...", pair, exchange);

            // retrieve all the candlesticks since the last check occurred from now, or since the last hour,
            // lastClose database read don't need to be part of the following alerts update transaction
            var lastClose = marketDataService.getLastCandlestickCloseTime(pair).orElse(null);
            List<Candlestick> prices = getCandlesticksSince(exchange, pair, lastClose);

            if(!prices.isEmpty()) {
                // load, notify, and update alerts that matches
                alertDao.transactional(() -> {
                    Candlestick previousPrice = marketDataService.getLastCandlestick(pair).orElse(null);
                    processMatchingAlerts(exchange, pair, prices, previousPrice);
                    marketDataService.updateLastCandlestick(pair, previousPrice, prices.get(prices.size() - 1));
                });
            } else {
                LOGGER.warn("No market data found for {} on exchange {}", pair, exchange);
            }
        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private List<Candlestick> getCandlesticksSince(@NotNull Exchange exchange, @NotNull String pair, @Nullable ZonedDateTime previousCloseTime) {
        DaysHours daysHours = Optional.ofNullable(previousCloseTime).map(Dates::daysHoursSince)
                .orElse(DaysHours.ZERO);
        LOGGER.debug("Computed {} days and {} hours since the last price close time {}", daysHours.days(), daysHours.hours(), previousCloseTime);

        List<Candlestick> prices = new ArrayList<>(daysHours.days() + daysHours.hours() + 1);
        if(daysHours.days() > 0) {
            prices.addAll(getCandlesticks(exchange, pair, TimeFrame.DAILY, daysHours.days()));
        }
        prices.addAll(getCandlesticks(exchange, pair, TimeFrame.HOURLY, daysHours.hours() + 1));
        return prices;
    }

    private List<Candlestick> getCandlesticks(@NotNull Exchange exchange, @NotNull String pair, @NotNull TimeFrame timeFrame, int limit) {
        var candlesticks  = exchange.getCandlesticks(pair, timeFrame, limit);
        if(candlesticks.isEmpty()) {
            throw new IllegalStateException("No " + timeFrame.name() + " candlestick found for pair " + pair + " on exchange" + exchange.name() + " with limit " + limit);
        }
        return candlesticks;
    }

    private void processMatchingAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Candlestick> prices, @Nullable Candlestick previousPrice) {
        long read = alertDao.fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(exchange.name(), pair,
                alerts -> batchAlertsUpdates(
                        split(STREAM_BUFFER_SIZE, true, alerts
                                .map(alert -> alert.match(prices, previousPrice))
                                .filter(MatchingAlert::hasMatch))
                        .map(this::fetchAlertsMessage)
                        .flatMap(this::sendDiscordNotifications)));
        LOGGER.debug("Processed {} alerts", read);
    }

    private void batchAlertsUpdates(@NotNull Stream<MatchingAlert> matchingAlerts) {
        alertDao.matchedAlertBatchUpdates(matchedUpdate ->
                alertDao.marginAlertBatchUpdates(marginUpdate ->
                        alertDao.alertBatchDeletes(remainderDelete ->
                            matchingAlerts.forEach(matchingAlert -> {
                                Alert alert = matchingAlert.alert();
                                (switch (matchingAlert.status()) {
                                    case MATCHED -> (alert.type == remainder ? remainderDelete : matchedUpdate);
                                    case MARGIN -> marginUpdate;
                                    case NOT_MATCHING -> (BatchEntry) Function.identity();
                                }).batchId(alert.id);
                        }))));
    }

    private List<MatchingAlert> fetchAlertsMessage(@NotNull List<MatchingAlert> matchingAlerts) {
        long[] alertIds = matchingAlerts.stream().mapToLong(matchingAlert -> matchingAlert.alert().id).toArray();
        var alertIdMessages = alertDao.getAlertMessages(alertIds); // calling code is already into a transactional context
        return matchingAlerts.stream().map(matchingAlert ->
                matchingAlert.withAlert(matchingAlert.alert()
                        .withMessage(alertIdMessages.getOrDefault(matchingAlert.alert().id, matchingAlert.alert().message))))
                .toList();
    }

    private Stream<MatchingAlert> sendDiscordNotifications(@NotNull List<MatchingAlert> matchingAlerts) {
        matchingAlerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().serverId))
                .forEach(this::sendAlerts);
        return matchingAlerts.stream();
    }

    private void sendAlerts(@NotNull Long serverId, @NotNull List<MatchingAlert> matchingAlerts) {
        try {
            if(isPrivate(serverId)) {
                sendPrivateAlerts(matchingAlerts);
            } else {
                sendServerAlerts(matchingAlerts, discord.getGuildServer(serverId)
                        .orElseThrow(() -> new IllegalStateException("Failed to get guild server " + serverId +
                                " : should not be connected to this bot")));
            }
        } catch (RuntimeException e) {
            String alertIds = matchingAlerts.stream()
                    .map(matchingAlert -> String.valueOf(matchingAlert.alert().id)).collect(joining(","));
            LOGGER.error("Failed to send alerts [" + alertIds + "]", e);
        }
    }

    private void sendServerAlerts(@NotNull List<MatchingAlert> matchingAlerts, @NotNull Guild guild) {
        List<String> roles = matchingAlerts.stream().map(MatchingAlert::status).anyMatch(MatchingStatus::notMatching) ? emptyList() : // no @SpotBot mention for a delete notification
                spotBotRole(guild).map(Role::getId).stream().toList();
        long[] users = matchingAlerts.stream().map(MatchingAlert::alert).mapToLong(Alert::getUserId).distinct().toArray();
        //TODO user must still be on server check, or else <@123> appears in discord
        Discord.spotBotChannel(guild).ifPresent(channel ->
                channel.sendMessages(toMessages(matchingAlerts),
                        List.of(message -> requireNonNull(message.mentionRoles(roles).mentionUsers(users)))));
    }

    private void sendPrivateAlerts(@NotNull List<MatchingAlert> matchingAlerts) {
        matchingAlerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().userId))
                .forEach((userId, userAlerts) -> // retrieving user channel is a blocking task (there is a cache though)
                        Thread.ofVirtual().name("SpotBot private channel " + userId)
                                .start(() -> discord.userChannel(userId)
                                        .ifPresent(channel -> channel.sendMessages(toMessages(userAlerts), emptyList()))));
    }

    @NotNull
    private List<EmbedBuilder> toMessages(@NotNull List<MatchingAlert> matchingAlerts) {
        return matchingAlerts.stream().map(this::toMessage).toList();
    }

    private EmbedBuilder toMessage(@NotNull MatchingAlert matchingAlert) {
        Alert alert = matchingAlert.alert();
        if(matchingAlert.status().notMatching()) { // delete notification
            return embedBuilder("Delete notification - alert #" + alert.id, Color.black,
                    "Following alert has expired and will be deleted :\n\n" + alert.descriptionMessage());
        }
        return embedBuilder(title(alert, matchingAlert.status()),
                matchingAlert.status().isMatched() ? Color.green : Color.orange,
                alert.triggeredMessage(matchingAlert.status(), matchingAlert.matchingCandlestick()));
    }

    private static String title(@NotNull Alert alert, @NotNull MatchingStatus matchingStatus) {
        String title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + alert.type.titleName + (alert.type != remainder ? " ALERT !!!" : "") + " - [" + alert.pair + "] ";
        if(TITLE_MAX_LENGTH - alert.message.length() < title.length()) {
            LOGGER.warn("Alert name '{}' will be truncated from the title because it is too long : {}", alert.type.titleName, title);
            title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + "ALERT !!! - [" + alert.pair + "] ";
            if(TITLE_MAX_LENGTH - alert.message.length() < title.length()) {
                LOGGER.warn("Pair '{}' will be truncated from the title because it is too long : {}", alert.pair, title);
                title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + " ALERT !!! - ";
            }
        }
        return title + (alert.type != remainder ? alert.message : "");
    }
}