package org.sbot.alerts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.discord.Discord;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.storage.AlertStorage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum Alerts {
    ;

    private static final Logger LOGGER = LogManager.getLogger(Alerts.class);

    public static void checkPricesAndSendAlerts(AlertStorage alertStorage) {
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

    private static void checkPricesAndSendAlerts(Map<String, List<Alert>> alertsByPairs, Exchange exchange) {
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

    private static void triggerAlerts(List<Alert> alerts, Candlestick candlestick) {
        String alertsToTrigger = matchingAlerts(alerts, candlestick);
        if(!alertsToTrigger.isEmpty()) {
            Discord.spotBotChannel.sendMessage(alertsToTrigger);
        }
    }

    private static String matchingAlerts(List<Alert> alerts, Candlestick candlestick) {
        return alerts.stream().filter(alert -> alert.match(candlestick))
                .map(alert -> "@sbot ALERT triggered by " + alert.notification())
                .collect(Collectors.joining("\n"));
    }
}
