package org.sbot.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.discord.Discord;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
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

public final class Alerts {

    private static final Logger LOGGER = LogManager.getLogger(Alerts.class);


    private final Discord discord;
    private final AlertStorage alertStorage;

    public Alerts(@NotNull Discord discord, @NotNull AlertStorage alertStorage) {
        this.discord = requireNonNull(discord);
        this.alertStorage = requireNonNull(alertStorage);
    }

    // this splits in tasks by exchanges and pairs, one rest call must be done by each task to retrieve the candlesticks
    public void checkPricesAndSendAlerts(@NotNull AlertStorage alertStorage) {
        try {
            //TODO query filter to retrieve enabled alerts, repeat != 0 and lastTrigger < date
            alertStorage.getAlertsByPairsAndExchanges()
                    .forEach((xchange, alertsByPair) -> { // one task by exchange / pair
                        Exchange exchange = Exchanges.get(xchange);
                        alertsByPair.forEach((pair, alerts) ->
                                Thread.ofVirtual().name('[' + pair + ']')
                                        .start(() -> getPricesAndTriggerAlerts(exchange, pair, alerts)));
                        LockSupport.parkNanos(Duration.ofSeconds(1).toNanos()); // no need to flood the exchanges
                    });
        } catch (RuntimeException e) {
            LOGGER.debug("Exception thrown while fetching prices", e);
        }
    }

    private void getPricesAndTriggerAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Alert> alerts) {
        try {
            if(!alerts.isEmpty()) {
                LOGGER.debug("Retrieving price for pair [{}] on {}...", pair, exchange);
                // TODO récuperer l'historique depuis le last candlestick des alerts
                List<Candlestick> prices = exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1)
                        .sorted(Comparator.comparing(Candlestick::openTime)).toList();

                alertStorage.updateAlerts(alerts.stream()
                        .map(alert -> alert.match(prices, null)) //TODO previous candlestick
                        .filter(MatchingAlert::hasMatch)
                        .collect(groupingBy(matchingAlert -> matchingAlert.alert().serverId)).entrySet().stream()
                        .flatMap(this::sendAlerts)
                        .toList());
            }
        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private Stream<Alert> sendAlerts(@NotNull Entry<Long, List<MatchingAlert>> serverAlerts) {
        long serverId = serverAlerts.getKey();
        var alerts = serverAlerts.getValue();
        try {
            if(PRIVATE_ALERT != serverId) {
                sendServerAlerts(alerts, discord.getDiscordServer(serverId));
            } else {
                sendPrivateAlerts(alerts);
            }
            return alerts.stream().map(MatchingAlert::alert);
        } catch (RuntimeException e) {
            String alertIds = Optional.ofNullable(alerts).orElse(emptyList()).stream()
                    .map(MatchingAlert::alert)
                    .map(alert -> String.valueOf(alert.id))
                    .collect(Collectors.joining(","));
            LOGGER.error("Failed to send alerts [" + alertIds + "], update won't be done for them", e);
            return Stream.empty();
        }
    }

    private void sendServerAlerts(@NotNull List<MatchingAlert> alerts, @NotNull Guild guild) {
        var roles = spotBotRole(guild).map(Role::getId).stream().toList();
        var users = alerts.stream().map(MatchingAlert::alert).mapToLong(Alert::getUserId).distinct().toArray();
        //TODO user must be on server check
        discord.spotBotChannel(guild).ifPresent(channel ->
                channel.sendMessages(toMessage(alerts),
                        List.of(message -> requireNonNull(message.mentionRoles(roles).mentionUsers(users)))));
    }

    private void sendPrivateAlerts(@NotNull List<MatchingAlert> alerts) {
        alerts.stream().collect(groupingBy(matchingAlert -> matchingAlert.alert().userId))
                .forEach((userId, userAlerts) -> discord.userChannel(userId)
                        .ifPresent(channel -> channel.sendMessages(toMessage(userAlerts), emptyList())));
    }

    @NotNull
    private List<EmbedBuilder> toMessage(@NotNull List<MatchingAlert> alerts) {
        return shrinkToPageSize(alerts.stream()
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(this::toMessage).collect(toList()), alerts.size());
    }

    private EmbedBuilder toMessage(@NotNull MatchingAlert matchingAlert) {
        Alert alert = matchingAlert.alert();
        return embedBuilder(title(alert.name(), alert.getSlashPair(), alert.message, matchingAlert.matchingStatus()),
                MARGIN == matchingAlert.matchingStatus() ? Color.orange : Color.green,
                alert.triggeredMessage(matchingAlert.matchingStatus(), matchingAlert.matchingCandlestick()));
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
