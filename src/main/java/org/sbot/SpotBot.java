package org.sbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.Parameters;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.discord.Discord;
import org.sbot.services.discord.Discord.DiscordLoader;
import org.sbot.utils.PropertiesReader;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static org.sbot.commands.Commands.SPOTBOT_COMMANDS;
import static org.sbot.commands.interactions.Interactions.SPOTBOT_INTERACTIONS;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.nowUtc;
import static org.sbot.utils.PropertiesReader.loadProperties;


public class SpotBot {

    private static final String VERSION = "1.0";

    private static final Logger LOGGER = LogManager.getLogger(SpotBot.class);

    public static final PropertiesReader appProperties = loadProperties("spotbot.properties");

    static final String DATABASE_URL_PROPERTY = "database.url";
    static final String DISCORD_BOT_TOKEN_FILE_PROPERTY = "discord.token.file";
    static final String ALERTS_CHECK_PERIOD_MIN_PROPERTY = "alerts.check.period-minutes";
    static final String ALERTS_HOURLY_SYNC_DELTA_MIN_PROPERTY = "alerts.check.hourly-sync.delta-minutes";


    private static final String DATABASE_URL = appProperties.get(DATABASE_URL_PROPERTY);
    private static final String DISCORD_BOT_TOKEN_FILE = appProperties.get(DISCORD_BOT_TOKEN_FILE_PROPERTY);
    private static final int ALERTS_CHECK_PERIOD_MIN = Math.max(1, appProperties.getIntOr(ALERTS_CHECK_PERIOD_MIN_PROPERTY, 15));
    private static final int ALERTS_HOURLY_SYNC_DELTA_MIN = requirePositive(appProperties.getIntOr(ALERTS_HOURLY_SYNC_DELTA_MIN_PROPERTY, 3));



    public static void main(String[] args) {
        var params = Stream.ofNullable(args).flatMap(Stream::of).toList();
        if(params.stream().anyMatch("-help"::equals)) {
            System.out.println(help());
            System.exit(0);
        }
        Parameters appParameters = getParameters(params);
        if(null == appParameters) {
            LOGGER.error("Aborting");
            System.exit(1);
        }

        LOGGER.info("Starting SpotBot v{} with {} storage, discord token file {}, check period {} min, hourly sync delta {} min",
                VERSION, null == appParameters.databaseUrl() ? "in memory" : "SQLite " + appParameters.databaseUrl(),
                appParameters.discordTokenFile(), appParameters.checkPeriodMin(), appParameters.hourlySyncDeltaMin());

        int exitStatus = 0;

        try {
            var repository = Optional.ofNullable(appParameters.databaseUrl()).map(JDBIRepository::new).orElse(null);
            spotBot(Runnable::run, Clock.systemUTC(), appParameters, repository, Discord::new);
            LOGGER.info("Application shutdown");
        } catch (Throwable t) {
            LOGGER.error("Application shutdown with error", t);
            exitStatus = 2;
        } finally {
            LogManager.shutdown(); // flush the logs
        }
        System.exit(exitStatus);
    }

    public static void spotBot(@NotNull Executor executor, @NotNull Clock clock, @NotNull Parameters parameters, @Nullable JDBIRepository repository, @NotNull DiscordLoader discordLoader) {
        requireNonNull(clock);
        requireNonNull(parameters);
        executor.execute(() -> {
            LOGGER.info("Loading Context...");
            Context context = Context.of(clock, parameters, repository, ctx -> discordLoader.newInstance(ctx, SPOTBOT_COMMANDS, SPOTBOT_INTERACTIONS));

            LOGGER.info("Entering infinite loop to check prices and send alerts. Scheduling plan : {}",
                    schedulingPlan(nowUtc(clock), parameters.checkPeriodMin(), parameters.hourlySyncDeltaMin()));

            while(!Thread.interrupted()) {
                LOGGER.info("Thread [" + Thread.currentThread().getName() + "] now checking prices...");
                context.alertsWatcher().checkAlerts();
                long sleepingMinutes = minutesUntilNextCheck(nowUtc(clock), parameters.checkPeriodMin(), parameters.hourlySyncDeltaMin());
                LOGGER.info("Thread [" + Thread.currentThread().getName() + "] now sleeping for {} minutes...", sleepingMinutes);
                LockSupport.parkNanos(Duration.ofMinutes(sleepingMinutes).toNanos());
            }
            Thread.currentThread().interrupt();
        });
    }

