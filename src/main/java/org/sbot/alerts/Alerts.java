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
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.commands.CommandAdapter.embedBuilder;

public final class Alerts {

    private static final Logger LOGGER = LogManager.getLogger(Alerts.class);

    private final Discord discord;

    public Alerts(@NotNull Discord discord) {
        this.discord = requireNonNull(discord);
    }

    public void checkPricesAndSendAlerts(@NotNull AlertStorage alertStorage) {
        try {
            alertStorage.getAlertsByPairsAndExchanges().forEach((exchangeName, alertsByPairs) -> {
                Exchange exchange = Exchanges.get(exchangeName);
                new Thread(() -> getPricesAndTriggerAlerts(alertsByPairs, exchange),
                        "SBOT price fetcher : " + exchange).start();
            });
        } catch (RuntimeException e) {
            LOGGER.warn("Exception thrown", e);
        }
    }

    private void getPricesAndTriggerAlerts(@NotNull Map<String, List<Alert>> alertsByPairs, @NotNull Exchange exchange) {
        alertsByPairs.forEach((pair, alerts) -> {
            try {
                exchange.getCandlesticks(pair, TimeFrame.HOURLY, 1) // only one call by pair
                        .sorted(Comparator.comparing(Candlestick::openTime))
                        .forEach(candlestick -> triggerAlerts(alerts, candlestick));
            } catch(RuntimeException e) {
                LOGGER.warn("Exception thrown while processing alerts", e);
            }
        });
    }

    private void triggerAlerts(@NotNull List<Alert> alerts, @NotNull Candlestick candlestick) {
        matchingAlerts(alerts, candlestick).collect(groupingBy(Alert::getServerId))
                .forEach((serverId, alertsToTrigger) -> {
                    try {
                        if(PRIVATE_ALERT == serverId) {
                            alertsToTrigger.stream().collect(groupingBy(Alert::getUserId))
                                    .forEach((userId, userAlerts) -> {
                                        discord.userChannel(userId)
                                                .sendMessages(userAlerts.stream().map(this::toMessage).toList());
                                    });
                        } else {
                            discord.spotBotChannel(serverId)
                                    .sendMessages(alertsToTrigger.stream().map(this::toMessage).toList());
                        }
                    } catch (IllegalStateException e) {
                        LOGGER.error("Failed to send alert", e);
                    }
                });
    }

    @NotNull
    private Stream<Alert> matchingAlerts(@NotNull List<Alert> alerts, @NotNull Candlestick candlestick) {
        return alerts.stream().filter(alert -> alert.match(candlestick));
    }

    @NotNull
    private EmbedBuilder toMessage(@NotNull Alert alert) {
        return embedBuilder('[' + alert.getSlashPair() + "] " + alert.message,
                alert.isPrivate() ? Color.blue : (alert.isOver() ? Color.black : Color.green),
                alert.triggerMessage());
    }
}
