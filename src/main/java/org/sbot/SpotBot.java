package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.Parameters;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.discord.Discord;
import org.sbot.utils.PropertiesReader;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static org.sbot.commands.Commands.SPOTBOT_COMMANDS;
import static org.sbot.commands.interactions.Interactions.SPOTBOT_INTERACTIONS;
import static org.sbot.services.context.Context.Parameters.MAX_CHECK_PERIOD;
import static org.sbot.services.context.Context.Parameters.MAX_HOURLY_SYNC_DELTA;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.nowUtc;
import static org.sbot.utils.PropertiesReader.loadProperties;


public class SpotBot {

    static final String VERSION = "1.0";

    private static final Logger LOGGER = LogManager.getLogger(SpotBot.class);

    public static final PropertiesReader appProperties = loadProperties("spotbot.properties");

    public static final String DATABASE_URL_PROPERTY = "database.url";
    public static final String DISCORD_BOT_TOKEN_FILE_PROPERTY = "discord.token.file";
    public static final String ALERTS_CHECK_PERIOD_MINUTES_PROPERTY = "alerts.check.period-minutes";
    public static final String ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY = "alerts.check.hourly-sync.delta-minutes";

    private static final int DEFAULT_CHECK_PERIOD_MINUTES = 15;
    private static final int DEFAULT_HOURLY_SYNC_DELTA_MINUTES = 3;

    private static final int EXIT_APP_KILLED = 0;
    private static final int EXIT_BAD_PARAMETERS = 1;
    private static final int EXIT_APP_ERROR = 2;


    public static void main(String[] args) {

        var parameters = appParameters(args);

        LOGGER.info("Starting SpotBot v{} with {} storage, parameters : {}",
                VERSION, null == parameters.databaseUrl() ? "in memory" : "SQLite", parameters);

        int exitStatus = EXIT_APP_KILLED;

        try {
            LOGGER.info("Loading Context...");
            var repository = Optional.ofNullable(parameters.databaseUrl()).map(JDBIRepository::new).orElse(null);
            Context context = Context.of(Clock.systemUTC(), parameters, repository, ctx -> new Discord(ctx, SPOTBOT_COMMANDS, SPOTBOT_INTERACTIONS));
            spotBotThread(context).run();
            LOGGER.info("Application shutdown");
        } catch (Throwable t) {
            LOGGER.error("Application error", t);
            exitStatus = EXIT_APP_ERROR;
        } finally {
            LogManager.shutdown(); // flush the logs
            System.exit(exitStatus);
        }
    }

    @NotNull
    public static Runnable spotBotThread(@NotNull Context context) {
        requireNonNull(context);
        return (() -> {
            LOGGER.info("Entering infinite loop to check prices and send alerts. Scheduling plan (UTC time) : {}",
                    schedulingPlan(nowUtc(context.clock()), context.parameters().checkPeriodMin(), context.parameters().hourlySyncDeltaMin()));

            while(!Thread.interrupted()) {
                LOGGER.info("SpotBot thread [{}] now checking alerts...", Thread.currentThread().getName());
                long sleepingMinutes = checkAlerts(context);
                LOGGER.info("SpotBot thread [{}] now sleeping for {} minutes...", Thread.currentThread().getName(), sleepingMinutes);
                LockSupport.parkNanos(Duration.ofMinutes(sleepingMinutes).toNanos());
            }
            Thread.currentThread().interrupt();
        });
    }

    static long checkAlerts(@NotNull Context context) {
        context.alertsWatcher().checkAlerts();
        return minutesUntilNextCheck(nowUtc(context.clock()),
                context.parameters().checkPeriodMin(),
                context.parameters().hourlySyncDeltaMin());
    }

    @NotNull
    private static Parameters appParameters(String[] args) {
        var params = Stream.ofNullable(args).flatMap(Stream::of).toList();
        if(params.size() == 1 && requireOneItem(params).equals("-help")) {
            System.out.println(help(true));
            System.exit(0);
        }
        try {
            return getParameters(params);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid parameters, aborting...", e);
            System.exit(EXIT_BAD_PARAMETERS);
            throw e;
        }
    }

