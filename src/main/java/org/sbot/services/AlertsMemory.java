package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.alerts.RangeAlert;
import org.sbot.alerts.TrendAlert;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class AlertsMemory implements Alerts {

    private static final Logger LOGGER = LogManager.getLogger(AlertsMemory.class);

    private final Map<Long, Alert> alerts = new ConcurrentHashMap<>();

    {
        LOGGER.debug("Loading memory storage for alerts");
        fakeAlerts();
    }

    private void fakeAlerts() {
        for (int i = 0; i < 1; i++) {
            Alert alert = new RangeAlert(787813751151657001L, 0, "binance", "ETH", "BTC", BigDecimal.valueOf(1), BigDecimal.valueOf(2), "1".repeat(210))
                    .withRepeat((short) 0);
            alerts.put(alert.id, alert);
            alert = new TrendAlert(787813751151657001L, 0, "binance", "ETH", "EUR", BigDecimal.valueOf(1), ZonedDateTime.now(), BigDecimal.valueOf(2), ZonedDateTime.now(), "zone conso 1 blabla et encore balb a voir un lien je met du test j'allonge, lalalal la, https://discord.com/channels/@me/1189726086720921670/1191938967017361508")
                    .withRepeat((short) 0);
            alerts.put(alert.id, alert);
            alert = new TrendAlert(787813751151657001L, 0, "binance", "ETH", "BTC", BigDecimal.valueOf(1), ZonedDateTime.now(), BigDecimal.valueOf(2), ZonedDateTime.now(), "Dans cet exemple, stripTrailingZeros() est utilisé pour supprimer les zéros inutiles à la fin du BigDecimal. Ensuite, toString() est appelé sur le BigDecimal résultant.")
                    .withRepeat((short) 0);
            alerts.put(alert.id, alert);
        }
        for (int i = 0; i < 1; i++) {
            Alert alert = new RangeAlert(787813751151657001L, 1189584422891163758L, "binance", "ETH", "EUR", BigDecimal.valueOf(1), BigDecimal.valueOf(2), "test");
            alerts.put(alert.id, alert);
            alert = new TrendAlert(787813751151657001L, 1189584422891163758L, "binance", "ETH", "BTC", BigDecimal.valueOf(1), ZonedDateTime.now(), BigDecimal.valueOf(2), ZonedDateTime.now(), "test2");
            alerts.put(alert.id, alert);
        }
    }


        @Override
    public Stream<Alert> getAlerts() {
         return new ArrayList<>(alerts.values()).stream();
    }

    @Override
    public Optional<Alert> getAlert(long alertId) {
        LOGGER.debug("Getting alert {}", alertId);
        return Optional.ofNullable(alerts.get(alertId));
    }

    @Override
    public Map<String, Map<String, List<Alert>>> getAlertsByPairsAndExchanges() {
        return alerts.values().stream()
                .collect(groupingBy(Alert::getExchange, groupingBy(Alert::getSlashPair)));
    }

    @Override
    public void addAlert(@NotNull Alert alert) {
        LOGGER.debug("Adding alert {}", alert);
        alerts.put(alert.id, alert);
    }

    @Override
    public void updateAlerts(@NotNull List<Alert> alerts) {
        alerts.forEach(this::addAlert);
    }

    @Override
    public void deleteAlert(long alertId) {
        LOGGER.debug("Deleting alert {}", alertId);
        alerts.remove(alertId);
    }
}
