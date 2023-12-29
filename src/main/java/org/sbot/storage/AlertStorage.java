package org.sbot.storage;

import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface AlertStorage {

    Stream<Alert> getAlerts();

    Optional<Alert> getAlert(long id);

    Map<String, Map<String, List<Alert>>> getAlertsByPairsAndExchanges();

    void addAlert(@NotNull Alert alert, @NotNull Consumer<String> asyncErrorHandler);

    void deleteAlert(long alertId, @NotNull Consumer<String> asyncErrorHandler);
}
