package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.AlertsDaoTest;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.fakeJdbi;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class AlertsSQLiteTest extends AlertsDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(loadTransactionalDao(AlertsSQLite::setupTable, AlertsSQLite::new, (jdbi, txHandler) -> new AlertsSQLite(jdbi, txHandler, new AtomicLong(1)))));
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