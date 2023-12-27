package org.sbot.storage;

import org.sbot.alerts.Alert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public interface AlertStorage {

    Stream<Alert> getAlerts();

    Optional<Alert> getAlert(long id);

    Map<String, Map<String, List<Alert>>> getAlertsByPairsAndExchanges();

    void addAlert(Alert alert);

    boolean deleteAlert(long alertId);
}
