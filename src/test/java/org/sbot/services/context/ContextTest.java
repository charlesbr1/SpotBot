package org.sbot.services.context;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.sbot.exchanges.Exchanges;
import org.sbot.services.context.Context.Parameters;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.memory.UsersMemory;
import org.sbot.services.dao.sql.AlertsSQLite;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;
import org.sbot.services.dao.sql.UsersSQLite;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;
import org.sbot.services.discord.Discord;

import java.time.Clock;
import java.time.ZoneId;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.SERIALIZABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.services.context.Context.Parameters.MAX_CHECK_PERIOD;
import static org.sbot.services.context.Context.Parameters.MAX_HOURLY_SYNC_DELTA;
import static org.sbot.services.context.TransactionalContext.DEFAULT_ISOLATION_LEVEL;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.SQLITE_MEMORY_PERSISTENT;

class ContextTest {

    @Test
    void clock() {
        Clock clock = Clock.system(ZoneId.of("Europe/Paris"));
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(clock, parameters, null, ctx -> mock(Discord.class));
        assertEquals(clock, context.clock());
        assertThrows(NullPointerException.class, () -> Context.of(null, parameters, null, ctx -> mock(Discord.class)));
    }

    @Test
    void dataServices() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertNotNull(context.dataServices());
        assertNotNull(context.dataServices().usersDao());
        assertInstanceOf(UsersMemory.class, context.dataServices().usersDao().apply(null));
        assertNotNull(context.dataServices().alertsDao());
        assertInstanceOf(AlertsMemory.class, context.dataServices().alertsDao().apply(null));
        assertNotNull(context.dataServices().lastCandlesticksDao());
        assertInstanceOf(LastCandlesticksMemory.class, context.dataServices().lastCandlesticksDao().apply(null));

