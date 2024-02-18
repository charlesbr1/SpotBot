package org.sbot.services.context;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.sbot.services.discord.Discord;
import org.sbot.services.context.Context.Parameters;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.sql.AlertsSQLite;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.time.Clock;
import java.time.ZoneId;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.SERIALIZABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
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
    void alertsWatcher() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertNotNull(context.alertsWatcher());
    }

    @Test
    void transaction() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertThrows(NullPointerException.class, () -> context.transaction(null));
        assertThrows(NullPointerException.class, () -> context.transaction(null, DEFAULT_ISOLATION_LEVEL));

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
        }, SERIALIZABLE);
        assertTrue(run[0]);
    }

    @Test
    void transactional() {
        Parameters parameters = Parameters.of(null, "discordTokenFile", 1, 1);
        var context = Context.of(Clock.systemUTC(), parameters, null, ctx -> mock(Discord.class));
        assertThrows(NullPointerException.class, () -> context.transactional(null));
        assertThrows(NullPointerException.class, () -> context.transactional(null, DEFAULT_ISOLATION_LEVEL));

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
        }, SERIALIZABLE));
        assertTrue(run[0]);
    }
}