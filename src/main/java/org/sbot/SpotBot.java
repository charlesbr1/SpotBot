package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alerts;
import org.sbot.commands.*;
import org.sbot.discord.Discord;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.PropertiesReader;

import static java.lang.Long.parseLong;
import static org.sbot.utils.PropertiesReader.loadProperties;


public enum SpotBot {
    ;

    private static final Logger LOGGER = LogManager.getLogger(SpotBot.class);

    private static final PropertiesReader appProperties = loadProperties("spotbot.properties");

    public static final String DISCORD_BOT_CHANNEL = appProperties.get("discord.bot.channel");
    private static final long ALERTS_CHECK_PERIOD_MS = 60L * 1000L * parseLong(appProperties.get("alerts.check.period.minutes"));
    private static final String ALERTS_STORAGE_CLASS = appProperties.get("alerts.storage.class");


    public static void main(String[] args) {
        try {
            LOGGER.info("Starting SpotBot v1");

            AlertStorage alertStorage = setupStorage();

            Discord discord = new Discord();
            setupDiscordEvents(discord, alertStorage);

            LOGGER.info("Entering infinite loop to check prices and send alerts every hours...");
            Alerts alerts = new Alerts(discord);
            for(;;) {
                alerts.checkPricesAndSendAlerts(alertStorage);
                Thread.sleep(ALERTS_CHECK_PERIOD_MS);
            }
        } catch (Throwable t) {
            LOGGER.info("Application exit", t);
            System.exit(1);
        }
    }

    @NotNull
    private static AlertStorage setupStorage() throws Exception {
        LOGGER.info("Loading Alert storage service {}...", ALERTS_STORAGE_CLASS);
        return (AlertStorage) Class.forName(ALERTS_STORAGE_CLASS)
                .getDeclaredConstructor().newInstance();
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
                new ThresholdCommand(alertStorage),
                new HelpCommand(alertStorage));
    }
}
