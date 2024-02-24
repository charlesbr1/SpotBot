package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.AlertsDaoTest;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.fakeJdbi;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class AlertsSQLiteTest extends AlertsDaoTest {

    public static Stream<Arguments> provideDao() {
        return provideDao(null);
    }

    public static Stream<Arguments> provideDao(Consumer<JDBIRepository> usersDaoConstructor) {
        var initConstructors = new ArrayList<Consumer<JDBIRepository>>();
        Optional.ofNullable(usersDaoConstructor).ifPresent(initConstructors::add);
        initConstructors.add(AlertsSQLite::new);
        UsersSQLite[] userDao = new UsersSQLite[1];
        return Stream.of(Arguments.of(loadTransactionalDao((dao, handle) -> {
            try {
                var field = AbstractJDBI.class.getDeclaredField("transactionHandler");
                field.setAccessible(true);
                JDBITransactionHandler txHandler = (JDBITransactionHandler) field.get(dao);
                userDao[0] = new UsersSQLite(dao, txHandler);
                userDao[0].setupTable(handle);
                handle.execute(UsersSQLite.SQL.CREATE_TABLE);
                dao.setupTable(handle);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        },(jdbi, txHandler) -> new AlertsSQLite(jdbi, txHandler, new AtomicLong(1)), initConstructors), userDao[0]));
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(null));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(null, mock(), mock()));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(mock(), null, null));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(mock(), mock(), null));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(fakeJdbi(mock()), mock(), mock()).withHandler(null));
        assertDoesNotThrow(() -> new AlertsSQLite(fakeJdbi(mock()), mock(), mock()).withHandler(mock()));
    }
}