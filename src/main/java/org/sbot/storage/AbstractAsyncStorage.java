package org.sbot.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.alerts.Alert;
import org.sbot.discord.Discord;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class AbstractAsyncStorage extends MemoryStorage {

    private static final Logger LOGGER = LogManager.getLogger(AbstractAsyncStorage.class);

    protected abstract List<Alert> loadAlerts();
    protected abstract boolean saveAlerts();

    private final AtomicBoolean doUpdate = new AtomicBoolean(false);
    private final Executor flushingThread = new ThreadPoolExecutor(0, 1, 10L, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(), r -> new Thread(r, "SBOT async storage - " + doUpdate.hashCode()));
    {
        // load the alerts on start
        LOGGER.info("Loading alerts...");
        long start = System.currentTimeMillis();
        loadAlerts().forEach(super::addAlert);
        LOGGER.info("Alerts loaded in {}ms", (System.currentTimeMillis() - start));
    }

    @Override
    public final void addAlert(Alert alert) {
        super.addAlert(alert);
        doUpdate.set(true);
        flushingThread.execute(this::syncAlerts);
    }

    @Override
    public final boolean deleteAlert(long alertId) {
        try {
            return super.deleteAlert(alertId);
        } finally {
            doUpdate.set(true);
            flushingThread.execute(this::syncAlerts);
        }
    }

    private void syncAlerts() {
        if(doUpdate.compareAndSet(true, false)) {
            LOGGER.debug("Saving alerts...");
            try {
                if (!saveAlerts()) {
                    throw new RuntimeException();
                }
                LOGGER.debug("Alerts saved");
            } catch (RuntimeException e) {
                LOGGER.error("Failed to save alerts", e);
                Discord.spotBotChannel.sendMessage("Encountered a failure while updating persistent state of alerts");
            }
        } else {
            LOGGER.debug("Alerts saving skipped (already done)");
        }
    }
}
