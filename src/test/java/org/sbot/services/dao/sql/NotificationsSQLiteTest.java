package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.NotificationsDaoTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class NotificationsSQLiteTest extends NotificationsDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(loadTransactionalDao(NotificationsSQLite::setupTable, NotificationsSQLite::new, List.of(NotificationsSQLite::new))));
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new NotificationsSQLite(null));
        assertThrows(NullPointerException.class, () -> new NotificationsSQLite(mock(), null));
        assertThrows(NullPointerException.class, () -> new NotificationsSQLite(null, mock()));
        assertThrows(NullPointerException.class, () -> new NotificationsSQLite(mock()).withHandler(null));
        assertDoesNotThrow(() -> new NotificationsSQLite(mock()).withHandler(mock()));
    }
}