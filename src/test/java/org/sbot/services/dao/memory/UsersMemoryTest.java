package org.sbot.services.dao.memory;

import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.UsersDaoTest;

import java.util.stream.Stream;

class UsersMemoryTest extends UsersDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(new AlertsMemory().usersDao));
    }

    public static Stream<Arguments> provideBothDao() {
        var alerts = new AlertsMemory();
        return Stream.of(Arguments.of(alerts, alerts.usersDao));
    }
}