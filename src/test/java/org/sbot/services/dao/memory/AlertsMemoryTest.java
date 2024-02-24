package org.sbot.services.dao.memory;

import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.AlertsDaoTest;

import java.util.stream.Stream;

class AlertsMemoryTest extends AlertsDaoTest {

    public static Stream<Arguments> provideDao() {
        var alerts = new AlertsMemory();
        return Stream.of(Arguments.of(alerts, alerts.usersDao));
    }
}