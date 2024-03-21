package org.sbot.services.context;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.SERIALIZABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreadSafeTxContextTest {

    @Test
    void constructor() {
        Context context = mock();
        Context.DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> mock());
        assertDoesNotThrow(() -> new ThreadSafeTxContext(context, SERIALIZABLE, 1));
        assertThrows(NullPointerException.class, () -> new ThreadSafeTxContext(null, SERIALIZABLE, 1));
        assertThrows(NullPointerException.class, () -> new ThreadSafeTxContext(context, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new ThreadSafeTxContext(context, SERIALIZABLE, -1));
    }

    @Test
    void commit() {
        Context context = mock();
        Context.DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> mock());

        assertThrows(IllegalArgumentException.class, () -> new ThreadSafeTxContext(context, SERIALIZABLE, 1).commit(0));
        assertThrows(IllegalArgumentException.class, () -> new ThreadSafeTxContext(context, SERIALIZABLE, 1).commit(-1));

        // test single commit
        var txCtx = new ThreadSafeTxContext(context, SERIALIZABLE, 1);
        var count = new AtomicInteger(0);
        Runnable afterCommit = count::incrementAndGet;

        txCtx.afterCommit(afterCommit);
        assertEquals(0, count.get());
        txCtx.commit();
        assertEquals(1, count.get());
        assertThrows(IllegalStateException.class, txCtx::commit); // no more commit expected

        // test many commit
        txCtx = new ThreadSafeTxContext(context, SERIALIZABLE, 7);
        txCtx.afterCommit(afterCommit);
        count.set(0);
        txCtx.commit(); // 1 commit
        assertEquals(0, count.get());
        txCtx.commit(3); // 4 commits
        assertEquals(0, count.get());
        txCtx.commit(); // 5 commits
        assertEquals(0, count.get());
        txCtx.commit(2); // 7 commits
        assertEquals(1, count.get());
        assertThrows(IllegalStateException.class, txCtx::commit); // 8 commits

        var txCtx2 = new ThreadSafeTxContext(context, SERIALIZABLE, 7);
        txCtx2.afterCommit(afterCommit);
        txCtx2.afterCommit(afterCommit);
        txCtx2.afterCommit(afterCommit);
        count.set(0);
        txCtx2.commit(); // 1 commit
        assertEquals(0, count.get());
        txCtx2.commit(5); // 6 commits
        assertEquals(0, count.get());
        assertThrows(IllegalStateException.class, () -> txCtx2.commit(2)); // 8 commits

        var txCtx3 = new ThreadSafeTxContext(context, SERIALIZABLE, 7);
        txCtx3.afterCommit(afterCommit);
        count.set(0);
        txCtx3.commit(); // 1 commit
        assertEquals(0, count.get());
        txCtx3.commit(6); // 7 commits
        assertEquals(1, count.get());
        assertThrows(IllegalStateException.class, txCtx3::commit); // 8 commits
    }

    @Test
    void rollback() {
        Context context = mock();
        Context.DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> mock());
        var txCtx = new ThreadSafeTxContext(context, SERIALIZABLE, 1);
        assertThrows(UnsupportedOperationException.class, txCtx::rollback);
    }

    @Test
    void afterCommit() {
        Context context = mock();
        Context.DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> mock());

        assertThrows(NullPointerException.class, () -> new ThreadSafeTxContext(context, SERIALIZABLE, 1).afterCommit(null));

        var txCtx = new ThreadSafeTxContext(context, SERIALIZABLE, 1);
        var count = new AtomicInteger(0);

        Runnable afterCommit = count::incrementAndGet;
        txCtx.afterCommit(afterCommit);
        txCtx.afterCommit(afterCommit);
        txCtx.afterCommit(afterCommit);
        assertEquals(0, count.get());
        txCtx.commit();
        assertEquals(1, count.get());
    }
}