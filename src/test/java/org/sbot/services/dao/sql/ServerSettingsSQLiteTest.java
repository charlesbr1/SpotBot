package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.ServerSettingsDaoTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ServerSettingsSQLiteTest extends ServerSettingsDaoTest {

    public static Stream<Arguments> provideDao() {
        return provideAlertWithSettingsDao().map(arguments -> Arguments.of(arguments.get()[2]));
    }

    public static Stream<Arguments> provideAlertWithSettingsDao() {
        return AlertsSQLiteTest.provideDao(ServerSettingsSQLite::new);
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new ServerSettingsSQLite(null));
        assertThrows(NullPointerException.class, () -> new ServerSettingsSQLite(mock(), null));
        assertThrows(NullPointerException.class, () -> new ServerSettingsSQLite(null, mock()));
        assertThrows(NullPointerException.class, () -> new ServerSettingsSQLite(mock()).withHandler(null));
        assertDoesNotThrow(() -> new ServerSettingsSQLite(mock()).withHandler(mock()));
    }
}