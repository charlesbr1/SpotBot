package org.sbot.services.dao.sql.jdbi;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JDBITransactionHandlerTest {

    @Test
    void constructor() {
        assertThrows(NullPointerException.class, () -> new JDBITransactionHandler(null));
        assertEquals(SERIALIZABLE, new JDBITransactionHandler(SERIALIZABLE).transactionIsolationLevel);
    }

    private static Handle getHandle(@NotNull JDBITransactionHandler transactionHandler) {
        try {
            var field = JDBITransactionHandler.class.getDeclaredField("handle");
            field.setAccessible(true);
            return (Handle) field.get(transactionHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ReentrantLock getSpyLock(@NotNull JDBITransactionHandler transactionHandler) {
        try {
            var field = JDBITransactionHandler.class.getDeclaredField("lock");
            field.setAccessible(true);
            var lock = spy((ReentrantLock) field.get(transactionHandler));
            field.set(transactionHandler, lock);
            return lock;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sync() {
        // test sync(@NotNull Jdbi jdbi, @NotNull Function<Handle, T> synchronizedAccess)
        assertThrows(NullPointerException.class, () -> new JDBITransactionHandler(SERIALIZABLE).sync(null, h -> null));
        assertThrows(NullPointerException.class, () -> new JDBITransactionHandler(SERIALIZABLE).sync(mock(Jdbi.class), null));

        var transactionHandler = new JDBITransactionHandler(SERIALIZABLE);
        var jdbi = mock(Jdbi.class);
        var handle = mock(Handle.class);
        when(jdbi.open()).thenReturn(handle);
        when(handle.begin()).thenReturn(handle);

        assertEquals(1L, transactionHandler.<Long>sync(jdbi, h -> 1L));
        verify(jdbi).open();
        verify(handle).begin();
        verify(handle).setTransactionIsolationLevel(eq(SERIALIZABLE));
        // test Handle getHandle(@NotNull Jdbi jdbi)
        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        assertEquals("test", transactionHandler.sync(jdbi, h -> "test"));
        verify(jdbi).open();
        verify(handle).begin();
        verify(handle).setTransactionIsolationLevel(eq(SERIALIZABLE));

        // test sub call to sync(@NotNull Supplier<T> synchronizedAccess)
        transactionHandler = new JDBITransactionHandler(REPEATABLE_READ);
        jdbi = mock(Jdbi.class);
        handle = mock(Handle.class);
        when(jdbi.open()).thenReturn(handle);
        when(handle.begin()).thenReturn(handle);
        ReentrantLock lock = getSpyLock(transactionHandler);

        assertEquals("test", transactionHandler.sync(jdbi, h -> "test"));
        verify(jdbi).open();
        verify(handle).begin();
        verify(handle).setTransactionIsolationLevel(eq(REPEATABLE_READ));
        verify(lock).lock();
        verify(lock).unlock();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        verify(jdbi).open();
        verify(handle).begin();
        verify(handle).setTransactionIsolationLevel(eq(REPEATABLE_READ));
        verify(lock, times(2)).lock();
        verify(lock, times(2)).unlock();

        // lock should be released on any exception
        var finalTxHandler = transactionHandler;
        var finalJdbi = jdbi;
        assertThrows(Error.class, () -> finalTxHandler.sync(finalJdbi, h -> { throw new Error(); }));
        verify(finalJdbi).open();
        verify(handle).begin();
        verify(handle).setTransactionIsolationLevel(eq(REPEATABLE_READ));
        verify(lock, times(3)).lock();
        verify(lock, times(3)).unlock();

        // Thread test
        AtomicLong counter = new AtomicLong(0);
        int innerLoopSize = Short.MAX_VALUE;
        try(var executor = Executors.newWorkStealingPool()) {
            var tasks = new ArrayList<Callable<Long>>();
            for (int i = innerLoopSize; i-- != 0; ) {
                tasks.add(() -> { // avoid addAndGet for this test to sync fails
                    var count = counter.get();
                    counter.set(count+1);
                    return count+1;
                });
            }
            executor.invokeAll(tasks);
            assertNotEquals(innerLoopSize, counter.get());
            System.out.println("counter : " + counter.get());

            tasks.clear();
            counter.set(0L);
            var txHandler = new JDBITransactionHandler(REPEATABLE_READ);
            for (int i = innerLoopSize; i-- != 0; ) {
                tasks.add(() -> txHandler.sync(finalJdbi, h -> {
                    var count = counter.get();
                    counter.set(count+1);
                    return count+1;
                }));
            }
            executor.invokeAll(tasks);
            assertEquals(innerLoopSize, counter.get());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void commit() {
        Jdbi jdbi = mock(Jdbi.class);
        Handle handle = mock(Handle.class);
        when(jdbi.open()).thenReturn(handle);
        when(handle.begin()).thenReturn(handle);

        JDBITransactionHandler transactionHandler = new JDBITransactionHandler(READ_COMMITTED);
        ReentrantLock lock = getSpyLock(transactionHandler);

        assertNull(getHandle(transactionHandler));
        transactionHandler.commit();
        verify(lock, never()).lock();
        verify(lock, never()).unlock();
        verify(handle, never()).commit();
        verify(handle, never()).rollback();
        verify(handle, never()).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        assertNotNull(getHandle(transactionHandler));
        verify(lock).lock();
        verify(lock).unlock();
        verify(handle, never()).commit();
        verify(handle, never()).rollback();
        verify(handle, never()).close();

        transactionHandler.commit();
        assertNull(getHandle(transactionHandler));
        verify(lock, times(2)).lock();
        verify(lock, times(2)).unlock();
        verify(handle).commit();
        verify(handle, never()).rollback();
        verify(handle).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        assertNotNull(getHandle(transactionHandler));
        verify(lock, times(3)).lock();
        verify(lock, times(3)).unlock();
        verify(handle).commit();
        verify(handle, never()).rollback();
        verify(handle).close();

        transactionHandler.commit();
        assertNull(getHandle(transactionHandler));
        verify(lock, times(4)).lock();
        verify(lock, times(4)).unlock();
        verify(handle, times(2)).commit();
        verify(handle, never()).rollback();
        verify(handle, times(2)).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        when(handle.commit()).thenThrow(IllegalStateException.class).thenReturn(handle);
        assertThrows(IllegalStateException.class, transactionHandler::commit);
        verify(lock, times(6)).lock();
        verify(lock, times(6)).unlock();
        verify(handle, times(3)).commit();
        verify(handle, times(3)).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        assertNotNull(getHandle(transactionHandler));
        transactionHandler.commit();
        verify(lock, times(8)).lock();
        verify(lock, times(8)).unlock();
        verify(handle, times(4)).commit();
        verify(handle, times(4)).close();
    }

    @Test
    void rollback() {
        Jdbi jdbi = mock(Jdbi.class);
        Handle handle = mock(Handle.class);
        when(jdbi.open()).thenReturn(handle);
        when(handle.begin()).thenReturn(handle);

        JDBITransactionHandler transactionHandler = new JDBITransactionHandler(READ_COMMITTED);
        ReentrantLock lock = getSpyLock(transactionHandler);

        assertNull(getHandle(transactionHandler));
        transactionHandler.rollback();
        verify(lock, never()).lock();
        verify(lock, never()).unlock();
        verify(handle, never()).rollback();
        verify(handle, never()).commit();
        verify(handle, never()).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        assertNotNull(getHandle(transactionHandler));
        verify(lock).lock();
        verify(lock).unlock();
        verify(handle, never()).rollback();
        verify(handle, never()).commit();
        verify(handle, never()).close();

        transactionHandler.rollback();
        assertNull(getHandle(transactionHandler));
        verify(lock, times(2)).lock();
        verify(lock, times(2)).unlock();
        verify(handle).rollback();
        verify(handle, never()).commit();
        verify(handle).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        assertNotNull(getHandle(transactionHandler));
        verify(lock, times(3)).lock();
        verify(lock, times(3)).unlock();
        verify(handle).rollback();
        verify(handle, never()).commit();
        verify(handle).close();

        transactionHandler.rollback();
        assertNull(getHandle(transactionHandler));
        verify(lock, times(4)).lock();
        verify(lock, times(4)).unlock();
        verify(handle, times(2)).rollback();
        verify(handle, never()).commit();
        verify(handle, times(2)).close();

        assertEquals(handle, transactionHandler.sync(jdbi, Objects::requireNonNull));
        when(handle.rollback()).thenThrow(new NullPointerException());
        assertThrows(NullPointerException.class, transactionHandler::rollback);
        verify(lock, times(6)).lock();
        verify(lock, times(6)).unlock();
        verify(handle, times(3)).rollback();
        verify(handle, never()).commit();
        verify(handle, times(3)).close();
    }
}