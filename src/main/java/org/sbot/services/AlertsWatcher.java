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

import java.awt.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static net.dv8tion.jda.api.entities.MessageEmbed.TITLE_MAX_LENGTH;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.MARGIN;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.spotBotRole;
import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;
import static org.sbot.utils.PartitionSpliterator.split;

public final class AlertsWatcher {

    private static final Logger LOGGER = LogManager.getLogger(AlertsWatcher.class);

    private static final int STREAM_BUFFER_SIZE = 1000; // should not exceed the max underlying sgdb IN clause arguments number

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
            var exchangePairs = alertDao.transactional(alertDao::getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange);
            exchangePairs.forEach((xchange, pairs) -> {  // one task by exchange / pair
                Exchanges.get(xchange).ifPresent(exchange ->
                        Thread.ofVirtual().start(() -> pairs.forEach(pair -> {
                            Thread.ofVirtual().name('[' + pair + "] SpotBot fetcher")
                                    .start(() -> getPricesAndRaiseAlerts(exchange, pair));
                            LockSupport.parkNanos(VIRTUAL_EXCHANGES.contains(xchange) ? 0 :
                                    Duration.ofMillis(300).toNanos()); // no need to flood the exchanges
                        })));
                LockSupport.parkNanos(Duration.ofMillis(300 / (Math.min(10, exchangePairs.size()))).toNanos());
            });
            monthlyAlertsCleanup();
        } catch (RuntimeException e) {
            LOGGER.error("Exception thrown while performing hourly alerts task", e);
        }
    }

    void monthlyAlertsCleanup() {
        //TODO drop alerts that are disabled since 1 month, or range box alert out of time...
    }

    private void getPricesAndRaiseAlerts(@NotNull Exchange exchange, @NotNull String pair) {
        try {
            LOGGER.debug("Retrieving price for pair [{}] on {}...", pair, exchange);
            // TODO récuperer l'historique depuis le last candlestick des alerts
            List<Candlestick> prices = exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1);
            if(prices.isEmpty()) {
                throw new IllegalStateException("No candlestick found for pair " + pair + " on exchange" + exchange.name());
            }
            Candlestick previousPrice = marketDataService.getAndUpdateLastCandlestick(pair, prices.get(prices.size()-1));

            // load, notify, and update alerts that matches
            processMatchingAlerts(exchange, pair, prices, previousPrice);

        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private void processMatchingAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Candlestick> prices, @Nullable Candlestick previousPrice) {
        alertDao.transactional(() -> alertDao
                .fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(exchange.name(), pair,
                alerts -> updateAlerts(
                        split(STREAM_BUFFER_SIZE, true, alerts.map(alert -> alert.match(prices, previousPrice)).filter(MatchingAlert::hasMatch))
                        .map(this::fetchAlertsMessage)
                        .flatMap(this::sendDiscordNotifications))));
    }

    private void updateAlerts(@NotNull Stream<MatchingAlert> matchingAlerts) {
        alertDao.matchedAlertBatchUpdates(matchedUpdater ->
                alertDao.marginAlertBatchUpdates(marginUpdater ->
                        alertDao.matchedRemainderAlertBatchDeletes(remainderDelete ->
                            matchingAlerts.forEach(matchingAlert -> {
                                Alert alert = matchingAlert.alert();
                                switch (matchingAlert.status()) {
                                    case MATCHED -> (alert.type == remainder ? remainderDelete : matchedUpdater).update(alert.id);
                                    case MARGIN -> marginUpdater.update(alert.id);
                                }
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
                sendServerAlerts(matchingAlerts, discord.getDiscordServer(serverId));
            }
        } catch (RuntimeException e) {
            String alertIds = matchingAlerts.stream()
                    .map(matchingAlert -> String.valueOf(matchingAlert.alert().id)).collect(joining(","));
            LOGGER.error("Failed to send alerts [" + alertIds + "]", e);
        }
    }

    private void sendServerAlerts(@NotNull List<MatchingAlert> matchingAlerts, @NotNull Guild guild) {
        var roles = spotBotRole(guild).map(Role::getId).stream().toList();
        var users = matchingAlerts.stream().map(MatchingAlert::alert).mapToLong(Alert::getUserId).distinct().toArray();
        //TODO user must still be on server check, or else <@123> appears in discord
        discord.spotBotChannel(guild).ifPresent(channel ->
                channel.sendMessages(toMessage(matchingAlerts),
                        List.of(message -> requireNonNull(message.mentionRoles(roles).mentionUsers(users)))));
    }

    private void sendPrivateAlerts(@NotNull List<MatchingAlert> matchingAlerts) {
        matchingAlerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().userId))
                .forEach((userId, userAlerts) -> discord.userChannel(userId)
                        .ifPresent(channel -> channel.sendMessages(toMessage(userAlerts), emptyList())));
    }

    @NotNull
    private List<EmbedBuilder> toMessage(@NotNull List<MatchingAlert> matchingAlerts) {
        return shrinkToPageSize(matchingAlerts.stream()
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(this::toMessage).collect(toList()), matchingAlerts.size());
    }

    private EmbedBuilder toMessage(@NotNull MatchingAlert matchingAlert) {
        Alert alert = matchingAlert.alert();
        return embedBuilder(title(alert, matchingAlert.status()),
                MARGIN == matchingAlert.status() ? Color.orange : Color.green,
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

    private List<EmbedBuilder> shrinkToPageSize(@NotNull List<EmbedBuilder> alerts, int total) {
        if(alerts.size() > MESSAGE_PAGE_SIZE) {
            while(alerts.size() >= MESSAGE_PAGE_SIZE) {
                alerts.remove(alerts.size() - 1);
            }
            alerts.add(embedBuilder("...", Color.red, "Limit reached ! That's too much alerts.\n\n* " +
                    total + " alerts were triggered\n* " + MESSAGE_PAGE_SIZE + " alerts were notified\n* " +
                    (total - MESSAGE_PAGE_SIZE + 1) + " remaining alerts are discarded, sorry."));
        }
        return alerts;
    }
}
