package org.sbot.services.context;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.context.Context.Services;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.util.function.Function;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.services.context.TransactionalContext.DEFAULT_ISOLATION_LEVEL;

class TransactionalContextTest {

    private static void setTransactionHandler(@NotNull TransactionalContext transactionalContext, JDBITransactionHandler transactionHandler) {
        try {
            var field = transactionalContext.getClass().getDeclaredField("transactionHandler");
            field.setAccessible(true);
            field.set(transactionalContext, transactionHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void constructor() {
        assertThrows(NullPointerException.class, () -> new TransactionalContext(mock(Context.class), null));
        assertThrows(NullPointerException.class, () -> new TransactionalContext(null, DEFAULT_ISOLATION_LEVEL));
        assertDoesNotThrow(() -> new TransactionalContext(mock(Context.class), DEFAULT_ISOLATION_LEVEL));
    }

    @Test
    void transactionIsolationLevel() {
        var txCtx = new TransactionalContext(mock(Context.class), DEFAULT_ISOLATION_LEVEL);
        assertEquals(DEFAULT_ISOLATION_LEVEL, txCtx.transactionIsolationLevel());
        txCtx = new TransactionalContext(mock(Context.class), SERIALIZABLE);
        assertEquals(SERIALIZABLE, txCtx.transactionIsolationLevel());
    }

    @Test
    void commit() {
        TransactionalContext txContext = new TransactionalContext(mock(Context.class), READ_COMMITTED);
        var txHandler = mock(JDBITransactionHandler.class);
        setTransactionHandler(txContext, txHandler);
        txContext.commit();
        verify(txHandler).commit();
    }

    @Test
    void rollback() {
        TransactionalContext txContext = new TransactionalContext(mock(Context.class), READ_COMMITTED);
        var txHandler = mock(JDBITransactionHandler.class);
        setTransactionHandler(txContext, txHandler);
        txContext.rollback();
        verify(txHandler).rollback();
    }

    @Test
    void userSettingsDao() {
        var context = mock(Context.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        var settingsDaoBuilder = mock(Function.class);
        when(dataServices.userSettingsDao()).thenReturn(settingsDaoBuilder);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.userSettingsDao();
        verify(context).dataServices();
        verify(dataServices).userSettingsDao();
        verify(settingsDaoBuilder).apply(any());
    }

    @Test
    void serverSettingsDao() {
        var context = mock(Context.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        var settingsDaoBuilder = mock(Function.class);
        when(dataServices.serverSettingsDao()).thenReturn(settingsDaoBuilder);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.serverSettingsDao();
        verify(context).dataServices();
        verify(dataServices).serverSettingsDao();
        verify(settingsDaoBuilder).apply(any());
    }

    @Test
    void alertsDao() {
        var context = mock(Context.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        var alertsDaoBuilder = mock(Function.class);
        when(dataServices.alertsDao()).thenReturn(alertsDaoBuilder);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.alertsDao();
        verify(context).dataServices();
        verify(dataServices).alertsDao();
        verify(alertsDaoBuilder).apply(any());
    }

    @Test
    void notificationsDao() {
        var context = mock(Context.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        var notificationsDaoBuilder = mock(Function.class);
        when(dataServices.notificationsDao()).thenReturn(notificationsDaoBuilder);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.notificationsDao();
        verify(context).dataServices();
        verify(dataServices).notificationsDao();
        verify(notificationsDaoBuilder).apply(any());
    }

    @Test
    void lastCandlesticksDao() {
        var context = mock(Context.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        var lastCandlesticksDaoBuilder = mock(Function.class);
        when(dataServices.lastCandlesticksDao()).thenReturn(lastCandlesticksDaoBuilder);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.lastCandlesticksDao();
        verify(context).dataServices();
        verify(dataServices).lastCandlesticksDao();
        verify(lastCandlesticksDaoBuilder).apply(any());
    }

    @Test
    void lastCandlesticksService() {
        var context = mock(Context.class);
        var services = mock(Services.class);
        when(context.services()).thenReturn(services);
        var lastCandlesticksServiceBuilder = mock(Function.class);
        when(services.lastCandlesticksService()).thenReturn(lastCandlesticksServiceBuilder);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.lastCandlesticksService();
        verify(context).services();
        verify(services).lastCandlesticksService();
        verify(lastCandlesticksServiceBuilder).apply(eq(txContext));
    }

    @Test
    void clock() {
        var context = mock(Context.class);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.clock();
        verify(context).clock();
    }

    @Test
    void dataServices() {
        var context = mock(Context.class);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.dataServices();
        verify(context).dataServices();
    }

    @Test
    void services() {
        var context = mock(Context.class);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.services();
        verify(context).services();
    }

    @Test
    void exchanges() {
        var context = mock(Context.class);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.exchanges();
        verify(context).exchanges();
    }

    @Test
    void parameters() {
        var context = mock(Context.class);
        TransactionalContext txContext = new TransactionalContext(context, READ_COMMITTED);
        txContext.parameters();
        verify(context).parameters();
    }

    @Test
    void run() {
        // test run(Context, Consumer, Isolation)
        assertThrows(NullPointerException.class, () -> TransactionalContext.run(null, txCtx -> null, DEFAULT_ISOLATION_LEVEL, false));
        assertThrows(NullPointerException.class, () -> TransactionalContext.run(mock(Context.class), null, DEFAULT_ISOLATION_LEVEL, false));
        assertThrows(NullPointerException.class, () -> TransactionalContext.run(mock(Context.class), txCtx -> null, null, false));

        var txContext = spy(new TransactionalContext(mock(Context.class), READ_COMMITTED));
        Function<TransactionalContext, Long> transactionalContextConsumer = mock(Function.class);
        assertThrows(IllegalArgumentException.class, () -> TransactionalContext.run(txContext, transactionalContextConsumer, REPEATABLE_READ, false));
        assertThrows(IllegalArgumentException.class, () -> TransactionalContext.run(txContext, transactionalContextConsumer, SERIALIZABLE, false));
        verify(transactionalContextConsumer, never()).apply(any());
        assertNull(TransactionalContext.run(txContext, transactionalContextConsumer, SERIALIZABLE, true));
        verify(transactionalContextConsumer).apply(any());
        assertNull(TransactionalContext.<Long>run(txContext, transactionalContextConsumer, READ_COMMITTED, false));
        verify(transactionalContextConsumer, times(2)).apply(any());

        assertEquals(123L, TransactionalContext.<Long>run(txContext, ctx -> 123L, READ_COMMITTED, false));
        assertEquals(123L, TransactionalContext.<Long>run(txContext, ctx -> 123L, READ_UNCOMMITTED, false));
        assertEquals(123L, TransactionalContext.<Long>run(txContext, ctx -> 123L, NONE, false));
        assertEquals(123L, TransactionalContext.<Long>run(txContext, ctx -> 123L, UNKNOWN, false));
        verify(txContext, never()).commit();
        verify(txContext, never()).rollback();

        var context = mock(Context.class);
        assertEquals(123L, TransactionalContext.<Long>run(context, ctx -> 123L, UNKNOWN, false));
        assertEquals(123L, TransactionalContext.<Long>run(context, ctx -> 123L, NONE, false));
        assertEquals(123L, TransactionalContext.<Long>run(context, ctx -> 123L, READ_COMMITTED, false));
        assertEquals(123L, TransactionalContext.<Long>run(context, ctx -> 123L, SERIALIZABLE, false));
        assertNull(TransactionalContext.run(context, TransactionalContext::services, SERIALIZABLE, false));
        verify(context).services();
    }

    @Test
    void newTransaction() {
        // test newTransaction(TransactionalContext, Consumer)
        var txContext = spy(new TransactionalContext(mock(Context.class), READ_COMMITTED));
        assertThrows(NullPointerException.class, () -> TransactionalContext.newTransaction(null, txCtx -> null));
        assertThrows(NullPointerException.class, () -> TransactionalContext.newTransaction(mock(TransactionalContext.class), null));

        assertEquals(123L, TransactionalContext.<Long>newTransaction(txContext, ctx -> 123L));
        verify(txContext).commit();
        verify(txContext, never()).rollback();
        assertThrows(IllegalStateException.class, () -> TransactionalContext.newTransaction(txContext, ctx -> { throw new IllegalStateException(); }));
        verify(txContext).commit();
        verify(txContext).rollback();
        assertEquals(321L, TransactionalContext.<Long>newTransaction(txContext, ctx -> 321L));
        verify(txContext, times(2)).commit();
        verify(txContext).rollback();

        doThrow(new IllegalStateException()).when(txContext).commit();
        assertThrows(IllegalStateException.class, () -> TransactionalContext.newTransaction(txContext, ctx -> 123L));
        verify(txContext, times(3)).commit();
        verify(txContext, times(2)).rollback();
    }
}