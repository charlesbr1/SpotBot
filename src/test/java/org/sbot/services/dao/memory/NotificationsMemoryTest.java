package org.sbot.services.dao.memory;

import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.NotificationsDaoTest;

import java.util.stream.Stream;

class NotificationsMemoryTest extends NotificationsDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(new NotificationsMemory()));
    }
}