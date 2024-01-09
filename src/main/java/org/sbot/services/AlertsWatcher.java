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
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.entities.MessageEmbed.TITLE_MAX_LENGTH;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
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
            alertDao.transactional(alertDao::getPairsByExchangesHavingRepeatAndDelayOver)
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

            List<MatchingAlert> matchingAlerts = new ArrayList<>();
            alertDao.transactional(() -> {
                alertDao.fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOver(
                        exchange.name(), pair, alerts -> updateAlerts(alerts
                                .map(alert -> alert.match(prices, null)) //TODO previous candlestick
                                .filter(MatchingAlert::hasMatch)
                                .filter(matchingAlerts::add)));
            });

            // matching alerts has no message, they are retrieved in a second time
            Map<Long, String> alertMessages = split(1000, matchingAlerts.stream()) // split in many calls using SQL IN clause
                    .map(alerts ->
                            alertDao.transactional(() -> alertDao.getAlertMessages(alerts.stream().mapToLong(matchingAlert -> matchingAlert.alert().id).toArray())))
                    .flatMap(map -> map.entrySet().stream())
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

            matchingAlerts.stream()
                    .map(matchingAlert -> new MatchingAlert(matchingAlert.alert() // replace the alerts by ones with their messages
                            .withMessage(alertMessages.getOrDefault(matchingAlert.alert().id, matchingAlert.alert().message)),
                            matchingAlert.status(), matchingAlert.matchingCandlestick()))
                    .collect(groupingBy(matchingAlert -> matchingAlert.alert().serverId))
                    .forEach(this::sendAlerts);

        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
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

    private void sendAlerts(@NotNull Long serverId, @NotNull List<MatchingAlert> matchingAlerts) {
        try {
            if(PRIVATE_ALERT != serverId) {
                sendServerAlerts(matchingAlerts, discord.getDiscordServer(serverId));
            } else {
                sendPrivateAlerts(matchingAlerts);
            }
        } catch (RuntimeException e) {
            String alertIds = matchingAlerts.stream()
                    .map(matchingAlert -> String.valueOf(matchingAlert.alert().id))
                    .collect(Collectors.joining(","));
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
        return embedBuilder(title(alert.type.titleName, alert.getSlashPair(), alert.message, matchingAlert.status()),
                MARGIN == matchingAlert.status() ? Color.orange : Color.green,
                alert.triggeredMessage(matchingAlert.status(), matchingAlert.matchingCandlestick()));
    }

    //TODO remove / simplify, check max ticker size, this should adapt - remove thing to fit ticker size
    private static String title(@NotNull String alertName, @NotNull String slashPair, @NotNull String message, MatchingStatus matchingStatus) {
        String title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + alertName + " ALERT !!! - [" + slashPair + "] ";
        if(!alertName.isEmpty() && TITLE_MAX_LENGTH - message.length() < title.length()) {
            LOGGER.warn("Alert name '{}' truncated because alert title is too long : {}", alertName, title);
            return title(alertName.substring(0,
                            Math.max(0, Math.min(alertName.length(),
                                    (TITLE_MAX_LENGTH - message.length() - title.length() + alertName.length())))),
                    slashPair, message, matchingStatus);
        }
        return title + message;
    }

    private List<EmbedBuilder> shrinkToPageSize(@NotNull List<EmbedBuilder> alerts, int total) {
        if(alerts.size() > MESSAGE_PAGE_SIZE) {
            while(alerts.size() >= MESSAGE_PAGE_SIZE) {
                alerts.remove(alerts.size() - 1);
            }
            alerts.add(embedBuilder("...", Color.red, "Limit reached ! That's too much alerts.\n\n* " +
                    total + " alerts were triggered\n* " + MESSAGE_PAGE_SIZE + " alerts were notified\n* " +
                    (total - MESSAGE_PAGE_SIZE + 1) + " remaining alerts are discarded"));
        }
        return alerts;
    }
}
