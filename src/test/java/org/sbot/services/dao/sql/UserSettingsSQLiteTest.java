package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.UserSettingsDaoTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class UserSettingsSQLiteTest extends UserSettingsDaoTest {

    public static Stream<Arguments> provideDao() {
        return provideAlertWithSettingsDao().map(arguments -> Arguments.of(arguments.get()[1]));
    }

    public static Stream<Arguments> provideAlertWithSettingsDao() {
        return AlertsSQLiteTest.provideDao(UserSettingsSQLite::new);
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new UserSettingsSQLite(null));
        assertThrows(NullPointerException.class, () -> new UserSettingsSQLite(mock(), null));
        assertThrows(NullPointerException.class, () -> new UserSettingsSQLite(null, mock()));
        assertThrows(NullPointerException.class, () -> new UserSettingsSQLite(mock()).withHandler(null));
        assertDoesNotThrow(() -> new UserSettingsSQLite(mock()).withHandler(mock()));
    }
}