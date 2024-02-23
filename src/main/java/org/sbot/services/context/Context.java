package org.sbot.services.context;

import org.apache.logging.log4j.LogManager;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.exchanges.Exchanges;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.dao.memory.UsersMemory;
import org.sbot.services.dao.sql.UsersSQLite;
import org.sbot.services.discord.Discord;
import org.sbot.services.AlertsWatcher;
import org.sbot.services.LastCandlesticksService;
import org.sbot.services.MatchingService;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.sql.AlertsSQLite;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.sbot.services.context.TransactionalContext.DEFAULT_ISOLATION_LEVEL;

public interface Context {

    // register new data service here
    record DataServices(@NotNull Function<JDBITransactionHandler, UsersDao> usersDao,
                        @NotNull Function<JDBITransactionHandler, AlertsDao> alertsDao,
                        @NotNull Function<JDBITransactionHandler, LastCandlesticksDao> lastCandlesticksDao) {
        @NotNull
        static DataServices load(@Nullable JDBIRepository repository) {
            if(null == repository) {
                LogManager.getLogger(DataServices.class).info("Loading data services in memory");
                var usersDao = new UsersMemory();
                var alertsDao = new AlertsMemory();
                var lastCandlesticksDao = new LastCandlesticksMemory();
                return new DataServices(v -> usersDao, v -> alertsDao, v -> lastCandlesticksDao);
            }
            LogManager.getLogger(DataServices.class).info("Loading data services SQLite");
            return new DataServices(new UsersSQLite(repository)::withHandler, new AlertsSQLite(repository)::withHandler,
                    new LastCandlesticksSQLite(repository)::withHandler);
        }
    }

    // register new service here
    record Services(@NotNull Discord discord,
                    @NotNull MatchingService matchingService,
                    @NotNull AlertsWatcher alertsWatcher,
                    @NotNull Function<TransactionalContext, LastCandlesticksService> lastCandlesticksService) {
        @NotNull
        static Services load(@NotNull Context context, @NotNull Function<Context, Discord> discordLoader) {
            LogManager.getLogger(Services.class).info("Loading services Discord, MatchingService, AlertsWatcher, LastCandlesticksService");
            return new Services(requireNonNull(discordLoader.apply(context)),
                    new MatchingService(context),
                    new AlertsWatcher(context),
                    LastCandlesticksService::new);
        }
    }

    record Parameters(@Nullable String databaseUrl, @NotNull String discordTokenFile, int checkPeriodMin, int hourlySyncDeltaMin) {
        public static Parameters of(@Nullable String databaseUrl, @NotNull String discordTokenFile, int checkPeriodMin, int hourlySyncDeltaMin) {
            return new Parameters(databaseUrl, requireNonNull(discordTokenFile), checkPeriodMin, hourlySyncDeltaMin);
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
    default AlertsWatcher alertsWatcher() {
        return services().alertsWatcher();
    }

    default void transaction(@NotNull Consumer<TransactionalContext> transactionalContextConsumer) {
        transaction(transactionalContextConsumer, DEFAULT_ISOLATION_LEVEL);
    }

    default void transaction(@NotNull Consumer<TransactionalContext> transactionalContextConsumer, @NotNull TransactionIsolationLevel transactionIsolationLevel) {
        requireNonNull(transactionalContextConsumer);
        transactional(v -> { transactionalContextConsumer.accept(v); return null; }, transactionIsolationLevel);
    }

    default <T> T transactional(@NotNull Function<TransactionalContext, T> transactionalContextConsumer) {
        return transactional(transactionalContextConsumer, DEFAULT_ISOLATION_LEVEL);
    }

    default <T> T transactional(@NotNull Function<TransactionalContext, T> transactionalContextConsumer, @NotNull TransactionIsolationLevel transactionIsolationLevel) {
        return TransactionalContext.run(this, transactionalContextConsumer, transactionIsolationLevel);
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
