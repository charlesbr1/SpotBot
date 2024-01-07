package org.sbot.services.dao;

import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_COMMITTED;

public interface TransactionalCtx {

    TransactionIsolationLevel DEFAULT_ISOLATION_LEVEL = READ_COMMITTED;

    default void transactional(@NotNull Runnable callback) {
        transactional(callback, DEFAULT_ISOLATION_LEVEL);
    }

    default void transactional(@NotNull Runnable callback, @NotNull TransactionIsolationLevel isolationLevel) {
        transactional(() -> {
            callback.run();
            return null;
        }, isolationLevel);
    }

    default <T> T transactional(@NotNull Supplier<T> callback) {
        return transactional(callback, DEFAULT_ISOLATION_LEVEL);
    }

    <T> T transactional(@NotNull Supplier<T> callback, @NotNull TransactionIsolationLevel isolationLevel);
}
