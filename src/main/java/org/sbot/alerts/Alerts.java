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
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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

    private static final int MAX_ALERT_THREADS = 16;
    private final Executor threadPool = Executors.newWorkStealingPool(MAX_ALERT_THREADS);
    private final Discord discord;

    public Alerts(@NotNull Discord discord) {
        this.discord = requireNonNull(discord);
    }

    public void checkPricesAndSendAlerts(@NotNull AlertStorage alertStorage) {
        try {
            alertStorage.getAlertsByPairsAndExchanges().entrySet().stream()
                    .flatMap(e -> e.getValue().entrySet().stream() // this remapping simply distributes the couples of exchange / pair
                            .map(v -> new SimpleEntry<>(e.getKey(), v))).collect(toSet())
                    .forEach(entry -> {
                        Exchange exchange = Exchanges.get(entry.getKey());
                        String pair = entry.getValue().getKey();
                        List<Alert> alerts = entry.getValue().getValue();
                        threadPool.execute(() -> getPricesAndTriggerAlerts(exchange, pair, alerts));
                    });
        } catch (RuntimeException e) {
            LOGGER.warn("Exception thrown", e);
        }
    }

    private record AlertOrMargin(@NotNull Alert alert, boolean isMargin) {}

    private void getPricesAndTriggerAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Alert> alerts) {
        try {
            LOGGER.debug("Retrieving price for pair [{}] on {}...", pair, exchange);
            List<Candlestick> prices = exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1) // only one call by pair
                    .sorted(Comparator.comparing(Candlestick::openTime)).toList();

            matchingAlerts(alerts, prices)
                    .collect(groupingBy(alertOrMargin -> alertOrMargin.alert.serverId))
                    .entrySet().stream()
                    .filter(this::sendAlerts)
                    .map(Entry::getValue)
                    .forEach(this::updateAlerts);
        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private static Stream<AlertOrMargin> matchingAlerts(@NotNull List<Alert> alerts, @NotNull List<Candlestick> prices) {
        return alerts.stream()
                .map(alert -> prices.stream().noneMatch(alert::inMargin) ? null :
                        new AlertOrMargin(alert, prices.stream().noneMatch(alert::match)))
                .filter(Objects::nonNull);
    }

    private boolean sendAlerts(@NotNull Entry<Long, List<AlertOrMargin>> serverAlerts) {
        return sendAlerts(serverAlerts.getKey(), serverAlerts.getValue());
    }

    private boolean sendAlerts(long serverId, @NotNull List<AlertOrMargin> alerts) {
        try {
            if(PRIVATE_ALERT != serverId) {
                sendServerAlerts(alerts, discord.getDiscordServer(serverId));
            } else {
                sendPrivateAlerts(alerts);
            }
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to send alerts", e);
            return false;
        }
    }

    private void sendServerAlerts(@NotNull List<AlertOrMargin> alerts, @NotNull Guild guild) {
        var roles = spotBotRole(guild).map(Role::getId).stream().toList();
        var users = alerts.stream().map(AlertOrMargin::alert).mapToLong(Alert::getUserId).distinct().toArray();
        discord.spotBotChannel(guild).ifPresent(channel ->
                channel.sendMessages(toMessage(alerts),
                        List.of(message -> requireNonNull(message.mentionRoles(roles).mentionUsers(users)))));
    }

    private void sendPrivateAlerts(@NotNull List<AlertOrMargin> alerts) {
        alerts.stream().collect(groupingBy(alertOrMargin -> alertOrMargin.alert.userId))
                .forEach((userId, userAlerts) -> discord.userChannel(userId).ifPresent(channel ->
                        channel.sendMessages(toMessage(userAlerts), emptyList())));
    }

    @NotNull
    private List<EmbedBuilder> toMessage(@NotNull List<AlertOrMargin> alerts) {
        return shrinkToPageSize(alerts.stream()
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(this::toMessage).collect(toList()), alerts.size());

    }

    private EmbedBuilder toMessage(@NotNull AlertOrMargin alertOrMargin) {
        Alert alert = alertOrMargin.alert;
        return embedBuilder(title(alert.name(), alert.getSlashPair(), alert.message, alertOrMargin.isMargin),
                alertOrMargin.isMargin ? Color.orange : Color.green,
                alert.asTriggered());
    }

    //TODO remove / simplify at the end
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

    private void updateAlerts(List<AlertOrMargin> alerts) {
        try {
            //TODO
        } catch (RuntimeException e) {
            LOGGER.error("Failed to save alerts", e);
        }
    }
}