    @Nullable
    static Parameters getParameters(@NotNull List<String> params) {
        try {
            boolean memoryDao = params.stream().anyMatch("-memory"::equals);
            String databaseUrl = memoryDao ? null : getParameter(params, DATABASE_URL_PROPERTY, DATABASE_URL, identity());
            String discordTokenFile = getParameter(params, DISCORD_BOT_TOKEN_FILE_PROPERTY, DISCORD_BOT_TOKEN_FILE, identity());
            int checkPeriodMin = getParameter(params, ALERTS_CHECK_PERIOD_MIN_PROPERTY, ALERTS_CHECK_PERIOD_MIN, v -> Math.max(1, Integer.parseInt(v)));
            int hourlySyncDeltaMin = getParameter(params, ALERTS_HOURLY_SYNC_DELTA_MIN_PROPERTY, ALERTS_HOURLY_SYNC_DELTA_MIN, v -> Math.max(0, Integer.parseInt(v)));
            return Parameters.of(databaseUrl, discordTokenFile, checkPeriodMin, hourlySyncDeltaMin);
        } catch (RuntimeException e) {
            LOGGER.error("Invalid parameter", e);
            return null;
        }
    }

    static <T> T getParameter(@NotNull List<String> args, @NotNull String name, @Nullable T defaultValue, @NotNull Function<String, T> mapper) {
        int index = args.indexOf('-' + requireNonNull(name)) + 1;
        if(index > 0 && index < args.size()) {
            String value = args.get(index);
            if(value.startsWith("-")) {
                throw new IllegalArgumentException(value + " is not a valid value for " + name);
            }
            defaultValue = mapper.apply(value);
        }
        LOGGER.info("parameter : {} = {}", name, defaultValue);
        return defaultValue;
    }

    static String help() {
        var help = "SpotBot v" + VERSION + "\ncommand line arguments (override spotbot.properties) :\n";
        help += "\t-memory" + " : use internal memory storage (data will be loose on shutdown)\n";
        help += "\t-" + DATABASE_URL_PROPERTY + " 'url' : use database url (SQLite)\n";
        help += "\t-" + DISCORD_BOT_TOKEN_FILE_PROPERTY + " 'file path' : use discord token file path\n";
        help += "\t-" + ALERTS_CHECK_PERIOD_MIN_PROPERTY + " 'positive number' : set the period between two check of the alerts (minutes)\n";
        help += "\t-" + ALERTS_HOURLY_SYNC_DELTA_MIN_PROPERTY + " 'positive number' : set the hourly sync delta for checks (minutes)";
        return help;
    }

    @NotNull
    static String schedulingPlan(@NotNull ZonedDateTime now, int checkPeriodMin, int hourlySyncDeltaMin) {
        long nbChecksFirstHour = Math.ceilDiv(ChronoUnit.MINUTES.between(now, now.truncatedTo(HOURS).plusHours(1L).plusMinutes(1L)), checkPeriodMin);
        long nbChecksByHour = 1 + Math.ceilDiv(60 - hourlySyncDeltaMin, checkPeriodMin);
        ZonedDateTime[] future = {now};
        return "First hour : now" + (nbChecksFirstHour > 1 ? ", " : "") +
                LongStream.of(nbChecksFirstHour - 1, nbChecksByHour).mapToObj(nbChecks ->
                        LongStream.range(0, nbChecks)
                                .mapToObj(i ->  (future[0] = future[0].plusMinutes(minutesUntilNextCheck(future[0], checkPeriodMin, hourlySyncDeltaMin)))
                                        .format(DateTimeFormatter.ofPattern("HH'h'mm")) + " (UTC)")
                                .collect(joining(", "))).collect(joining(". Following hour : "));
    }

    static long minutesUntilNextCheck(@NotNull ZonedDateTime now, int checkPeriodMin, int hourlySyncDeltaMin) {
        long hourlyDelta = ChronoUnit.SECONDS.between(now, now.truncatedTo(HOURS).plusMinutes(hourlySyncDeltaMin));
        if(hourlyDelta > 0) {
            return Math.ceilDiv(hourlyDelta, 60);
        }
        ZonedDateTime startOfNextHour = now.truncatedTo(HOURS)
                .plusHours(1L)
                .plusMinutes(1 + requirePositive(hourlySyncDeltaMin));
        return Math.min(ChronoUnit.MINUTES.between(now, startOfNextHour),
                        ChronoUnit.MINUTES.between(now, now.plusMinutes(checkPeriodMin)));
    }
}
