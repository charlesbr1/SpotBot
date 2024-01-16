package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.discord.Discord;
import org.sbot.services.AlertsWatcher;
import org.sbot.services.MarketDataService;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.sql.AlertsSQLite;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.utils.PropertiesReader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.sbot.utils.PropertiesReader.loadProperties;


public class SpotBot {

    private static final Logger LOGGER = LogManager.getLogger(SpotBot.class);

    public static final PropertiesReader appProperties = loadProperties("spotbot.properties");

    private static final String DATABASE_URL = appProperties.get("database.url");

    private static final int ALERTS_HOURLY_CHECK_DELTA_MIN = appProperties.getInt("alerts.hourly-check.delta");


    public static void main(String[] args) {
        try {
            boolean memoryDao = Stream.ofNullable(args).flatMap(Stream::of).findFirst()
                    .filter("-memory"::equals).isPresent();

            LOGGER.info("Starting SpotBot v1.0 with {} storage", memoryDao ? "memory" : "SQLite");

            // load data storage
            JDBIRepository repository = memoryDao ? null : new JDBIRepository(DATABASE_URL);
            AlertsDao alertsDao = memoryDao ? new AlertsMemory() : new AlertsSQLite(repository);
            LastCandlesticksDao lastCandlestickDao = memoryDao ? new LastCandlesticksMemory() : new LastCandlesticksSQLite(repository);

            // load services
            Discord discord = new Discord(alertsDao);
            MarketDataService marketDataService =  new MarketDataService(lastCandlestickDao);
            AlertsWatcher alertsWatcher = new AlertsWatcher(discord, alertsDao, marketDataService);

            LOGGER.info("Entering infinite loop to check prices and send alerts every start of hours...");

            for(;;) {
                alertsWatcher.checkAlerts();
                long sleepingMinutes = minutesUntilNextHour() + ALERTS_HOURLY_CHECK_DELTA_MIN;
                LOGGER.info("Main thread now sleeping for {} minutes...", sleepingMinutes);
                LockSupport.parkNanos(Math.max(Duration.ofMinutes(1L).toNanos(),
                        Duration.ofMinutes(sleepingMinutes).toNanos()));
            }
        } catch (Throwable t) {
            LOGGER.info("Bye...", t);
            LogManager.shutdown(); // flush the logs
            System.exit(1);
        }
    }

    private static long minutesUntilNextHour() {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime startOfNextHour = currentTime.truncatedTo(HOURS).plusHours(1L).plusMinutes(1L);
        return ChronoUnit.MINUTES.between(currentTime, startOfNextHour);
    }
}
