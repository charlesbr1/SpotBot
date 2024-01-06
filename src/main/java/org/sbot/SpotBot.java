package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alerts;
import org.sbot.commands.*;
import org.sbot.discord.Discord;
import org.sbot.storage.AlertStorage;
import org.sbot.storage.MemoryStorage;
import org.sbot.utils.PropertiesReader;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.LockSupport;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.sbot.utils.PropertiesReader.loadProperties;


public class SpotBot {

    private static final Logger LOGGER = LogManager.getLogger(SpotBot.class);

    public static final PropertiesReader appProperties = loadProperties("spotbot.properties");


    public static void main(String[] args) {
        try {
            LOGGER.info("Starting SpotBot v1");

            AlertStorage alertStorage = new MemoryStorage();
            Discord discord = new Discord();
            setupDiscordEvents(discord, alertStorage);
            Alerts alerts = new Alerts(discord, alertStorage);

            LOGGER.info("Entering infinite loop to check prices and send alerts every start of hours...");

            for(;;) {
                alerts.checkPricesAndSendAlerts(alertStorage);
                long sleepingMinutes = minutesUntilNextHour() + 5;
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

    private static void setupDiscordEvents(@NotNull Discord discord, @NotNull AlertStorage alertStorage) {
        LOGGER.info("Registering discord events...");
        discord.registerCommands(
                new UpTimeCommand(alertStorage),
                new RangeCommand(alertStorage),
                new DeleteCommand(alertStorage),
                new TrendCommand(alertStorage),
                new ListCommand(alertStorage),
                new OwnerCommand(alertStorage),
                new PairCommand(alertStorage),
                new RepeatCommand(alertStorage),
                new RepeatDelayCommand(alertStorage),
                new MarginCommand(alertStorage),
                new MessageCommand(alertStorage),
                new RemainderCommand(alertStorage),
                new SpotBotCommand(alertStorage));
    }
}
