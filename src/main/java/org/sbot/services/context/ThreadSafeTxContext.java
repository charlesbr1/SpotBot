package org.sbot.services.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.services.dao.NotificationsDao;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;

public final class ThreadSafeTxContext extends TransactionalContext {

    private static final Logger LOGGER = LogManager.getLogger(ThreadSafeTxContext.class);

    public final NotificationsDao notificationsDao;
    private final AtomicInteger countdown;
    private final Set<Runnable> afterCommit = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ThreadSafeTxContext(@NotNull Context context, @NotNull TransactionIsolationLevel isolationLevel, int countdown) {
        super(context, isolationLevel);
        notificationsDao = notificationsDao();
        this.countdown = new AtomicInteger(requireStrictlyPositive(countdown));
        //TODO remove
        Thread.ofVirtual().name("debug check").start(() -> {
            LockSupport.parkNanos(Duration.ofMinutes(5L).toNanos());
            if(ThreadSafeTxContext.this.countdown.get() != 0) {
                LOGGER.error("COUNTDOWN FAILED ! {}", ThreadSafeTxContext.this.countdown.get());
                Thread.ofVirtual().start(() -> { LockSupport.parkNanos(1000_000_000L);System.exit(3);});
                throw new Error("COUNTDOWN FAILED");
            } else
                LOGGER.info("COUNTDOWN OK");
        });
    }

    @Override
    public void commit() {
        commit(1);
    }

    @Override
    public void rollback() {
        throw new UnsupportedOperationException();
    }

    public void commit(int count) {
        LOGGER.debug("committing {} transaction", count);
        if (countdown.addAndGet(-requireStrictlyPositive(count)) == 0) {
            LOGGER.debug("countdown reached, committing whole transaction");
            try {
                super.commit();
            } finally {
                if(!afterCommit.isEmpty()) {
                    LOGGER.debug("after commit");
                    afterCommit.forEach(Runnable::run);
                    afterCommit.clear();
                }
            }
        } else if (countdown.get() < 0) {
            LOGGER.error("Unexpected negative countdown value {}", countdown.get());
            throw new IllegalStateException("Unexpected negative counter value " + countdown.get());
        }
    }

    public void afterCommit(@NotNull Runnable runnable) {
        afterCommit.add(runnable); // this retains only 1 instance of same runnable
    }
}
