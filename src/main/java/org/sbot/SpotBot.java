package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.*;
import org.sbot.discord.Discord;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.AlertsWatcher;
import org.sbot.services.dao.AlertsSQL;
import org.sbot.utils.PropertiesReader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Long.parseLong;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.sbot.utils.PropertiesReader.loadProperties;


public class SpotBot {

    private static final Logger LOGGER = LogManager.getLogger(SpotBot.class);

    public static final PropertiesReader appProperties = loadProperties("spotbot.properties");

    private static final String DATABASE_URL = appProperties.get("database.url");

    private static final long ALERTS_CHECK_PERIOD_DELTA_MIN = parseLong(appProperties.get("alerts.check.period.delta"));


    public static void main(String[] args) {
        try {
            LOGGER.info("Starting SpotBot v1");

            AlertsDao alertsDao = new AlertsSQL(DATABASE_URL);
//            AlertsDao alertsDao = new AlertsMemory();
            Discord discord = new Discord();
            setupDiscordEvents(discord, alertsDao);
            AlertsWatcher alertsWatcher = new AlertsWatcher(discord, alertsDao);

            LOGGER.info("Entering infinite loop to check prices and send alerts every start of hours...");

            for(;;) {
                alertsWatcher.checkAlerts();
                long sleepingMinutes = minutesUntilNextHour() + ALERTS_CHECK_PERIOD_DELTA_MIN;
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

    private static void setupDiscordEvents(@NotNull Discord discord, @NotNull AlertsDao alertsDao) {
        LOGGER.info("Registering discord events...");
        discord.registerCommands(
                new UpTimeCommand(alertsDao),
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
                new SpotBotCommand(alertsDao));
    }
}
