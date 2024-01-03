package org.sbot.alerts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.discord.Discord;
import org.sbot.discord.Discord.BotChannel;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.storage.AlertStorage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

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
                new Thread(() -> checkPricesAndSendAlerts(alertsByPairs, exchange),
                        "SBOT price fetcher : " + exchange).start();
            });
        } catch (RuntimeException e) {
            LOGGER.warn("Exception thrown", e);
        }
    }

    private void checkPricesAndSendAlerts(@NotNull Map<String, List<Alert>> alertsByPairs, @NotNull Exchange exchange) {
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
                        // TODO gere le cas serveur prive PRIVATE_ALERT != serverId ?
                        BotChannel botChannel = discord.spotBotChannel(serverId);
                        alertsToTrigger.stream()
                                .map(alert -> "@sbot ALERT triggered by " + alert.triggerMessage())
                                .forEach(botChannel::sendMessage);
                    } catch (IllegalStateException e) {
                        LOGGER.error("Failed to send alert", e);
                    }
                });
    }

    @NotNull
    private Stream<Alert> matchingAlerts(@NotNull List<Alert> alerts, @NotNull Candlestick candlestick) {
        return alerts.stream().filter(alert -> alert.match(candlestick));
    }
}
