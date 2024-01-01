package org.sbot.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class MemoryStorage implements AlertStorage {

    private static final Logger LOGGER = LogManager.getLogger(MemoryStorage.class);

    private final Map<Long, Alert> alerts = new ConcurrentHashMap<>();

    {
        LOGGER.debug("Loading memory storage for alerts");
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
    public void deleteAlert(long alertId) {
        LOGGER.debug("Deleting alert {}", alertId);
        alerts.remove(alertId);
    }
}
