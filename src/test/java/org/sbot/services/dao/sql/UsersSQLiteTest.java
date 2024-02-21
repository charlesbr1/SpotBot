package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.UsersDaoTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class UsersSQLiteTest extends UsersDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(loadTransactionalDao(UsersSQLite::setupTable, UsersSQLite::new, UsersSQLite::new)));
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new UsersSQLite(null));
        assertThrows(NullPointerException.class, () -> new UsersSQLite(mock(), null));
        assertThrows(NullPointerException.class, () -> new UsersSQLite(null, mock()));
        assertThrows(NullPointerException.class, () -> new UsersSQLite(mock()).withHandler(null));
        assertDoesNotThrow(() -> new UsersSQLite(mock()).withHandler(mock()));
    }
}