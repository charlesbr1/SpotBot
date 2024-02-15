package org.sbot.services.context;

import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.LastCandlesticksService;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.time.Clock;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_COMMITTED;

public final class TransactionalContext implements Context {

    public static final TransactionIsolationLevel DEFAULT_ISOLATION_LEVEL = READ_COMMITTED;

    private final JDBITransactionHandler transactionHandler;

    private final Context context;

    public TransactionalContext(@NotNull Context context, @NotNull TransactionIsolationLevel transactionIsolationLevel) {
        this.context = requireNonNull(context);
        this.transactionHandler = new JDBITransactionHandler(transactionIsolationLevel);
    }

    @NotNull
    public TransactionIsolationLevel transactionIsolationLevel() {
        return transactionHandler.transactionIsolationLevel;
    }

    public void commit() {
        transactionHandler.commit();
    }

    public void rollback() {
        transactionHandler.rollback();
    }

    @NotNull
    public AlertsDao alertsDao() {
        return dataServices().alertsDao().apply(transactionHandler);
    }

    @NotNull
    public LastCandlesticksDao lastCandlesticksDao() {
        return dataServices().lastCandlesticksDao().apply(transactionHandler);
    }

    @NotNull
    public LastCandlesticksService lastCandlesticksService() {
        return services().lastCandlesticksService().apply(this);
    }

    @NotNull
    @Override
    public Clock clock() { return context.clock(); }
    @NotNull
    @Override
    public DataServices dataServices() { return context.dataServices(); }
    @NotNull
    @Override
    public Services services() { return context.services(); }
    @NotNull
    @Override
    public Parameters parameters() { return context.parameters(); }


    static <T> T run(@NotNull Context context, @NotNull Function<TransactionalContext, T> transactionalContextConsumer, @NotNull TransactionIsolationLevel transactionIsolationLevel) {
        if(context instanceof TransactionalContext txCtx) {
            if(transactionIsolationLevel.intValue() > txCtx.transactionHandler.transactionIsolationLevel.intValue()) {
                throw new IllegalArgumentException("Cannot handle inner transaction with higher isolation level, actual : " + txCtx.transactionHandler.transactionIsolationLevel + ", required : " + transactionIsolationLevel);
            }
            return transactionalContextConsumer.apply(txCtx);
        }
        return newTransaction(new TransactionalContext(context, transactionIsolationLevel), transactionalContextConsumer);

    }
    static <T> T newTransaction(@NotNull TransactionalContext context, @NotNull Function<TransactionalContext, T> transactionalContextConsumer) {
        try {
            var result = transactionalContextConsumer.apply(context);
            context.commit(); // won't be call if an exception is thrown
            return result;
        } catch (Throwable e) {
            context.rollback();
            throw e;
        }
    }
}
