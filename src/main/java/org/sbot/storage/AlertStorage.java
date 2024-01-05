package org.sbot.storage;

import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public interface AlertStorage {

    Stream<Alert> getAlerts();

    Optional<Alert> getAlert(long id);

    Map<String, Map<String, List<Alert>>> getAlertsByPairsAndExchanges();

    void addAlert(@NotNull Alert alert);

    default void updateAlert(@NotNull Alert alert) {
        updateAlerts(List.of(alert));
    }
    void updateAlerts(@NotNull List<Alert> alerts);

    void deleteAlert(long alertId);
}
