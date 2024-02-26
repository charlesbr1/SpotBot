package org.sbot.services.dao.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.AlertsDaoTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.AlertTest.*;

class AlertsMemoryTest extends AlertsDaoTest {

    public static Stream<Arguments> provideDao() {
        var alerts = new AlertsMemory();
        return Stream.of(Arguments.of(alerts, alerts.usersDao));
    }

    @Test
    void selectionFilter() {
        assertThrows(NullPointerException.class, () -> AlertsMemory.selectionFilter(null));

        long serverId = 1123L;
        long userId = 654L;
        var predicate = AlertsMemory.selectionFilter(SelectionFilter.ofServer(serverId, null));
        assertNotNull(predicate);
        assertFalse(predicate.test(createTestAlert()));
        assertTrue(predicate.test(createTestAlert().withServerId(serverId)));

        predicate = AlertsMemory.selectionFilter(SelectionFilter.ofUser(userId, null));
        assertNotNull(predicate);
        assertFalse(predicate.test(createTestAlert()));
        assertTrue(predicate.test(createTestAlertWithUserId(userId)));

        predicate = AlertsMemory.selectionFilter(SelectionFilter.of(serverId, userId, null));
        assertNotNull(predicate);
        assertFalse(predicate.test(createTestAlertWithUserId(userId)));
        assertTrue(predicate.test(createTestAlertWithUserId(userId).withServerId(serverId)));

        predicate = AlertsMemory.selectionFilter(SelectionFilter.ofServer(serverId, trend));
        assertNotNull(predicate);
        assertFalse(predicate.test(createTestAlert().withServerId(serverId)));
        assertTrue(predicate.test(createTestAlertWithType(trend).withServerId(serverId)));

        predicate = AlertsMemory.selectionFilter(SelectionFilter.ofUser(userId, null).withTickerOrPair("XMR"));
        assertNotNull(predicate);
        assertFalse(predicate.test(createTestAlertWithUserId(userId)));
        assertTrue(predicate.test(createTestAlertWithUserIdAndPair(userId, "DOT/XMR")));
        assertTrue(predicate.test(createTestAlertWithUserIdAndPair(userId, "XMR/ZER")));
        assertFalse(predicate.test(createTestAlertWithUserIdAndPair(userId, "BTX/MRE")));
    }
}