    @NotNull
    static Parameters getParameters(@NotNull List<String> params) {
        String propDatabaseUrl = appProperties.getOr(DATABASE_URL_PROPERTY, null);
        String propDiscordTokenFile = appProperties.getOr(DISCORD_BOT_TOKEN_FILE_PROPERTY, null);
        int propCheckPeriodMin = appProperties.getIntOr(ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, DEFAULT_CHECK_PERIOD_MINUTES);
        int propHourlySyncDeltaMin = appProperties.getIntOr(ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, DEFAULT_HOURLY_SYNC_DELTA_MINUTES);

        params = new ArrayList<>(params);
        boolean memoryDao = params.removeIf("-memory"::equals);
        String databaseUrl = memoryDao ? null : consumeParameter(params, DATABASE_URL_PROPERTY, propDatabaseUrl, identity());
        String discordTokenFile = consumeParameter(params, DISCORD_BOT_TOKEN_FILE_PROPERTY, propDiscordTokenFile, identity());
        int checkPeriodMin = consumeParameter(params, ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, propCheckPeriodMin, Integer::parseInt);
        int hourlySyncDeltaMin = consumeParameter(params, ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, propHourlySyncDeltaMin, Integer::parseInt);

        if(!params.isEmpty()) {
            throw new IllegalArgumentException("Too many arguments\n" + help(false));
        }
        return Parameters.of(databaseUrl, discordTokenFile, checkPeriodMin, hourlySyncDeltaMin);
    }

    static <T> T consumeParameter(@NotNull List<String> mutatedArgs, @NotNull String name, T defaultValue, @NotNull Function<String, T> mapper) {
        requireNonNull(mapper);
        int index = mutatedArgs.indexOf('-' + requireNonNull(name)) + 1;
        if(index > 0) {
            if(index < mutatedArgs.size()) {
                String value = mutatedArgs.get(index);
                if(value.startsWith("-")) { // this reject missing expected values or negative numbers
                    throw new IllegalArgumentException(value + " is not a valid value for " + name);
                }
                defaultValue = mapper.apply(value);
                mutatedArgs.remove(index);
            } else {
                throw new IllegalArgumentException("Missing value for parameter " + name);
            }
            mutatedArgs.remove(index - 1);
        }
        LOGGER.info("parameter : {} = {}", name, defaultValue);
        return defaultValue;
    }

    @NotNull
    static String help(boolean header) {
        var help = header ? "SpotBot v" + VERSION + '\n' : "";
        help += "command line arguments (override spotbot.properties) :\n";
        help += "\t-memory" + " : use internal memory storage (data will be loose on shutdown)\n";
        help += "\t-" + DATABASE_URL_PROPERTY + " 'url' : use database url (SQLite)\n";
        help += "\t-" + DISCORD_BOT_TOKEN_FILE_PROPERTY + " 'file path' : use discord token file path\n";
        help += "\t-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY + " 'positive number' : set the period between two check of the alerts (minutes, " + MAX_CHECK_PERIOD + " max)\n";
        help += "\t-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY + " 'positive number' : set the hourly sync delta for checks (minutes, " + MAX_HOURLY_SYNC_DELTA + " max, must be <= " + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY + ")";
        return help;
    }

    @NotNull
    static CharSequence schedulingPlan(@NotNull ZonedDateTime now, int checkPeriodMin, int hourlySyncDeltaMin) {
        ZonedDateTime[] date = {now};
        StringBuilder plan = new StringBuilder("First hour : now");
        IntConsumer fillHours = hour -> {
            while(hour == (date[0] = date[0].plusMinutes(minutesUntilNextCheck(date[0], checkPeriodMin, hourlySyncDeltaMin))).getHour())
                plan.append(", ").append(date[0].format(DateTimeFormatter.ofPattern("HH'h'mm")));
        };
        fillHours.accept(now.getHour());
        plan.append(". Following hour : ").append(date[0].format(DateTimeFormatter.ofPattern("HH'h'mm")));
        fillHours.accept(date[0].getHour());
        return plan;
    }

    static long minutesUntilNextCheck(@NotNull ZonedDateTime now, int checkPeriodMin, int hourlySyncDeltaMin) {
        long hourlyDelta = ChronoUnit.SECONDS.between(now, now.truncatedTo(HOURS).plusMinutes(requirePositive(hourlySyncDeltaMin)));
        if(hourlyDelta > 0) { // beginning of a new hour, wait until hourlySyncDeltaMin
            return Math.ceilDiv(hourlyDelta, 60);
        }
        ZonedDateTime startOfNextHour = now.truncatedTo(HOURS).plusHours(1L);
        return now.plusMinutes(requireStrictlyPositive(checkPeriodMin)).isBefore(startOfNextHour) ?
                ChronoUnit.MINUTES.between(now, now.plusMinutes(checkPeriodMin)) :
                ChronoUnit.MINUTES.between(now, startOfNextHour.plusMinutes(hourlySyncDeltaMin));
    }
}
