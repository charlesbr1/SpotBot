package org.sbot.alerts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.discord.Discord.SpotBotChannel;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.storage.AlertStorage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public final class Alerts {

    private static final Logger LOGGER = LogManager.getLogger(Alerts.class);

    private final SpotBotChannel spotBotChannel;

    public Alerts(@NotNull SpotBotChannel spotBotChannel) {
        this.spotBotChannel = requireNonNull(spotBotChannel);
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
        String alertsToTrigger = matchingAlerts(alerts, candlestick);
        if(!alertsToTrigger.isEmpty()) {
            spotBotChannel.sendMessage(alertsToTrigger);
        }
    }

    @NotNull
    private String matchingAlerts(@NotNull List<Alert> alerts, @NotNull Candlestick candlestick) {
        return alerts.stream().filter(alert -> alert.match(candlestick))
                .map(alert -> "@sbot ALERT triggered by " + alert.notification())
                .collect(Collectors.joining("\n"));
    }
}
