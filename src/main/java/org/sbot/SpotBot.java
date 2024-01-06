package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.AlertsWatcher;
import org.sbot.commands.*;
import org.sbot.discord.Discord;
import org.sbot.services.Alerts;
import org.sbot.services.AlertsMemory;
import org.sbot.services.jdbi.JDBIRepository;
import org.sbot.utils.PropertiesReader;

import java.time.Duration;
import java.time.LocalTime;
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

            JDBIRepository repository = new JDBIRepository(DATABASE_URL);
            Alerts alerts = new AlertsMemory();
            Discord discord = new Discord();
            setupDiscordEvents(discord, alerts);
            AlertsWatcher alertsWatcher = new AlertsWatcher(discord, alerts);

            LOGGER.info("Entering infinite loop to check prices and send alerts every start of hours...");

            for(;;) {
                alertsWatcher.checkAlerts(alerts);
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
        LocalTime currentTime = LocalTime.now();
        LocalTime startOfNextHour = currentTime.truncatedTo(HOURS).plusHours(1);
        return ChronoUnit.MINUTES.between(currentTime, startOfNextHour);
    }

    private static void setupDiscordEvents(@NotNull Discord discord, @NotNull Alerts alerts) {
        LOGGER.info("Registering discord events...");
        discord.registerCommands(
                new UpTimeCommand(alerts),
                new RangeCommand(alerts),
                new DeleteCommand(alerts),
                new TrendCommand(alerts),
                new ListCommand(alerts),
                new OwnerCommand(alerts),
                new PairCommand(alerts),
                new RepeatCommand(alerts),
                new RepeatDelayCommand(alerts),
                new MarginCommand(alerts),
                new MessageCommand(alerts),
                new RemainderCommand(alerts),
                new SpotBotCommand(alerts));
    }
}
