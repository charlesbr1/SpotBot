package org.sbot.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;


public abstract class AbstractAsyncStorage extends MemoryStorage {

    private static final Logger LOGGER = LogManager.getLogger(AbstractAsyncStorage.class);

    private final AtomicBoolean doUpdate = new AtomicBoolean(false);
    private final Executor flushingThread = new ThreadPoolExecutor(0, 1, 10L, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(), r -> new Thread(r, "SBOT async storage - " + doUpdate.hashCode()));

    protected AbstractAsyncStorage() {
        // load the alerts on start
        LOGGER.info("Loading alerts...");
        long start = System.currentTimeMillis();
        loadAlerts().forEach(alert -> super.addAlert(alert, LOGGER::error));
        LOGGER.info("Alerts loaded in {}ms", (System.currentTimeMillis() - start));
    }

    @NotNull
    protected abstract List<Alert> loadAlerts();
    protected abstract boolean saveAlerts();

    @Override
    public final void addAlert(@NotNull Alert alert, @NotNull Consumer<String> asyncErrorHandler) {
        super.addAlert(alert, requireNonNull(asyncErrorHandler));
        doUpdate.set(true);
        flushingThread.execute(() -> syncAlerts(asyncErrorHandler));
    }

    @Override
    public final void deleteAlert(long alertId, @NotNull Consumer<String> asyncErrorHandler) {
        super.deleteAlert(alertId, requireNonNull(asyncErrorHandler));
        doUpdate.set(true);
        flushingThread.execute(() -> syncAlerts(asyncErrorHandler));
    }

    private void syncAlerts(@NotNull Consumer<String> asyncErrorHandler) {
        if(doUpdate.compareAndSet(true, false)) {
            LOGGER.debug("Saving alerts...");
            try {
                if (!saveAlerts()) {
                    throw new RuntimeException();
                }
                LOGGER.debug("Alerts saved");
            } catch (RuntimeException e) {
                doUpdate.set(true);
                LOGGER.error("Failed to save alerts", e);
                try {
                    asyncErrorHandler.accept("Encountered a failure while updating persistent state of alert ");
                } catch (RuntimeException ex) {
                    LOGGER.error("Failed to notify about storage failure", ex);
                }
            }
        } else {
            LOGGER.debug("Saving of alerts skipped (already done)");
        }
    }
}
