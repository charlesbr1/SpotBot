package org.sbot.services;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static net.dv8tion.jda.api.entities.MessageEmbed.TITLE_MAX_LENGTH;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.MARGIN;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.spotBotRole;
import static org.sbot.utils.PartitionSpliterator.split;

public final class AlertsWatcher {

    private static final Logger LOGGER = LogManager.getLogger(AlertsWatcher.class);


    private final Discord discord;
    private final AlertsDao alertDao;

    public AlertsWatcher(@NotNull Discord discord, @NotNull AlertsDao alertsDao) {
        this.discord = requireNonNull(discord);
        this.alertDao = requireNonNull(alertsDao);
    }

    // this splits in tasks by exchanges and pairs, one rest call must be done by each task to retrieve the candlesticks
    public void checkAlerts() {
        try {
            alertDao.transactional(alertDao::getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange)
                    .forEach((xchange, pairs) -> {  // one task by exchange / pair
                        Exchanges.get(xchange).ifPresent(exchange ->
                                pairs.forEach(pair ->
                                        Thread.ofVirtual().name('[' + pair + "] SpotBot fetcher")
                                                .start(() -> getPricesAndCheckAlerts(exchange, pair))));
                        LockSupport.parkNanos(Duration.ofSeconds(1).toNanos()); // no need to flood the exchanges
                    });
        } catch (RuntimeException e) {
            LOGGER.error("Exception thrown while performing hourly alerts task", e);
        }
    }

    private void getPricesAndCheckAlerts(@NotNull Exchange exchange, @NotNull String pair) {
        try {
            LOGGER.debug("Retrieving price for pair [{}] on {}...", pair, exchange);
            // TODO récuperer l'historique depuis le last candlestick des alerts
            List<Candlestick> prices = exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1)
                    .sorted(Comparator.comparing(Candlestick::openTime)).toList();

            // load and update alerts that matches, their message attribute has to be loaded next
            List<MatchingAlert> matchingAlerts = updateMatchingAlerts(exchange, pair, prices, null);

            // load message of alerts that matches then send discord notifications on each related guild or private channels
            sendDiscordNotifications(matchingAlerts);

        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private List<MatchingAlert> updateMatchingAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Candlestick> prices, @NotNull Candlestick previousPrice) {
        List<MatchingAlert> matchingAlerts = new ArrayList<>();
        alertDao.transactional(() -> alertDao
                .fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange(exchange.name(), pair,
                alerts -> updateAlerts(alerts
                        .map(alert -> alert.match(prices, previousPrice))
                        .filter(MatchingAlert::hasMatch)
                        .filter(matchingAlerts::add))));
        return matchingAlerts;
    }

    private void updateAlerts(@NotNull Stream<MatchingAlert> matchingAlerts) {
        alertDao.matchedAlertBatchUpdates(matchedUpdater ->
                alertDao.marginAlertBatchUpdates(marginUpdater ->
                        matchingAlerts.forEach(matchingAlert -> {
                            Alert alert = matchingAlert.alert();
                            switch (matchingAlert.status()) {
                                case MATCHED -> matchedUpdater.update(alert.id);
                                case MARGIN -> marginUpdater.update(alert.id);
                            }
                        })));
    }

    private void sendDiscordNotifications(@NotNull List<MatchingAlert> matchingAlerts) {
        split(1000, matchingAlerts).flatMap(alertList -> { // splitting for SQL IN clause getAlertMessages(..)
                    long[] alertIds = alertList.stream().mapToLong(matchingAlert -> matchingAlert.alert().id).toArray();
                    var alertIdMessages = alertDao.transactional(() -> alertDao.getAlertMessages(alertIds));
                    return alertList.stream().map(matchingAlert -> matchingAlert.withAlert(matchingAlert.alert() // replace the alerts by ones with their messages
                            .withMessage(alertIdMessages.getOrDefault(matchingAlert.alert().id, matchingAlert.alert().message))));
                }).collect(groupingBy(matchingAlert -> matchingAlert.alert().serverId))
                .forEach(this::sendAlerts);
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
        String title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + alert.type.titleName + " ALERT !!! - [" + alert.getSlashPair() + "] ";
        if(TITLE_MAX_LENGTH - alert.message.length() < title.length()) {
            LOGGER.warn("Alert name '{}' will be truncated from the title because it is too long : {}", alert.type.titleName, title);
            title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + "ALERT !!! - [" + alert.getSlashPair() + "] ";
            if(TITLE_MAX_LENGTH - alert.message.length() < title.length()) {
                LOGGER.warn("Pair '{}' will be truncated from the title because it is too long : {}", alert.getSlashPair(), title);
                title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + " ALERT !!! - ";
            }
        }
        return title + alert.message;
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
