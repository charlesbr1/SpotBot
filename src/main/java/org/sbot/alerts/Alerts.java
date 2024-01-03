package org.sbot.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;

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
            alertStorage.getAlertsByPairsAndExchanges().forEach((exchangeName, alertsByPairs) -> {
                Exchange exchange = Exchanges.get(exchangeName);
                alertsByPairs.forEach((pair, alerts) ->
                        threadPool.execute(() -> getPricesAndTriggerAlerts(exchange, pair, alerts)));
            });
        } catch (RuntimeException e) {
            LOGGER.warn("Exception thrown", e);
        }
    }

    private void getPricesAndTriggerAlerts(@NotNull Exchange exchange, @NotNull String pair, @NotNull List<Alert> alerts) {
        try {
            LOGGER.debug("Retrieving price for pair [{}] on {}...", pair, exchange);
            List<Candlestick> prices = exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1) // only one call by pair
                    .sorted(Comparator.comparing(Candlestick::openTime)).toList();

            alerts.stream().filter(alert -> prices.stream().anyMatch(alert::match))
                    .collect(groupingBy(Alert::getServerId))
                    .forEach(this::sendAlerts);
        } catch(RuntimeException e) {
            LOGGER.warn("Exception thrown while processing alerts", e);
        }
    }

    private void sendAlerts(long serverId, @NotNull List<Alert> alerts) {
        try {
            if(PRIVATE_ALERT == serverId) {
                sendPrivateAlerts(alerts);
            } else {
                discord.spotBotChannel(serverId).sendMessages(shrinkToPageSize(alerts),
                        List.of(message -> message.mentionUsers(alerts.stream().mapToLong(Alert::getUserId).distinct().toArray())));
            }
        } catch (RuntimeException e) {
            LOGGER.error("Failed to send alerts", e);
        }
    }

    private void sendPrivateAlerts(@NotNull List<Alert> alerts) {
        alerts.stream().collect(groupingBy(Alert::getUserId))
                .forEach((userId, userAlerts) -> discord.userChannel(userId)
                        .ifPresent(channel -> channel.sendMessages(shrinkToPageSize(userAlerts),
                                List.of(message -> message.mentionUsers(userId)))));
    }

    private List<EmbedBuilder> shrinkToPageSize(@NotNull List<Alert> alerts) {
        List<EmbedBuilder> messages = alerts.stream()
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(this::toMessage).collect(toList());
        if(messages.size() > MESSAGE_PAGE_SIZE) {
            while(messages.size() >= MESSAGE_PAGE_SIZE) {
                messages.remove(messages.size() - 1);
            }
            messages.add(embedBuilder("...", Color.red, "Limit reached ! That's too much alerts.\n\n" +
                    (alerts.size() - MESSAGE_PAGE_SIZE + 1) + " more alerts were triggered but are discarded"));
        }
        return messages;
    }

    @NotNull
    private EmbedBuilder toMessage(@NotNull Alert alert) {
        return embedBuilder('[' + alert.getSlashPair() + "] " + alert.message,
                alert.isPrivate() ? Color.blue : (alert.isOver() ? Color.black : Color.green),
                alert.triggerMessage());
    }
}
