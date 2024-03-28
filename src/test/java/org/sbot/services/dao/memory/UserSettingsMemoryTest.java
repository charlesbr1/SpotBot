package org.sbot.services.dao.memory;

import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.UserSettingsDaoTest;

import java.util.stream.Stream;

class UserSettingsMemoryTest extends UserSettingsDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(new AlertsMemory().userSettingsDao));
    }

    public static Stream<Arguments> provideAlertWithSettingsDao() {
        var alerts = new AlertsMemory();
        return Stream.of(Arguments.of(alerts, alerts.userSettingsDao, new ServerSettingsMemory(alerts)));
    }
}