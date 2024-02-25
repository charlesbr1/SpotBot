package org.sbot.services.dao.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.AlertsDao.UpdateField;
import org.sbot.services.dao.AlertsDaoTest;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

    @Test
    void fieldsMapper() {
        assertThrows(NullPointerException.class, () -> AlertsMemory.fieldsMapper(null));

        long serverId = 1123L;
        Alert alert = createTestAlert().withServerId(serverId);
        var fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.SERVER_ID, serverId));
        assertEquals(serverId, fieldsMapper.apply(alert).serverId);
        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(ChronoUnit.MILLIS);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.LISTENING_DATE, date));
        assertEquals(date, fieldsMapper.apply(alert).listeningDate);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.FROM_PRICE, new BigDecimal("34.3434")));
        assertEquals(new BigDecimal("34.3434"), fieldsMapper.apply(alert).fromPrice);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.TO_PRICE, new BigDecimal("134.3434")));
        assertEquals(new BigDecimal("134.3434"), fieldsMapper.apply(alert).toPrice);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.FROM_DATE, date));
        assertEquals(date, fieldsMapper.apply(alert).fromDate);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.TO_DATE, date.plusHours(3L)));
        assertEquals(date.plusHours(3L), fieldsMapper.apply(alert).toDate);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.MESSAGE, "new messs test"));
        assertEquals("new messs test", fieldsMapper.apply(alert).message);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.MARGIN, new BigDecimal("4")));
        assertEquals(new BigDecimal("4"), fieldsMapper.apply(alert).margin);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.REPEAT, (short) 2));
        assertEquals((short) 2, fieldsMapper.apply(alert).repeat);
        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(UpdateField.SNOOZE, (short) 23));
        assertEquals((short) 23, fieldsMapper.apply(alert).snooze);

        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(
                UpdateField.SNOOZE, (short) 223,
                UpdateField.FROM_PRICE, new BigDecimal("99"),
                UpdateField.TO_PRICE, new BigDecimal("199"),
                UpdateField.MESSAGE, "updated",
                UpdateField.SERVER_ID, 999L,
                UpdateField.TO_DATE, date.plusHours(31L),
                UpdateField.FROM_DATE, date.minusMinutes(7L)));
        assertEquals((short) 223, fieldsMapper.apply(alert).snooze);
        assertEquals(new BigDecimal("99"), fieldsMapper.apply(alert).fromPrice);
        assertEquals(new BigDecimal("199"), fieldsMapper.apply(alert).toPrice);
        assertEquals("updated", fieldsMapper.apply(alert).message);
        assertEquals(999L, fieldsMapper.apply(alert).serverId);
        assertEquals(date.plusHours(31L), fieldsMapper.apply(alert).toDate);
        assertEquals(date.minusMinutes(7L), fieldsMapper.apply(alert).fromDate);

        fieldsMapper = AlertsMemory.fieldsMapper(Map.of(
                UpdateField.LISTENING_DATE, date.plusMinutes(666L),
                UpdateField.REPEAT, (short) 3,
                UpdateField.SNOOZE, (short) 54,
                UpdateField.FROM_PRICE, new BigDecimal("1.2"),
                UpdateField.MESSAGE, "updated again"));
        assertEquals(date.plusMinutes(666L), fieldsMapper.apply(alert).listeningDate);
        assertEquals((short) 3, fieldsMapper.apply(alert).repeat);
        assertEquals("updated again", fieldsMapper.apply(alert).message);
        assertEquals((short) 54, fieldsMapper.apply(alert).snooze);
        assertEquals(new BigDecimal("1.2"), fieldsMapper.apply(alert).fromPrice);
    }
}