        Context sqlContext;
        try (var handle = Jdbi.create(SQLITE_MEMORY_PERSISTENT).open()) { // need to maintain a handle open to keep data in memory during init
            parameters = Parameters.of(SQLITE_MEMORY_PERSISTENT, "discordTokenFile", 1, 1);
            sqlContext = Context.of(Clock.systemUTC(), parameters, new JDBIRepository(SQLITE_MEMORY_PERSISTENT), ctx -> mock(Discord.class));
        }
        assertNotNull(sqlContext.dataServices());
        assertNotNull(sqlContext.dataServices().usersDao());
        assertThrows(NullPointerException.class, () -> sqlContext.dataServices().usersDao().apply(null));
        assertInstanceOf(UsersSQLite.class, sqlContext.dataServices().usersDao().apply(mock(JDBITransactionHandler.class)));
        assertNotNull(sqlContext.dataServices().alertsDao());
        assertThrows(NullPointerException.class, () -> sqlContext.dataServices().alertsDao().apply(null));
        assertInstanceOf(AlertsSQLite.class, sqlContext.dataServices().alertsDao().apply(mock(JDBITransactionHandler.class)));
        assertNotNull(sqlContext.dataServices().lastCandlesticksDao());
        assertThrows(NullPointerException.class, () -> sqlContext.dataServices().lastCandlesticksDao().apply(null));
        assertInstanceOf(LastCandlesticksSQLite.class, sqlContext.dataServices().lastCandlesticksDao().apply(mock(JDBITransactionHandler.class)));
    }

    @Test
    void services() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        Discord discord = mock(Discord.class);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> discord);
        assertNotNull(context.services());
        assertNotNull(context.services().lastCandlesticksService());
        assertThrows(NullPointerException.class, () -> context.services().lastCandlesticksService().apply(null));
        assertNotNull(context.services().lastCandlesticksService().apply(mock(TransactionalContext.class)));
        assertNotNull(context.services().matchingService());
        assertNotNull(context.services().alertsWatcher());
        assertEquals(discord, context.services().discord());
    }

    @Test
    void exchanges() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        Discord discord = mock();
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> discord);
        assertInstanceOf(Exchanges.class, context.exchanges());
    }

    @Test
    void parametersOf() {
        assertDoesNotThrow(() -> Parameters.of("url", "token", 1, 1));
        assertDoesNotThrow(() -> Parameters.of(null, "token", 1, 1));
        assertThrows(NullPointerException.class, () -> Parameters.of("url", null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("", "token", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "token", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "token", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "token", 10, 11));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "token", 1, -1));
        assertDoesNotThrow(() -> Parameters.of("url", "token", MAX_CHECK_PERIOD, MAX_HOURLY_SYNC_DELTA));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "token", MAX_CHECK_PERIOD + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Parameters.of("url", "token", MAX_CHECK_PERIOD, MAX_HOURLY_SYNC_DELTA + 1));
    }

    @Test
    void parameters() {
        assertThrows(NullPointerException.class, () -> Parameters.of(null, null, 1, 1));
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertEquals(parameters, context.parameters());
        assertThrows(NullPointerException.class, () -> Context.of(Clock.systemUTC(), null, null, ctx -> mock(Discord.class)));
    }

    @Test
    void discord() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        Discord discord = mock();
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> discord);
        assertEquals(discord, context.discord());
        assertThrows(NullPointerException.class, () -> Context.of(Clock.systemUTC(), parameters, null, ctx -> null));
        assertThrows(NullPointerException.class, () -> Context.of(Clock.systemUTC(), parameters, null, null));
    }

    @Test
    void matchingService() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertNotNull(context.matchingService());
    }

    @Test
    void notificationService() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertNotNull(context.notificationService());
    }

    @Test
    void alertsWatcher() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertNotNull(context.alertsWatcher());
    }

    @Test
    void asThreadSafeTxContext() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertNotNull(context.asThreadSafeTxContext(SERIALIZABLE, 1));
        assertThrows(NullPointerException.class, () -> context.asThreadSafeTxContext(null, 1));
        assertThrows(IllegalArgumentException.class, () -> context.asThreadSafeTxContext(SERIALIZABLE, 0));
        assertThrows(IllegalArgumentException.class, () -> context.asThreadSafeTxContext(SERIALIZABLE, -1));
    }

    @Test
    void transaction() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertThrows(NullPointerException.class, () -> context.transaction(null));
        assertThrows(NullPointerException.class, () -> context.transaction(null, DEFAULT_ISOLATION_LEVEL, false));

        boolean[] run = new boolean[1];
        context.transaction(txCtx -> {
            assertInstanceOf(TransactionalContext.class, txCtx);
            assertEquals(DEFAULT_ISOLATION_LEVEL, txCtx.transactionIsolationLevel());
            run[0] = true;
        });
        assertTrue(run[0]);
        run[0] = false;
        context.transaction(txCtx -> {
            assertInstanceOf(TransactionalContext.class, txCtx);
            assertEquals(SERIALIZABLE, txCtx.transactionIsolationLevel());
            run[0] = true;
        }, SERIALIZABLE, true);
        assertTrue(run[0]);
    }

    @Test
    void transactional() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertThrows(NullPointerException.class, () -> context.transactional(null));
        assertThrows(NullPointerException.class, () -> context.transactional(null, DEFAULT_ISOLATION_LEVEL, false));

        boolean[] run = new boolean[1];
        assertEquals(123L, context.<Long>transactional(txCtx -> {
            assertInstanceOf(TransactionalContext.class, txCtx);
            assertEquals(DEFAULT_ISOLATION_LEVEL, txCtx.transactionIsolationLevel());
            run[0] = true;
            return 123L;
        }));
        assertTrue(run[0]);
        run[0] = false;
        assertEquals(321L, context.<Long>transactional(txCtx -> {
            assertInstanceOf(TransactionalContext.class, txCtx);
            assertEquals(SERIALIZABLE, txCtx.transactionIsolationLevel());
            run[0] = true;
            return 321L;
        }, SERIALIZABLE, true));
        assertTrue(run[0]);
    }
}