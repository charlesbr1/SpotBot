package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.*;
import org.sbot.discord.CommandListener;
import org.sbot.discord.Discord;
import org.sbot.services.AlertsWatcher;
import org.sbot.services.MarketDataService;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.sqlite.AlertsSQLite;
import org.sbot.services.dao.sqlite.LastCandlesticksSQLite;
import org.sbot.services.dao.sqlite.jdbi.JDBIRepository;
import org.sbot.utils.PropertiesReader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
            Discord discord = new Discord(loadDiscordCommands(alertsDao));
            MarketDataService marketDataService =  new MarketDataService(lastCandlestickDao);
            AlertsWatcher alertsWatcher = new AlertsWatcher(discord, alertsDao, marketDataService);

            LOGGER.info("Entering infinite loop to check prices and send alerts every start of hours...");

            for(;;) {
                alertsWatcher.checkAlerts();
                long sleepingMinutes = minutesUntilNextHour() + ALERTS_HOURLY_CHECK_DELTA_MIN;
                LOGGER.info("Main thread now sleeping for {} minutes...", sleepingMinutes);
                LockSupport.parkNanos(Duration.ofMinutes(sleepingMinutes).toNanos());
            }
        } catch (Throwable t) {
            LOGGER.info("Bye...", t);
            LogManager.shutdown(); // flush the logs
            System.exit(1);
        }
    }

    private static long minutesUntilNextHour() {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime startOfNextHour = currentTime.truncatedTo(HOURS).plusHours(1);
        return ChronoUnit.MINUTES.between(currentTime, startOfNextHour);
    }

    @NotNull
    private static List<CommandListener> loadDiscordCommands(@NotNull AlertsDao alertsDao) {
        return List.of(
                new RangeCommand(alertsDao),
                new DeleteCommand(alertsDao),
                new TrendCommand(alertsDao),
                new ListCommand(alertsDao),
                new OwnerCommand(alertsDao),
                new PairCommand(alertsDao),
                new RepeatCommand(alertsDao),
                new RepeatDelayCommand(alertsDao),
                new MarginCommand(alertsDao),
                new MessageCommand(alertsDao),
                new RemainderCommand(alertsDao),
                new SpotBotCommand(alertsDao),
                new TimeZoneCommand(alertsDao),
                new UpTimeCommand(alertsDao));
    }
}
