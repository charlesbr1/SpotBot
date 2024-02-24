package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.LastCandlesticksDaoTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class LastCandlesticksSQLiteTest extends LastCandlesticksDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(loadTransactionalDao(LastCandlesticksSQLite::setupTable, LastCandlesticksSQLite::new, List.of(LastCandlesticksSQLite::new))));
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new LastCandlesticksSQLite(null));
        assertThrows(NullPointerException.class, () -> new LastCandlesticksSQLite(mock(), null));
        assertThrows(NullPointerException.class, () -> new LastCandlesticksSQLite(null, mock()));
        assertThrows(NullPointerException.class, () -> new LastCandlesticksSQLite(mock()).withHandler(null));
        assertDoesNotThrow(() -> new LastCandlesticksSQLite(mock()).withHandler(mock()));
    }
}