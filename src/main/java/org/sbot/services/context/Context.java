package org.sbot.services.context;

import org.apache.logging.log4j.LogManager;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.exchanges.Exchanges;
import org.sbot.services.AlertsWatcher;
import org.sbot.services.LastCandlesticksService;
import org.sbot.services.MatchingService;
import org.sbot.services.NotificationsService;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.memory.NotificationsMemory;
import org.sbot.services.dao.sql.AlertsSQLite;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;
import org.sbot.services.dao.sql.NotificationsSQLite;
import org.sbot.services.dao.sql.UsersSQLite;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;
import org.sbot.services.discord.Discord;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.sbot.SpotBot.ALERTS_CHECK_PERIOD_MINUTES_PROPERTY;
import static org.sbot.SpotBot.ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY;
import static org.sbot.services.context.TransactionalContext.DEFAULT_ISOLATION_LEVEL;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;

public interface Context {

    // register new data service here
    record DataServices(@NotNull Function<JDBITransactionHandler, UsersDao> usersDao,
                        @NotNull Function<JDBITransactionHandler, AlertsDao> alertsDao,
                        @NotNull Function<JDBITransactionHandler, NotificationsDao> notificationsDao,
                        @NotNull Function<JDBITransactionHandler, LastCandlesticksDao> lastCandlesticksDao) {
        @NotNull
        static DataServices load(@Nullable JDBIRepository repository) {
            if(null == repository) {
                LogManager.getLogger(DataServices.class).info("Loading data services in memory");
                var alertsDao = new AlertsMemory();
                var notificationsDao = new NotificationsMemory();
                var lastCandlesticksDao = new LastCandlesticksMemory();
                return new DataServices(v -> alertsDao.usersDao, v -> alertsDao, v -> notificationsDao, v -> lastCandlesticksDao);
            }
            LogManager.getLogger(DataServices.class).info("Loading data services SQLite");
            return new DataServices(new UsersSQLite(repository)::withHandler,
                    new AlertsSQLite(repository)::withHandler,
                    new NotificationsSQLite(repository)::withHandler,
                    new LastCandlesticksSQLite(repository)::withHandler);
        }
    }

    // register new service here
    record Services(@NotNull Discord discord,
                    @NotNull MatchingService matchingService,
                    @NotNull NotificationsService notificationService,
                    @NotNull AlertsWatcher alertsWatcher,
                    @NotNull Function<TransactionalContext, LastCandlesticksService> lastCandlesticksService) {
        @NotNull
        static Services load(@NotNull Context context, @NotNull Function<Context, Discord> discordLoader) {
            LogManager.getLogger(Services.class).info("Loading services Discord, MatchingService, AlertsWatcher, LastCandlesticksService");
            return new Services(requireNonNull(discordLoader.apply(context)),
                    new MatchingService(context),
                    new NotificationsService(context),
                    new AlertsWatcher(context),
                    LastCandlesticksService::new);
        }
    }

    record Parameters(@Nullable String databaseUrl, @NotNull String discordTokenFile, int checkPeriodMin, int hourlySyncDeltaMin) {

        public static final int MAX_CHECK_PERIOD = 60;
        public static final int MAX_HOURLY_SYNC_DELTA = 15;

        public Parameters {
            if (requireStrictlyPositive(checkPeriodMin) < requirePositive(hourlySyncDeltaMin)) { // values from properties file may be negative
                throw new IllegalArgumentException(ALERTS_CHECK_PERIOD_MINUTES_PROPERTY + " (" + checkPeriodMin + ") must be >= " + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY + " (" + hourlySyncDeltaMin + ")");
            } else if (checkPeriodMin > MAX_CHECK_PERIOD) {
                throw new IllegalArgumentException(ALERTS_CHECK_PERIOD_MINUTES_PROPERTY + " (" + checkPeriodMin + ") is too high : " + MAX_CHECK_PERIOD + " max");
            } else if (hourlySyncDeltaMin > MAX_HOURLY_SYNC_DELTA) {
                throw new IllegalArgumentException(ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY + " (" + hourlySyncDeltaMin + ") is too high : " + MAX_HOURLY_SYNC_DELTA + " max");
            } else if (null != databaseUrl && databaseUrl.isEmpty()) {
                throw new IllegalArgumentException("Missing database url");
            } else if (discordTokenFile.isEmpty()) {
                throw new IllegalArgumentException("Missing discord token file path");
            }
        }

        public static Parameters of(@Nullable String databaseUrl, @NotNull String discordTokenFile, int checkPeriodMin, int hourlySyncDeltaMin) {
            return new Parameters(databaseUrl, discordTokenFile, checkPeriodMin, hourlySyncDeltaMin);
        }
    }

    @NotNull
    Clock clock();
    @NotNull
    DataServices dataServices();
    @NotNull
    Services services();
    @NotNull
    Exchanges exchanges();
    @NotNull
    Parameters parameters();

    @NotNull
    default Discord discord() {
        return services().discord();
    }

    @NotNull
    default MatchingService matchingService() {
        return services().matchingService();
    }

    @NotNull
    default NotificationsService notificationService() {
        return services().notificationService();
    }

    @NotNull
    default AlertsWatcher alertsWatcher() {
        return services().alertsWatcher();
    }

    default ThreadSafeTxContext asThreadSafeTxContext(@NotNull TransactionIsolationLevel isolationLevel, int countdown) {
        return new ThreadSafeTxContext(this, isolationLevel, countdown);
    }

    default void transaction(@NotNull Consumer<TransactionalContext> transactionalContextConsumer) {
        transaction(transactionalContextConsumer, DEFAULT_ISOLATION_LEVEL, false);
    }

    default void transaction(@NotNull Consumer<TransactionalContext> transactionalContextConsumer, @NotNull TransactionIsolationLevel transactionIsolationLevel, boolean isolated) {
        requireNonNull(transactionalContextConsumer);
        transactional(v -> { transactionalContextConsumer.accept(v); return null; }, transactionIsolationLevel, isolated);
    }

    default <T> T transactional(@NotNull Function<TransactionalContext, T> transactionalContextConsumer) {
        return transactional(transactionalContextConsumer, DEFAULT_ISOLATION_LEVEL, false);
    }

    default <T> T transactional(@NotNull Function<TransactionalContext, T> transactionalContextConsumer, @NotNull TransactionIsolationLevel transactionIsolationLevel, boolean isolated) {
        return TransactionalContext.run(this, transactionalContextConsumer, transactionIsolationLevel, isolated);
    }

    @NotNull
    static Context of(@NotNull Clock clock, @NotNull Parameters parameters, @Nullable JDBIRepository repository, @NotNull Function<Context, Discord> discordLoader) {
        requireNonNull(clock);
        requireNonNull(parameters);
        return new Context() {

            private final DataServices dataServices = DataServices.load(repository);
            private final Services services = Services.load(this, discordLoader);
            private final Exchanges exchanges = new Exchanges();

            @NotNull
            @Override
            public Clock clock() { return clock; }
            @NotNull
            @Override
            public DataServices dataServices() { return dataServices; }
            @NotNull
            @Override
            public Services services() { return services; }
            @NotNull
            @Override
            public Exchanges exchanges() { return exchanges; }
            @NotNull
            @Override
            public Parameters parameters() { return parameters; }
        };
    }
}
