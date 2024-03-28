package org.sbot.services.dao.memory;

import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.ServerSettingsDaoTest;

import java.util.stream.Stream;

class ServerSettingsMemoryTest extends ServerSettingsDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(new ServerSettingsMemory(new AlertsMemory())));
    }

    public static Stream<Arguments> provideAlertWithSettingsDao() {
        var alerts = new AlertsMemory();
        return Stream.of(Arguments.of(alerts, alerts.userSettingsDao, new ServerSettingsMemory(alerts)));
    }
}