package org.sbot.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.discord.Discord;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static net.dv8tion.jda.api.entities.MessageEmbed.TITLE_MAX_LENGTH;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.spotBotRole;

public final class Alerts {

    private static final Logger LOGGER = LogManager.getLogger(Alerts.class);

    private record AlertEvent(@NotNull Alert alert, boolean isMargin) {}

    private static final int MAX_ALERT_THREADS = 16;
    private final Executor threadPool = Executors.newWorkStealingPool(MAX_ALERT_THREADS);
    private final Discord discord;
    private final AlertStorage alertStorage;

    public Alerts(@NotNull Discord discord, @NotNull AlertStorage alertStorage) {
        this.discord = requireNonNull(discord);
        this.alertStorage = requireNonNull(alertStorage);
    }

    // this splits in tasks by exchanges and pairs, one rest call must be done by each task to retrieve the candlesticks
    public void checkPricesAndSendAlerts(@NotNull AlertStorage alertStorage) {
        try {//TODO query getEnabledAlertsBy....
            distribute(alertStorage.getAlertsByPairsAndExchanges())
                    .forEach(entry -> { // one task by exchange / pair
                        Exchange exchange = Exchanges.get(entry.getKey());
                        String pair = entry.getValue().getKey();
                        List<Alert> alerts = entry.getValue().getValue();
                        threadPool.execute(() -> getPricesAndTriggerAlerts(exchange, pair, alerts));
                    });
        } catch (RuntimeException e) {
            LOGGER.warn("Exception thrown", e);
        }
    }

    // this returns an identity mapping of entries from a map to a set,
    // to basically distributes the couples of exchange / pair,
    // to spread the rest calls between different exchanges, a little bit
    @NotNull
    private static Set<Entry<String, Entry<String, List<Alert>>>> distribute(@NotNull Map<String, Map<String, List<Alert>>> alertsByPairsAndExchanges) {
        return alertsByPairsAndExchanges.entrySet().stream()
                .flatMap(e -> e.getValue().entrySet().stream()
                        .map(v -> new SimpleEntry<>(e.getKey(), v))).collect(toSet());
    }

    private void getPricesAndTriggerAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Alert> alerts) {
        try {
            if(!alerts.isEmpty()) {
                LOGGER.debug("Retrieving price for pair [{}] on {}...", pair, exchange);
                List<Candlestick> prices = exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1)
                        .sorted(Comparator.comparing(Candlestick::openTime)).toList();

                alertStorage.updateAlerts(matchingAlerts(alerts, prices)
                        .collect(groupingBy(alertEvent -> alertEvent.alert.serverId)).entrySet().stream()
                        .map(this::updateMatchedAlerts)
                        .flatMap(this::sendAlerts)
                        .toList());
            }
        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private static Stream<AlertEvent> matchingAlerts(@NotNull List<Alert> alerts, @NotNull List<Candlestick> prices) {
        return alerts.stream()
                .flatMap(alert -> prices.stream().noneMatch(alert::inMargin) ? Stream.empty() :
                        Stream.of(new AlertEvent(alert, alert.hasMargin() && prices.stream().noneMatch(alert::match))));
    }

    private Entry<Long, List<AlertEvent>> updateMatchedAlerts(@NotNull Entry<Long, List<AlertEvent>> serverAlerts) {
        return new SimpleEntry<>(serverAlerts.getKey(), serverAlerts.getValue().stream().map(event ->
                new AlertEvent(event.isMargin ?
                        event.alert.withMargin((short) 0) :
                        event.alert.withRepeat((short) Math.max(0, event.alert.repeat - 1)),
                        event.isMargin)).toList());
    }

    private Stream<Alert> sendAlerts(@NotNull Entry<Long, List<AlertEvent>> serverAlerts) {
        long serverId = serverAlerts.getKey();
        var alerts = serverAlerts.getValue();
        try {
            if(PRIVATE_ALERT != serverId) {
                sendServerAlerts(alerts, discord.getDiscordServer(serverId));
            } else {
                sendPrivateAlerts(alerts);
            }
            return alerts.stream().map(AlertEvent::alert);
        } catch (RuntimeException e) {
            String alertIds = Optional.ofNullable(alerts).orElse(emptyList()).stream()
                    .map(AlertEvent::alert)
                    .map(alert -> String.valueOf(alert.id))
                    .collect(Collectors.joining(","));
            LOGGER.error("Failed to send alerts [" + alertIds + "], update won't be done for them", e);
            return Stream.empty();
        }
    }

    private void sendServerAlerts(@NotNull List<AlertEvent> alerts, @NotNull Guild guild) {
        var roles = spotBotRole(guild).map(Role::getId).stream().toList();
        var users = alerts.stream().map(AlertEvent::alert).mapToLong(Alert::getUserId).distinct().toArray();
        discord.spotBotChannel(guild).ifPresent(channel ->
                channel.sendMessages(toMessage(alerts),
                        List.of(message -> requireNonNull(message.mentionRoles(roles).mentionUsers(users)))));
    }

    private void sendPrivateAlerts(@NotNull List<AlertEvent> alerts) {
        alerts.stream().collect(groupingBy(alertEvent -> alertEvent.alert.userId))
                .forEach((userId, userAlerts) -> discord.userChannel(userId)
                        .ifPresent(channel -> channel.sendMessages(toMessage(userAlerts), emptyList())));
    }

    @NotNull
    private List<EmbedBuilder> toMessage(@NotNull List<AlertEvent> alerts) {
        return shrinkToPageSize(alerts.stream()
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(this::toMessage).collect(toList()), alerts.size());

    }

    private EmbedBuilder toMessage(@NotNull AlertEvent alertEvent) {
        Alert alert = alertEvent.alert;
        return embedBuilder(title(alert.name(), alert.getSlashPair(), alert.message, alertEvent.isMargin),
                alertEvent.isMargin ? Color.orange : Color.green,
                alert.triggeredMessage(alertEvent.isMargin));
    }

    //TODO remove / simplify, check max ticker size, this should adapt - remove thing to fit ticker size
    private static String title(@NotNull String alertName, @NotNull String slashPair, @NotNull String message, boolean isMargin) {
        String title = "!!! " + (isMargin ? "MARGIN " : "") + alertName + " ALERT !!! - [" + slashPair + "] ";
        if(!alertName.isEmpty() && TITLE_MAX_LENGTH - message.length() < title.length()) {
            LOGGER.warn("Alert name '{}' truncated because alert title is too long : {}", alertName, title);
            return title(alertName.substring(0,
                            Math.max(0, Math.min(alertName.length(),
                                    (TITLE_MAX_LENGTH - message.length() - title.length() + alertName.length())))),
                    slashPair, message, isMargin);
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
