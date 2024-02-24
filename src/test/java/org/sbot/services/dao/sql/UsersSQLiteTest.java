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
        return AlertsSQLiteTest.provideDao(UsersSQLite::new).map(arguments -> Arguments.of(arguments.get()[1]));
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