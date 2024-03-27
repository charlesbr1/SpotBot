package org.sbot.services.dao.sql.jdbi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

// thread safe transaction handler
public class JDBITransactionHandler {

    private static final Logger LOGGER = LogManager.getLogger(JDBITransactionHandler.class);

    public final TransactionIsolationLevel transactionIsolationLevel;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Handle handle;

    static final String ENABLE_FOREIGN_KEY_CONSTRAINT = "PRAGMA foreign_keys = ON";

    public JDBITransactionHandler(@NotNull TransactionIsolationLevel transactionIsolationLevel) {
        this.transactionIsolationLevel = requireNonNull(transactionIsolationLevel);
    }

    // all tx usage should be done by one thread at a time
    <T> T sync(@NotNull Jdbi jdbi, @NotNull Function<Handle, T> synchronizedAccess) {
        requireNonNull(jdbi); requireNonNull(synchronizedAccess);
        return sync(() -> synchronizedAccess.apply(getHandle(jdbi)));
    }

    private <T> T sync(@NotNull Supplier<T> synchronizedAccess) {
        lock.lock();
        long start = System.nanoTime();
        try {
            return synchronizedAccess.get();
        } finally {
            LOGGER.info("inner tx done in {} μs.", (System.nanoTime() - start) / 1000);
            lock.unlock();
        }
    }

    @NotNull
    private Handle getHandle(@NotNull Jdbi jdbi) {
        if(null == handle) {
            LOGGER.debug("new tx handler");
            // setup a new tx handler
            handle = jdbi.open();
            handle.setTransactionIsolationLevel(transactionIsolationLevel);
            handle.execute(ENABLE_FOREIGN_KEY_CONSTRAINT);
            handle.begin();
        }
        return handle;
    }

    private Optional<Handle> clearHandle() {
        return sync(() -> {
            var previous = this.handle;
            this.handle = null;
            return Optional.ofNullable(previous);
        });
    }

    public void commit() {
        LOGGER.debug("commit tx");
        if(null != handle) {
            clearHandle().ifPresent(tx -> {
                try (tx) { // this finally call handle close()
                    tx.commit();
                    LOGGER.debug("tx successfully committed");
                }
            });
        }
    }

    public void rollback() {
        LOGGER.debug("rollback tx");
        if(null != handle) {
            clearHandle().ifPresent(tx -> {
                try (tx) { // this finally  call handle close()
                    tx.rollback();
                    LOGGER.debug("tx successfully rollback");
                }
            });
        }
    }
}
