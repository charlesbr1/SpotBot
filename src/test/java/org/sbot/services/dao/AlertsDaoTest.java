package org.sbot.services.dao;

import org.jdbi.v3.core.statement.StatementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.User;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.entities.alerts.RemainderAlert;
import org.sbot.entities.alerts.TrendAlert;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.memory.AlertsMemory;
import org.sbot.services.dao.sql.AlertsSQLite;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static java.math.BigDecimal.*;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.entities.alerts.Alert.MARGIN_DISABLED;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.RangeAlertTest.createTestRangeAlert;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;
import static org.sbot.services.dao.AlertsDao.UpdateField.*;

public abstract class AlertsDaoTest {

    public static void assertDeepEquals(@Nullable Alert alert, @Nullable Alert other) {
        if (other == alert) return;
        assertTrue(null != alert && null != other);
        assertTrue(alert.id == other.id &&
                alert.userId == other.userId &&
                alert.serverId == other.serverId &&
                alert.repeat == other.repeat &&
                alert.snooze == other.snooze &&
                alert.type == other.type &&
                alert.clientType == other.clientType &&
                Objects.equals(alert.exchange, other.exchange) &&
                Objects.equals(alert.pair, other.pair) &&
                Objects.equals(alert.message, other.message) &&
                Objects.equals(alert.fromPrice, other.fromPrice) &&
                Objects.equals(alert.toPrice, other.toPrice) &&
                Objects.equals(alert.fromDate, other.fromDate) &&
                Objects.equals(alert.toDate, other.toDate) &&
                Objects.equals(alert.listeningDate, other.listeningDate) &&
                Objects.equals(alert.lastTrigger, other.lastTrigger) &&
                Objects.equals(alert.margin, other.margin));
    }

    private static Alert setId(@NotNull Alert alert, long id) {
        return alert.withId(() -> id);
    }

    private static void setUser(@NotNull UsersDao users, long userId) {
        users.addUser(new User(userId, Locale.UK, null, ZonedDateTime.now()));
    }

    private static SelectionFilter ofServer(long serverId, @NotNull String tickerOrPair) {
        return SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null).withTickerOrPair(tickerOrPair);
    }

    private static SelectionFilter ofUser(long userId, @NotNull String tickerOrPair) {
        return SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null).withTickerOrPair(tickerOrPair);
    }

    private static SelectionFilter of(long serverId, long userId, @NotNull String tickerOrPair) {
        return SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null).withTickerOrPair(tickerOrPair);
    }

    @Test
    void selectionFilterTest() {
        assertThrows(NullPointerException.class, () -> SelectionFilter.of(null, 1L, 1L, trend));
        assertThrows(NullPointerException.class, () -> SelectionFilter.ofServer(null, 1L, trend));
        assertThrows(NullPointerException.class, () -> SelectionFilter.ofUser(null, 1L, trend));

        long serverId = 123L;
        long userId = 654L;

        var selection = SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null);
        assertEquals(serverId, selection.serverId());
        assertNull(selection.userId());
        assertNull(selection.tickerOrPair());
        assertNull(selection.type());

        selection = SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, range);
        assertEquals(serverId, selection.serverId());
        assertNull(selection.userId());
        assertNull(selection.tickerOrPair());
        assertEquals(range, selection.type());

        selection = SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null);
        assertNull(selection.serverId());
        assertEquals(userId, selection.userId());
        assertNull(selection.tickerOrPair());
        assertNull(selection.type());

        selection = SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, trend);
        assertNull(selection.serverId());
        assertEquals(userId, selection.userId());
        assertNull(selection.tickerOrPair());
        assertEquals(trend, selection.type());

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null);
        assertEquals(serverId, selection.serverId());
        assertEquals(userId, selection.userId());
        assertNull(selection.tickerOrPair());
        assertNull(selection.type());

        selection = SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, remainder);
        assertNull(selection.serverId());
        assertEquals(userId, selection.userId());
        assertNull(selection.tickerOrPair());
        assertEquals(remainder, selection.type());

        assertEquals("ETH/USD", selection.withTickerOrPair("ETH/USD").tickerOrPair());
        assertNull(selection.withTickerOrPair("ETH/USD").withTickerOrPair(null).tickerOrPair());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        int checkPeriodMin = 15;
        assertDoesNotThrow(() -> alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/ERT1", now, checkPeriodMin, mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(null, "ALL/ERT1", now, checkPeriodMin, mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), null, now, checkPeriodMin, mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/ERT1", null, checkPeriodMin, mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/ERT1", now, checkPeriodMin, null));
        assertThrows(IllegalArgumentException.class, () -> alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/ERT1", now, -1, mock()));

        // listeningDate before now, type trend -> ok
        Alert trendAlert1 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", trend);
        long trendAlertId1 = alerts.addAlert(trendAlert1);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> trendAlertId1 == a.id))));
        assertFalse(trendAlert1.message.isEmpty());
        if(alerts instanceof AlertsSQLite) {
            assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", now, checkPeriodMin,
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        // listeningDate null, type trend -> ko
        Alert trendAlert2 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR2", trend)
                .withListeningDateRepeat(null, (short) 1);
        alerts.addAlert(trendAlert2);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR2", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));

        // listeningDate before now + 1 second, type trend -> ok
        Alert trendAlert3 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR3", trend)
                .withListeningDateRepeat(now.plus(1000L, MILLIS), (short) 1);
        long trendAlertId3 = alerts.addAlert(trendAlert3);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR3", now, 0,
                stream -> assertTrue(stream.allMatch(a -> trendAlertId3 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR3", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> trendAlertId3 == a.id))));

        // listeningDate after now + 1 second, type trend -> ko
        Alert trendAlert4 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR4", trend)
                .withListeningDateRepeat(now.plus(1001L, MILLIS), (short) 1);
        alerts.addAlert(trendAlert4);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR4", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR4", now, 0,
                stream -> assertEquals(0, stream.count())));

        Alert trendAlert5 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR5", trend)
                .withListeningDateRepeat(now.plusMinutes(15L), (short) 1);
        alerts.addAlert(trendAlert5);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR5", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR5", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));


        // listeningDate before now, type range, to date null -> ok
        Alert rangeAlert1 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA1", range)
                .withToDate(null);
        long rangeAlertId1 = alerts.addAlert(rangeAlert1);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA1", now, 0,
                stream -> assertTrue(stream.allMatch(a -> rangeAlertId1 == a.id))));
        assertFalse(rangeAlert1.message.isEmpty());
        if(alerts instanceof AlertsSQLite) {
            assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA1", now, checkPeriodMin,
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        // listeningDate null, type range, to date null -> ko
        Alert rangeAlert2 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA2", range)
                .withListeningDateRepeat(null, (short) 1)
                .withToDate(null);
        alerts.addAlert(rangeAlert2);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA2", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA2", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));

        // listeningDate before now + 1 second, type range, to date null -> ok
        Alert rangeAlert3 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA3", range)
                .withListeningDateRepeat(now.plus(1000L, MILLIS), (short) 1)
                .withToDate(null);
        long rangeAlertId3 = alerts.addAlert(rangeAlert3);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA3", now, 0,
                stream -> assertTrue(stream.allMatch(a -> rangeAlertId3 == a.id))));
        assertFalse(rangeAlert3.message.isEmpty());
        if(alerts instanceof AlertsSQLite) {
            assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA3", now, checkPeriodMin,
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        // listeningDate after now + 1 second, type range, to date null -> ko
        Alert rangeAlert4 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA4", range)
                .withListeningDateRepeat(now.plus(1001L, MILLIS), (short) 1)
                .withToDate(null);
        alerts.addAlert(rangeAlert4);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA4", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA4", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));


        // listeningDate before now, type range, to date after now -> ok
        Alert rangeAlert5 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA5", range)
                .withToDate(now.plusSeconds(1L));
        long rangeAlertId5 = alerts.addAlert(rangeAlert5);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA5", now, 0,
                stream -> assertTrue(stream.allMatch(a -> rangeAlertId5 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA5", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> rangeAlertId5 == a.id))));

        // listeningDate before now, type range, to date = now -> ko
        Alert rangeAlert6 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA6", range)
                .withToDate(now);
        alerts.addAlert(rangeAlert6);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA6", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA6", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));

        // listeningDate before now, type range, to date before now -> ko
        Alert rangeAlert7 = createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA7", range)
                .withToDate(now.minusSeconds(1L));
        alerts.addAlert(rangeAlert7);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA7", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/RA7", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));


        // listeningDate before now, type remainder, from date before now + delta -> ok
        Alert remainderAlert1 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE1", remainder)
                .withFromDate(now.minusSeconds(1L));
        long remainderAlertId1 = alerts.addAlert(remainderAlert1);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE1", now, 0,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId1 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE1", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId1 == a.id))));
        assertFalse(remainderAlert1.message.isEmpty());
        if(alerts instanceof AlertsSQLite) {
            assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE1", now, checkPeriodMin,
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        // listeningDate null, type remainder, from date before now + delta -> ko
        Alert remainderAlert2 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE2", remainder)
                .withListeningDateRepeat(null, (short) 1)
                .withFromDate(now);
        alerts.addAlert(remainderAlert2);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE2", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE2", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));

        // listeningDate before now + 1 second, type remainder, from date before now + delta -> ok
        Alert remainderAlert3 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE3", remainder)
                .withListeningDateRepeat(now.plus(1000L, MILLIS), (short) 1)
                .withFromDate(now.minusSeconds(1L));
        long remainderAlertId3 = alerts.addAlert(remainderAlert3);
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE3", now, 0,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId3 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE3", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId3 == a.id))));
        assertFalse(remainderAlert3.message.isEmpty());
        if(alerts instanceof AlertsSQLite) {
            assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE3", now, checkPeriodMin,
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        // listeningDate after now + 1 second, type remainder, from date before now + delta -> ko
        Alert remainderAlert4 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE4", remainder)
                .withListeningDateRepeat(now.plus(1001L, MILLIS), (short) 1)
                .withFromDate(now.minusSeconds(1L));
        alerts.addAlert(remainderAlert4);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE4", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE4", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));

        // listeningDate before now, type remainder, from date = now + delta -> ko
        Alert remainderAlert5 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", remainder)
                .withFromDate(now.minus(1L, MILLIS).plusMinutes(((checkPeriodMin / 2) + 1)));
        long remainderAlertId5 = alerts.addAlert(remainderAlert5);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", now, 1,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", now, 14,
                stream -> assertEquals(0, stream.count())));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId5 == a.id))));
        assertFalse(remainderAlert5.message.isEmpty());
        if(alerts instanceof AlertsSQLite) {
            assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", now, 3 + checkPeriodMin,
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        Alert remainderAlert6 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE6", remainder)
                .withFromDate(now.plusMinutes(((checkPeriodMin / 2) + 1)));
        long remainderAlertId6 = alerts.addAlert(remainderAlert6);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE6", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE6", now, checkPeriodMin,
                stream -> assertEquals(0, stream.count())));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE6", now, checkPeriodMin + 2,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId6 == a.id))));

        // listeningDate before now, type remainder, from date > now + delta -> ko
        Alert remainderAlert7 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE7", remainder)
                .withFromDate(now.plusMinutes(((checkPeriodMin / 2) + 3)));
        long remainderAlertId7 = alerts.addAlert(remainderAlert7);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE7", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE7", now, checkPeriodMin + 1,
                stream -> assertEquals(0, stream.count())));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE7", now.plusSeconds(1L), checkPeriodMin + 5,
                stream -> assertTrue(stream.allMatch(a -> remainderAlertId7 == a.id))));

        Alert remainderAlert8 = createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE8", remainder)
                .withFromDate(now.plusMinutes(50L));
        alerts.addAlert(remainderAlert8);
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE8", now, 0,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(VIRTUAL_EXCHANGES.get(0), "ALL/RE8", now, 30,
                stream -> assertEquals(0, stream.count())));


        // try deleting alerts while fetching the alert stream
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", trend));
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", trend));
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", trend));

        assertEquals(4, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", now, checkPeriodMin,
                stream -> assertEquals(4, stream.count())));
        assertEquals(4, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", now, checkPeriodMin,
                stream -> alerts.delete(deleter -> {
                    stream.map(Alert::getId).filter(id -> trendAlertId1 != id).forEach(deleter::batchId);
                })));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingPastListeningDateWithActiveRange(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", now, checkPeriodMin,
                stream -> assertTrue(stream.allMatch(a -> trendAlertId1 == a.id))));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(null, mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(DatesTest.nowUtc(), null));

        ZonedDateTime creationDate = TEST_FROM_DATE.minusMinutes(1L);
        ZonedDateTime lastTrigger = creationDate.plusDays(1L);
        Alert alert1 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.minusMinutes(40L), MARGIN_DISABLED, (short) -1);
        long alertId1 = alerts.addAlert(alert1);
        Alert alert2 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.minusHours(3L), MARGIN_DISABLED, (short) -1);
        long alertId2 = alerts.addAlert(alert2);
        Alert alert3 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger, MARGIN_DISABLED, (short) -1);
        long alertId3 = alerts.addAlert(alert3);
        Alert alert4 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.plusSeconds(1L), MARGIN_DISABLED, (short) -1);
        long alertId4 = alerts.addAlert(alert4);
        Alert alert5 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.plusMinutes(31L), MARGIN_DISABLED, (short) -1);
        long alertId5 = alerts.addAlert(alert5);

        alerts.addAlert(createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.minusMinutes(40L), MARGIN_DISABLED, (short) 0));
        alerts.addAlert(createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.minusHours(3L), MARGIN_DISABLED, (short) 1));
        alerts.addAlert(createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger, MARGIN_DISABLED, (short) 3));
        alerts.addAlert(createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.plusSeconds(1L), MARGIN_DISABLED, (short) 2));
        alerts.addAlert(createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger.plusMinutes(31L), MARGIN_DISABLED, (short) 1));

        Alert alert6 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, null, MARGIN_DISABLED, (short) -1);
        long alertId6 = alerts.addAlert(alert6);
        alerts.addAlert(createTestAlert().withListeningDateLastTriggerMarginRepeat(null, null, MARGIN_DISABLED, (short) 1));
        Alert alert7 = createTestAlertWithCreationDate(lastTrigger.plusDays(1L)).withListeningDateLastTriggerMarginRepeat(null, null, MARGIN_DISABLED, (short) -1);
        long alertId7 = alerts.addAlert(alert7);
        alerts.addAlert(createTestAlertWithCreationDate(lastTrigger.plusDays(1L)).withListeningDateLastTriggerMarginRepeat(null, null, MARGIN_DISABLED, (short) 1));

        assertEquals(14, alerts.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, TEST_USER_ID, null)));
        assertEquals(0, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusDays(2L),
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusDays(1L),
                stream -> assertEquals(0, stream.count())));
        assertEquals(1, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusDays(1L).plusSeconds(1L),
                stream -> assertTrue(stream.allMatch(a -> alertId6 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusDays(1L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> alertId6 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusDays(1L).plusHours(3L),
                stream -> assertTrue(stream.allMatch(a -> alertId6 == a.id))));
        if(alerts instanceof AlertsSQLite) { // sql dao do not retrieve message
            assertEquals(1, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusDays(1L).plusHours(3L),
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }

        assertEquals(3, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.minusSeconds(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId6).contains(a.id)))));
        assertEquals(3, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger,
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId6).contains(a.id)))));
        assertEquals(4, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.plusSeconds(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId6).contains(a.id)))));
        assertEquals(5, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.plusSeconds(2L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId4, alertId6).contains(a.id)))));
        assertEquals(6, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.plusMinutes(32L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId4, alertId5, alertId6).contains(a.id)))));
        assertEquals(6, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.plusDays(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId4, alertId5, alertId6).contains(a.id)))));
        assertEquals(7, alerts.fetchAlertsWithoutMessageHavingRepeatNegativeAndLastTriggerBeforeOrNullAndCreationBefore(lastTrigger.plusDays(1L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId4, alertId5, alertId6, alertId7).contains(a.id)))));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void fetchAlertsWithoutMessageByTypeHavingToDateBefore(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(null, DatesTest.nowUtc(), mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, null, mock()));
        assertThrows(NullPointerException.class, () -> alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, DatesTest.nowUtc(), null));

        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        Alert alert1 = createTestAlertWithType(range).withToDate(date.plusMinutes(40L));
        long alertId1 = alerts.addAlert(alert1);
        Alert alert2 = createTestAlertWithType(range).withToDate(date.plusHours(3L));
        long alertId2 = alerts.addAlert(alert2);
        Alert alert3 = createTestAlertWithType(range).withToDate(date.plusHours(10L));
        long alertId3 = alerts.addAlert(alert3);
        Alert alert4 = createTestAlertWithType(range).withToDate(date.plusDays(1L).plusHours(2L));
        long alertId4 = alerts.addAlert(alert4);
        alerts.addAlert(createTestAlertWithType(range).withToDate(null));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusMinutes(40L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusMinutes(10L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusMinutes(50L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusHours(3L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusHours(2L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusHours(4L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusHours(10L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusHours(12L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusDays(1L).plusHours(2L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusDays(1L).plusHours(2L).plusMinutes(13L)));

        assertEquals(15, alerts.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, TEST_USER_ID, null)));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusMinutes(40L),
                stream -> assertEquals(0, stream.count())));

        assertEquals(1, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusMinutes(41L),
                stream -> assertTrue(stream.allMatch(a -> alertId1 == a.id))));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusHours(3L),
                stream -> assertTrue(stream.allMatch(a -> alertId1 == a.id))));
        assertEquals(2, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusHours(3L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId2).count())));
        assertEquals(2, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusHours(3L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2).contains(a.id)))));
        assertEquals(2, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusHours(10L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2).contains(a.id)))));
        assertEquals(3, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusHours(10L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId3).count())));
        assertEquals(3, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusHours(10L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3).contains(a.id)))));
        assertEquals(3, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusDays(1L).plusHours(2L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3).contains(a.id)))));
        assertEquals(4, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusDays(1L).plusHours(2L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId4).count())));
        assertEquals(4, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(range, date.plusDays(1L).plusHours(2L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId4).contains(a.id)))));

        assertEquals(0, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(trend, date,
                stream -> assertEquals(0, stream.count())));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(trend, date.plusMinutes(11L),
                stream -> assertEquals(1, stream.count())));
        assertEquals(1, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(trend, date.plusHours(3L),
                stream -> assertEquals(1, stream.count())));
        assertEquals(2, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(trend, date.plusHours(3L).plusMinutes(1L),
                stream -> assertEquals(2, stream.count())));

        if(alerts instanceof AlertsSQLite) { // sql dao do not retrieve message
            assertEquals(2, alerts.fetchAlertsWithoutMessageByTypeHavingToDateBefore(trend, date.plusHours(3L).plusMinutes(1L),
                    stream -> assertTrue(stream.allMatch(a -> a.message.isEmpty()))));
        }
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getPairsByExchangesHavingPastListeningDateWithActiveRange(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        int checkPeriodMin = 15;
        assertDoesNotThrow(() -> alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin));
        assertThrows(NullPointerException.class, () -> alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(null, checkPeriodMin));
        assertThrows(IllegalArgumentException.class, () -> alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, -1));

        // listeningDate before now, type trend -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR1", trend));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate null, type trend -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR2", trend)
                .withListeningDateRepeat(null, (short) 1));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));


        // listeningDate before now + 1 second, type trend -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR3", trend)
                .withListeningDateRepeat(now.plus(1000L, MILLIS), (short) 1));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate after now + 1 second, type trend -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR4", trend)
                .withListeningDateRepeat(now.plus(1001L, MILLIS), (short) 1));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/TR5", trend)
                .withListeningDateRepeat(now.plusMinutes(15L), (short) 1));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));


        // listeningDate before now, type range, to date null -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA1", range)
                .withToDate(null));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate null, type range, to date null -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA2", range)
                .withListeningDateRepeat(null, (short) 1)
                .withToDate(null));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate before now + 1 second, type range, to date null -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA3", range)
                .withListeningDateRepeat(now.plus(1000L, MILLIS), (short) 1)
                .withToDate(null));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(4, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(4, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1", "ALL/RA3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate after now + 1 second, type range, to date null -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA4", range)
                .withListeningDateRepeat(now.plus(1001L, MILLIS), (short) 1)
                .withToDate(null));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(4, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(4, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1", "ALL/RA3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));


        // listeningDate before now, type range, to date after now -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA5", range)
                .withToDate(now.plusSeconds(1L)));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1", "ALL/RA3", "ALL/RA5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate before now, type range, to date = now -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA6", range)
                .withToDate(now));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1", "ALL/RA3", "ALL/RA5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));

        // listeningDate before now, type range, to date before now -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(SUPPORTED_EXCHANGES.get(0), "ALL/RA7", range)
                .withToDate(now.minusSeconds(1L)));
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/TR1", "ALL/TR3", "ALL/RA1", "ALL/RA3", "ALL/RA5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(SUPPORTED_EXCHANGES.get(0)));


        // listeningDate before now, type remainder, from date before now + delta -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE1", remainder)
                .withFromDate(now.minusSeconds(1L)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        // listeningDate null, type remainder, from date before now + delta -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE2", remainder)
                .withListeningDateRepeat(null, (short) 1)
                .withFromDate(now));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(1, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        // listeningDate before now + 1 second, type remainder, from date before now + delta -> ok
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE3", remainder)
                .withListeningDateRepeat(now.plus(1000L, MILLIS), (short) 1)
                .withFromDate(now.minusSeconds(1L)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        // listeningDate after now + 1 second, type remainder, from date before now + delta -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE4", remainder)
                .withListeningDateRepeat(now.plus(1001L, MILLIS), (short) 1)
                .withFromDate(now.minusSeconds(1L)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        // listeningDate before now, type remainder, from date = now + delta -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE5", remainder)
                .withFromDate(now.minus(1L, MILLIS).plusMinutes(((checkPeriodMin / 2) + 1))));

        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 1).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 1).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 1).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 14).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 14).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 14).get(VIRTUAL_EXCHANGES.get(0)));

        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 3 + checkPeriodMin).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 3 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 3 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE6", remainder)
                .withFromDate(now.plusMinutes(((checkPeriodMin / 2) + 1))));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 2 + checkPeriodMin).size());
        assertEquals(4, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 2 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5", "ALL/RE6"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 2 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        // listeningDate before now, type remainder, from date > now + delta -> ko
        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE7", remainder)
                .withFromDate(now.plusMinutes(((checkPeriodMin / 2) + 3))));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 1 + checkPeriodMin).size());
        assertEquals(3, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 1 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 1 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now.plusSeconds(1L), 5 + checkPeriodMin).size());
        assertEquals(6, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now.plusSeconds(1L), 5 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE4", "ALL/RE5", "ALL/RE6", "ALL/RE7"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now.plusSeconds(1L), 5 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 6 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5", "ALL/RE6", "ALL/RE7"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 6 + checkPeriodMin).get(VIRTUAL_EXCHANGES.get(0)));

        alerts.addAlert(createTestAlertWithExchangeAndPairAndType(VIRTUAL_EXCHANGES.get(0), "ALL/RE8", remainder)
                .withFromDate(now.plusMinutes(50L)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).size());
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 0).get(VIRTUAL_EXCHANGES.get(0)));
        assertEquals(2, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 30).size());
        assertEquals(5, alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 30).get(VIRTUAL_EXCHANGES.get(0)).size());
        assertEquals(Set.of("ALL/RE1", "ALL/RE3", "ALL/RE5", "ALL/RE6", "ALL/RE7"), alerts.getPairsByExchangesHavingPastListeningDateWithActiveRange(now, 30).get(VIRTUAL_EXCHANGES.get(0)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getUserIdsByServerId(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.getUserIdsByServerId(null, 1L));
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserId(user1).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserId(user1).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserId(user2).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserId(user3).withServerId(server2));

        var userIds = alerts.getUserIdsByServerId(TEST_CLIENT_TYPE, server1);
        assertNotEquals(new HashSet<>(userIds), Set.of(user1));
        assertEquals(new HashSet<>(userIds), Set.of(user1, user2));

        userIds = alerts.getUserIdsByServerId(TEST_CLIENT_TYPE, server2);
        assertNotEquals(new HashSet<>(userIds), Set.of(user1));
        assertEquals(new HashSet<>(userIds), Set.of(user1, user3));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlert(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.getAlert(null, 1L));
        setUser(users, TEST_USER_ID);

        Alert alert = createTestAlert();
        assertEquals(0, alert.id);
        long alertId = alerts.addAlert(alert);
        assertEquals(1, alertId);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        assertNotEquals(alert, alerts.getAlert(TEST_CLIENT_TYPE, alertId).get());
        assertDeepEquals(setId(alert, 1L), alerts.getAlert(TEST_CLIENT_TYPE, alertId).get());

        // check type of retrieved alert
        alertId = alerts.addAlert(createTestAlertWithType(range));
        assertEquals(2, alertId);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        if(alerts instanceof AlertsSQLite) // memory dao returns provided test instance : TestAlert
            assertInstanceOf(RangeAlert.class, alerts.getAlert(TEST_CLIENT_TYPE, alertId).get());

        alertId = alerts.addAlert(createTestAlertWithType(trend));
        assertEquals(3, alertId);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        if(alerts instanceof AlertsSQLite) // memory dao returns provided test instance : TestAlert
            assertInstanceOf(TrendAlert.class, alerts.getAlert(TEST_CLIENT_TYPE, alertId).get());

        alertId = alerts.addAlert(createTestAlertWithType(remainder));
        assertEquals(4, alertId);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        if(alerts instanceof AlertsSQLite) // memory dao returns provided test instance : TestAlert
            assertInstanceOf(RemainderAlert.class, alerts.getAlert(TEST_CLIENT_TYPE, alertId).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertWithoutMessage(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.getAlertWithoutMessage(null, 1L));
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withMessage("message...");
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        assertEquals(alert.message, alerts.getAlert(TEST_CLIENT_TYPE, alertId).get().message);
        assertTrue(alerts.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId).isPresent());
        assertNotEquals(alert.message, alerts.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId).get().message);
        assertTrue(alerts.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId).get().message.isEmpty());
        assertDeepEquals(setId(alert, 1L).withMessage(""), alerts.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertMessages(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        assertThrows(NullPointerException.class, () -> alerts.getAlertMessages(null));

        Alert alert1 = createTestAlert().withMessage("AAAA");
        alert1 = setId(alert1, alerts.addAlert(alert1));
        Alert alert2 = createTestAlert().withMessage("BBBB");
        alert2 = setId(alert2, alerts.addAlert(alert2));
        Alert alert3 = createTestAlert().withMessage("CCCC");
        alert3 = setId(alert3, alerts.addAlert(alert3));
        Alert alert4 = createTestAlert().withMessage("DDDD");
        alert4 = setId(alert4, alerts.addAlert(alert4));

        var messages = alerts.getAlertMessages(LongStream.of());
        assertEquals(0, messages.size());

        messages = alerts.getAlertMessages(LongStream.of(alert1.id));
        assertEquals(1, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));

        messages = alerts.getAlertMessages(LongStream.of(alert2.id));
        assertEquals(1, messages.size());
        assertEquals(alert2.message, messages.get(alert2.id));

        messages = alerts.getAlertMessages(LongStream.of(alert3.id));
        assertEquals(1, messages.size());
        assertEquals(alert3.message, messages.get(alert3.id));

        messages = alerts.getAlertMessages(LongStream.of(alert4.id));
        assertEquals(1, messages.size());
        assertEquals(alert4.message, messages.get(alert4.id));

        messages = alerts.getAlertMessages(LongStream.of(alert1.id, alert4.id));
        assertEquals(2, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));
        assertEquals(alert4.message, messages.get(alert4.id));


        messages = alerts.getAlertMessages(LongStream.of(alert1.id, alert2.id, alert4.id));
        assertEquals(3, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));
        assertEquals(alert2.message, messages.get(alert2.id));
        assertEquals(alert4.message, messages.get(alert4.id));

        messages = alerts.getAlertMessages(LongStream.of(alert1.id, alert2.id, alert3.id, alert4.id));
        assertEquals(4, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));
        assertEquals(alert2.message, messages.get(alert2.id));
        assertEquals(alert3.message, messages.get(alert3.id));
        assertEquals(alert4.message, messages.get(alert4.id));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlerts(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.countAlerts(null));

        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/BTC", range).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/USD", trend).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/BTC", remainder).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user2, "DOT/BTC", range).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user3, "ETH/BTC", remainder).withServerId(server2));

        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null)));
        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, range)));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, trend)));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, remainder)));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null).withTickerOrPair("ETH/BTC")));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, range).withTickerOrPair("ETH/BTC")));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, remainder).withTickerOrPair("ETH/BTC")));
        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null).withTickerOrPair("BTC")));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, remainder).withTickerOrPair("BTC")));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null).withTickerOrPair("DOT")));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, range).withTickerOrPair("DOT")));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, trend).withTickerOrPair("DOT")));

        assertEquals(3, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null)));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, range)));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, trend)));
        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, remainder)));
        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null).withTickerOrPair("ETH/BTC")));
        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, remainder).withTickerOrPair("ETH/BTC")));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, range).withTickerOrPair("ETH/BTC")));
        assertEquals(3, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null).withTickerOrPair("ETH")));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, trend).withTickerOrPair("ETH")));
        assertEquals(2, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, remainder).withTickerOrPair("ETH")));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlertsOfUser(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(3, alerts.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, user1, null)));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, user2, null)));
        assertEquals(1, alerts.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, user3, null)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlertsOfUserAndTickers(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(2, alerts.countAlerts(ofUser(user1, "ETH/BTC")));
        assertEquals(3, alerts.countAlerts(ofUser(user1, "ETH")));
        assertEquals(1, alerts.countAlerts(ofUser(user1, "USD")));
        assertEquals(2, alerts.countAlerts(ofUser(user1, "BTC")));
        assertEquals(0, alerts.countAlerts(ofUser(user2, "ETH/BTC")));
        assertEquals(1, alerts.countAlerts(ofUser(user2, "DOT")));
        assertEquals(1, alerts.countAlerts(ofUser(user2, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(ofUser(user3, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(ofUser(user3, "WAG/BTC")));
        assertEquals(1, alerts.countAlerts(ofUser(user3, "BTC")));
        assertEquals(1, alerts.countAlerts(ofUser(user3, "ETH/BTC")));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlertsOfServer(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ADA/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "BNB/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "ADA/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(9, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null)));
        assertEquals(3, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null)));
        assertEquals(0, alerts.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, 123L, null)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlertsOfServerAndUser(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ADA/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(3, alerts.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, null)));
        assertEquals(1, alerts.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, null)));
        assertEquals(0, alerts.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user3, null)));
        assertEquals(2, alerts.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, null)));
        assertEquals(0, alerts.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user2, null)));
        assertEquals(1, alerts.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, null)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlertsOfServerAndTickers(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(1, alerts.countAlerts(ofServer(server1, "ETH/BTC")));
        assertEquals(1, alerts.countAlerts(ofServer(server1, "ETH")));
        assertEquals(0, alerts.countAlerts(ofServer(server1, "USD")));
        assertEquals(2, alerts.countAlerts(ofServer(server1, "BTC")));
        assertEquals(1, alerts.countAlerts(ofServer(server1, "DOT")));
        assertEquals(1, alerts.countAlerts(ofServer(server1, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(ofServer(server1, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(ofServer(server1, "WAG/BTC")));

        assertEquals(2, alerts.countAlerts(ofServer(server2, "ETH/BTC")));
        assertEquals(3, alerts.countAlerts(ofServer(server2, "ETH")));
        assertEquals(1, alerts.countAlerts(ofServer(server2, "USD")));
        assertEquals(2, alerts.countAlerts(ofServer(server2, "BTC")));
        assertEquals(0, alerts.countAlerts(ofServer(server2, "DOT")));
        assertEquals(0, alerts.countAlerts(ofServer(server2, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(ofServer(server2, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(ofServer(server2, "WAG/BTC")));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void countAlertsOfServerAndUserAndTickers(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ADA/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "BNB/BTC").withServerId(server2));

        assertEquals(1, alerts.countAlerts(of(server1, user1, "ETH/BTC")));
        assertEquals(1, alerts.countAlerts(of(server1, user1, "ETH")));
        assertEquals(0, alerts.countAlerts(of(server1, user1, "USD")));
        assertEquals(1, alerts.countAlerts(of(server1, user1, "BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user1, "DOT")));
        assertEquals(0, alerts.countAlerts(of(server1, user1, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user1, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(of(server1, user1, "WAG/BTC")));

        assertEquals(1, alerts.countAlerts(of(server2, user1, "ETH/BTC")));
        assertEquals(2, alerts.countAlerts(of(server2, user1, "ETH")));
        assertEquals(1, alerts.countAlerts(of(server2, user1, "USD")));
        assertEquals(1, alerts.countAlerts(of(server2, user1, "BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user1, "DOT")));
        assertEquals(0, alerts.countAlerts(of(server2, user1, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user1, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(of(server2, user1, "WAG/BTC")));


        assertEquals(0, alerts.countAlerts(of(server1, user2, "ETH/BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user2, "ETH")));
        assertEquals(0, alerts.countAlerts(of(server1, user2, "USD")));
        assertEquals(1, alerts.countAlerts(of(server1, user2, "BTC")));
        assertEquals(1, alerts.countAlerts(of(server1, user2, "DOT")));
        assertEquals(1, alerts.countAlerts(of(server1, user2, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user2, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(of(server1, user2, "WAG/BTC")));

        assertEquals(0, alerts.countAlerts(of(server2, user2, "ETH/BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "ETH")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "USD")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "DOT")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(of(server2, user2, "WAG/BTC")));


        assertEquals(0, alerts.countAlerts(of(server1, user3, "ETH/BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "ETH")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "USD")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "DOT")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(of(server1, user3, "WAG/BTC")));

        assertEquals(1, alerts.countAlerts(of(server2, user3, "ETH/BTC")));
        assertEquals(1, alerts.countAlerts(of(server2, user3, "ETH")));
        assertEquals(0, alerts.countAlerts(of(server2, user3, "USD")));
        assertEquals(3, alerts.countAlerts(of(server2, user3, "BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user3, "DOT")));
        assertEquals(0, alerts.countAlerts(of(server2, user3, "DOT/BTC")));
        assertEquals(0, alerts.countAlerts(of(server2, user3, "WAG/BNG")));
        assertEquals(0, alerts.countAlerts(of(server2, user3, "WAG/BTC")));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOrderByPairUserIdId(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.getAlertsOrderByPairUserIdId(null, 0L, 1L));
        assertThrows(NullPointerException.class, () -> alerts.getAlertsOrderByPairUserIdId(mock(), 0L, 0L));
        assertThrows(NullPointerException.class, () -> alerts.getAlertsOrderByPairUserIdId(mock(), 0L, -1L));
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/BTC", range).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/USD", trend).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/BTC", remainder).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user2, "DOT/BTC", trend).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user3, "ETH/BTC", range).withServerId(server2));

        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, trend), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, remainder), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, range), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null).withTickerOrPair( "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, range).withTickerOrPair( "ETH/BTC"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null).withTickerOrPair( "ETH"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, range).withTickerOrPair( "ETH"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, remainder).withTickerOrPair( "ETH"), 0, 1000).size());

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null).withTickerOrPair("BTC"), 0, 1000)).size());
        assertEquals(1, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, trend).withTickerOrPair("BTC"), 0, 1000)).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, remainder).withTickerOrPair("BTC"), 0, 1000).size());

        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, trend), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, remainder), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, range), 0, 1000).size());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOfUserOrderByPairId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        var filterUser1 = SelectionFilter.ofUser(TEST_CLIENT_TYPE, user1, null);
        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterUser1, 0, 1000)).size());
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(filterUser1, 0, 1000).stream().filter(alert -> alert.userId == user1).count());
        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterUser1, 0, 3)).size());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterUser1, 0, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterUser1, 0, 1).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterUser1, 2, 3).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterUser1, 3, 3).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterUser1, 30, 3).size());

        var filterUser2 = SelectionFilter.ofUser(TEST_CLIENT_TYPE, user2, null);
        var filterUser3 = SelectionFilter.ofUser(TEST_CLIENT_TYPE, user3, null);
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterUser2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterUser2, 0, 1000).stream().filter(alert -> alert.userId == user2).count());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterUser2, 1, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterUser3, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterUser3, 0, 1000).stream().filter(alert -> alert.userId == user3).count());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOfUserAndTickersOrderByPairId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .filter(alert -> alert.userId == user1).count());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 0, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 0, 1).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 1, 2).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 2, 3).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH/BTC"), 20, 3).size());

        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH"), 0, 1000)).size());
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "ETH"), 0, 3).stream()
                .filter(alert -> alert.pair.contains("ETH"))
                .filter(alert -> alert.userId == user1).count());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "USD"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "USD"), 0, 1).stream()
                .filter(alert -> alert.pair.contains("USD"))
                .filter(alert -> alert.userId == user1).count());

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "BTC"), 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(ofUser(user1, "BTC"), 0, 2).stream()
                .filter(alert -> alert.pair.contains("BTC"))
                .filter(alert -> alert.userId == user1).count());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofUser(user2, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user2, "DOT"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user2, "DOT"), 0, 1000).stream()
                .filter(alert -> alert.pair.contains("DOT"))
                .filter(alert -> alert.userId == user2).count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user2, "DOT/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user2, "DOT/BTC"), 0, 1000).stream()
                .filter(alert -> alert.pair.contains("DOT/BTC"))
                .filter(alert -> alert.userId == user2).count());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofUser(user3, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofUser(user3, "WAG/BTC"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user3, "BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user3, "BTC"), 0, 1000).stream()
                .filter(alert -> alert.pair.contains("BTC"))
                .filter(alert -> alert.userId == user3).count());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user3, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofUser(user3, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.pair.contains("BTC"))
                .filter(alert -> alert.userId == user3).count());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOfServerOrderByPairUserIdId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ADA/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "BNB/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "ADA/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        var filterServer1 = SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null);
        assertEquals(9, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer1, 0, 1000)).size());
        assertEquals(9, alerts.getAlertsOrderByPairUserIdId(filterServer1, 0, 1000).stream()
                .filter(alert -> alert.serverId == server1).count());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer1, 0, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1, 0, 1).size());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer1, 1, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1, 1, 1).size());
        assertEquals(5, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer1, 2, 5)).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer1, 9, 7).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer1, 20, 3).size());

        var filterServer2 = SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null);
        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer2, 0, 1000)).size());
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(filterServer2, 0, 1000).stream()
                .filter(alert -> alert.serverId == server2).count());
        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer2, 0, 3)).size());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer2, 0, 2)).size());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer2, 1, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer2, 2, 2).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer2, 3, 2).size());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOfServerAndUserOrderByPairId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ADA/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        var filterServer1User1 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, null);
        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer1User1, 0, 1000)).size());
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(filterServer1User1, 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1User1, 0, 1).size());

        var filterServer1User2 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, null);
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1User2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1User2, 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1User2, 0, 1).size());

        var filterServer1User3 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user3, null);
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer1User3, 0, 1000).size());

        var filterServer2User1 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, null);
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .count());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 0, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 0, 1).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 1, 2).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 2, 3).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 20, 3).size());

        var filterServer2User2 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user2, null);
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer2User2, 0, 1000).size());
        var filterServer2User3 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, null);
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer2User3, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer2User3, 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .count());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOfServerAndTickersOrderByPairUserIdId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "ETH/BTC"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "ETH"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "ETH"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "ETH"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "USD"), 0, 1000).size());

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "BTC"), 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "BTC"), 0, 2)).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "DOT"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "DOT"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("DOT"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "DOT"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "DOT/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "DOT/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("DOT/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "DOT/BTC"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server1, "WAG/BTC"), 0, 1000).size());

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH/BTC"), 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH/BTC"), 0, 2).size());

        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 0, 1000)).size());
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 0, 3)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 2, 3).size());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 1, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 2, 2).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 3, 2).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 3, 4).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 30, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "USD"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "USD"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("USD"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "USD"), 0, 1).size());

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "BTC"), 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "ETH"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "BTC"), 0, 2)).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "DOT"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "DOT/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "WAF/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(ofServer(server2, "WAG/BTC"), 0, 1000).size());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getAlertsOfServerAndUserAndTickersOrderByPairId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ADA/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "BNB/BTC").withServerId(server2));

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "USD"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "BTC"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "DOT"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "DOT/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "WAG/BTC"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH/BTC"), 0, 1).size());

        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000)).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(2, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 2)).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 1, 2).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 2, 1).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 3, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("USD"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "BTC"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "DOT"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "DOT/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "WAG/BTC"), 0, 1000).size());


        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "ETH/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "ETH"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "USD"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .filter(alert -> alert.pair.contains("DOT"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .filter(alert -> alert.pair.contains("DOT/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT/BTC"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "WAG/BTC"), 0, 1000).size());


        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "ETH/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "ETH"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "USD"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "DOT"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "DOT/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user2, "WAG/BTC"), 0, 1000).size());


        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "ETH/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "ETH"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "USD"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "DOT"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "DOT/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user3, "WAG/BTC"), 0, 1000).size());


        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH/BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH/BTC"), 0, 1).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "USD"), 0, 1000).size());

        assertEquals(3, assertSortedByPairUserIdId(alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "BTC"), 0, 1000)).size());
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "BTC"), 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "BTC"), 0, 1).size());

        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "DOT"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "DOT/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "WAG/BNG"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "WAG/BTC"), 0, 1000).size());
    }

    @NotNull
    private List<Alert> assertSortedByPairUserIdId(@NotNull List<Alert> alerts) {
        return assertSortedBy(alerts, (a, b) -> a.pair.compareTo(b.pair) < 0 || (a.pair.equals(b.pair) &&
                (a.userId < b.userId || (a.userId == b.userId && a.id <= b.id))));
    }

    @NotNull
    private List<Alert> assertSortedBy(@NotNull List<Alert> alerts, @NotNull BiPredicate<Alert, Alert> comparingCheck) {
        assertFalse(alerts.isEmpty());
        assertEquals(alerts.get(alerts.size() -1).getId(), alerts.stream()
                .reduce((a, b) -> {
                    if(!comparingCheck.test(a, b))
                        throw new IllegalStateException("Invalid sorting order");
                    return b;
                }).map(Alert::getId).get());
        return alerts;
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void addAlert(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.addAlert(null));

        // test user foreign key constraint
        if(alerts instanceof AlertsMemory) {
            assertThrows(IllegalArgumentException.class, () -> alerts.addAlert(createTestAlert()));
        } else if(alerts instanceof AlertsSQLite) {
            assertThrows(StatementException.class, () -> alerts.addAlert(createTestAlert()));
        }

        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert();
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        assertDeepEquals(alert.withId(() -> alertId), alerts.getAlert(TEST_CLIENT_TYPE, alertId).get());
        // check id strictly increments
        for(long i = alertId; i++ < alertId + 1000;) {
            if(i > 3)
                alerts.delete(TEST_CLIENT_TYPE, i - 3);
            assertEquals(i, alerts.addAlert(createTestAlert()));
        }
        var finalAlert = setId(alert, alerts.addAlert(alert));
        assertThrows(IllegalArgumentException.class, () -> alerts.addAlert(finalAlert));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerIdOf(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.updateServerIdOf(null, 0L));

        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPairType(user1, "ETH/BTC", range).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPairType(user1, "ETH/USD", trend).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPairType(user2, "DOT/BTC", trend).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPairType(user3, "ETH/BTC", remainder).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, null).withTickerOrPair("ETH/BTC"), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, range).withTickerOrPair("ETH/BTC"), 0, 1000).contains(alertU1S1));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, trend).withTickerOrPair("ETH/BTC"), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, null).withTickerOrPair("BTC"), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, trend).withTickerOrPair("BTC"), 0, 1000).contains(alertU2S1));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, range).withTickerOrPair("BTC"), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, null).withTickerOrPair("DOT"), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, trend).withTickerOrPair("DOT"), 0, 1000).contains(alertU2S1));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, remainder).withTickerOrPair("DOT"), 0, 1000).contains(alertU2S1));

        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, null).withTickerOrPair("ETH"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, trend).withTickerOrPair("ETH"), 0, 1000).contains(alertU1S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, remainder).withTickerOrPair("ETH"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, null).withTickerOrPair("USD"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, trend).withTickerOrPair("USD"), 0, 1000).contains(alertU1S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, range).withTickerOrPair("USD"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, null).withTickerOrPair("ETH"), 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, remainder).withTickerOrPair("ETH"), 0, 1000).contains(alertU3S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, range).withTickerOrPair("ETH"), 0, 1000).contains(alertU3S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, trend).withTickerOrPair("ETH"), 0, 1000).contains(alertU3S2));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerIdOfUserAndServerId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null), 0, 1000).contains(alert2U1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null), 0, 1000).contains(alertU3S2));

        long newServerId = 987654321L;
        var filterServer1User1 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, null);
        var filterServer1User2 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, null);
        var filterServer2User1 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, null);
        var filterServer2User3 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, null);

        assertEquals(1, alerts.updateServerIdOf(filterServer1User1, newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, newServerId, null), 0, 1000).contains(alertU1S1));
        assertEquals(0, alerts.updateServerIdOf(filterServer1User1, newServerId));
        assertEquals(1, alerts.updateServerIdOf(filterServer2User3, newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null), 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, newServerId, null), 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, newServerId, null), 0, 1000).contains(alertU1S1));
        assertEquals(2, alerts.updateServerIdOf(filterServer2User1, newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null), 0, 1000).contains(alertU1S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null), 0, 1000).contains(alert2U1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, newServerId, null), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, newServerId, null), 0, 1000).contains(alert2U1S2));
        assertEquals(1, alerts.updateServerIdOf(filterServer1User2, newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server1, null), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, newServerId, null), 0, 1000).contains(alertU2S1));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerIdOfUserAndServerIdAndTickers(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "BTC"), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1000).contains(alertU2S1));

        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000).contains(alert2U1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH"), 0, 1000).contains(alertU3S2));

        long newServerId = 987654321L;
        assertEquals(0, alerts.updateServerIdOf(of(server1, user1, "WAG/BSD"), newServerId));
        assertEquals(1, alerts.updateServerIdOf(of(server1, user1, "ETH/BTC"), newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user1, "ETH/BTC"), 0, 1000).contains(alertU1S1));

        assertEquals(1, alerts.updateServerIdOf(of(server1, user2, "DOT"), newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1000).contains(alertU2S1));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user2, "DOT"), 0, 1000).contains(alertU2S1));

        assertEquals(2, alerts.updateServerIdOf(of(server2, user1, "ETH"), newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000).contains(alertU1S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1000).contains(alertU1S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000).contains(alert2U1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user1, "ETH"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user1, "USD"), 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user1, "ETH"), 0, 1000).contains(alert2U1S2));
        assertEquals(3, alerts.getAlertsOrderByPairUserIdId(of(newServerId, user1, "ETH"), 0, 1000).size());

        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user3, "ETH"), 0, 1000).contains(alertU3S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user3, "BTC"), 0, 1000).contains(alertU3S2));
        assertEquals(1, alerts.updateServerIdOf(of(server2, user3, "BTC"), newServerId));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH/BTC"), 0, 1000).contains(alertU3S2));
        assertFalse(alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "BTC"), 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user3, "ETH"), 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOrderByPairUserIdId(of(newServerId, user3, "BTC"), 0, 1000).contains(alertU3S2));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void update(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.update(null, null));
        assertThrows(NullPointerException.class, () -> alerts.update(null, Set.of(MESSAGE)));
        assertThrows(NullPointerException.class, () -> alerts.update(createTestAlert(), null));

        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withServerId(123L);
        alert = setId(alert, alerts.addAlert(alert));
        alerts.update(alert, EnumSet.allOf(AlertsDao.UpdateField.class));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertDeepEquals(alert, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get());

        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(MILLIS) // clear the nanoseconds as sqlite save milliseconds
                .plusHours(1L);
        alert = alert
                .withServerId(321L)
                .withListeningDateRepeat(date, (short) 22)
                .withFromPrice(new BigDecimal("1212"))
                .withToPrice(new BigDecimal("91212"))
                .withFromDate(date)
                .withToDate(date.plusDays(2L))
                .withMessage("new test message updated")
                .withMargin(new BigDecimal("34"))
                .withSnooze((short) 11);

        alerts.update(alert, EnumSet.allOf(AlertsDao.UpdateField.class));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertDeepEquals(alert, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateServerId(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withServerId(123L);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(123L, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().serverId);
        alerts.update(alert.withServerId(321L), Set.of(SERVER_ID));
        assertEquals(321L, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().serverId);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateListeningDate(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(MILLIS) // clear the nanoseconds as sqlite save milliseconds
                .minusMonths(1L);
        Alert alert = createTestAlert().withToDate(date.plusYears(33L)).withListeningDateFromDate(date, date.plusMinutes(37L));
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(date, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().listeningDate);
        alerts.update(alert.withListeningDateRepeat(date.plusDays(3L), alert.repeat), Set.of(LISTENING_DATE));
        assertEquals(date.plusDays(3L), alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().listeningDate);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateFromPrice(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestRangeAlert().withFromPrice(ONE);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(ONE, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().fromPrice);
        alerts.update(alert.withFromPrice(TWO), Set.of(FROM_PRICE));
        assertEquals(TWO, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().fromPrice);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateToPrice(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestRangeAlert().withToPrice(BigDecimal.valueOf(100L));
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(BigDecimal.valueOf(100L), alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().toPrice);
        alerts.update(alert.withToPrice(BigDecimal.valueOf(30L)), Set.of(TO_PRICE));
        assertEquals(BigDecimal.valueOf(30L), alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().toPrice);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateFromDate(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        ZonedDateTime date = TEST_FROM_DATE.minusWeeks(2L).truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        Alert alert = createTestAlert().withFromDate(date);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(date, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().fromDate);
        alerts.update(alert.withFromDate(date.plusHours(3L)), Set.of(FROM_DATE));
        assertEquals(date.plusHours(3L), alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().fromDate);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateToDate(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        Alert alert = createTestAlert().withToDate(date);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(date, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().toDate);
        alerts.update(alert.withToDate(date.plusHours(3L)), Set.of(TO_DATE));
        assertEquals(date.plusHours(3L), alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().toDate);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateMessage(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withMessage("message");
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals("message", alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().message);
        alerts.update(alert.withMessage("new message"), Set.of(MESSAGE));
        assertEquals("new message", alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().message);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateMargin(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withMargin(ONE);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(ONE, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().margin);
        alerts.update(alert.withMargin(TWO), Set.of(MARGIN));
        assertEquals(TWO, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().margin);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateRepeat(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withListeningDateRepeat(DatesTest.nowUtc(), (short) 13);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(13, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().repeat);
        alerts.update(alert.withListeningDateRepeat(alert.listeningDate, (short) 7), Set.of(REPEAT));
        assertEquals(7, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().repeat);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateSnooze(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert().withSnooze((short) 51);
        alert = setId(alert, alerts.addAlert(alert));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert.id).isPresent());
        assertEquals(51, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().snooze);
        alerts.update(alert.withSnooze((short) 77), Set.of(SNOOZE));
        assertEquals(77, alerts.getAlert(TEST_CLIENT_TYPE, alert.id).get().snooze);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void deleteAlert(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.delete(null, 1L));
        setUser(users, TEST_USER_ID);
        Alert alert = createTestAlert();
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isPresent());
        alerts.delete(TEST_CLIENT_TYPE, alertId);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void deleteAlerts(AlertsDao alerts, UsersDao users) {
        assertThrows(NullPointerException.class, () -> alerts.delete((SelectionFilter) null));

        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;

        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/BTC", range).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user1, "ETH/USD", trend).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user2, "DOT/BTC", trend).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPairType(user3, "ETH/BTC", remainder).withServerId(server2));

        assertEquals(0, alerts.delete(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, trend).withTickerOrPair( "ETH/BTC")));
        assertEquals(1, alerts.delete(SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, range).withTickerOrPair( "ETH/BTC")));

        assertEquals(0, alerts.delete(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, range).withTickerOrPair("ETH")));
        assertEquals(1, alerts.delete(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, remainder).withTickerOrPair("ETH")));
        assertEquals(0, alerts.delete(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, remainder).withTickerOrPair("ETH")));
        assertEquals(1, alerts.delete(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null).withTickerOrPair("ETH")));
        assertEquals(0, alerts.delete(SelectionFilter.ofServer(TEST_CLIENT_TYPE, server2, null).withTickerOrPair("ETH")));

        assertEquals(0, alerts.delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, user2, remainder)));
        assertEquals(1, alerts.delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, user2, null)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void deleteAlertsOfUserAndServerId(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;

        alerts.addAlert(createTestAlertWithUserId(user1).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserId(user1).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserId(user1).withServerId(server2));
        alerts.addAlert(createTestAlertWithUserId(user2).withServerId(server1));
        alerts.addAlert(createTestAlertWithUserId(user3).withServerId(server2));

        var filterServer1User1 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user1, null);
        var filterServer1User2 = SelectionFilter.of(TEST_CLIENT_TYPE, server1, user2, null);
        var filterServer2User1 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user1, null);
        var filterServer2User3 = SelectionFilter.of(TEST_CLIENT_TYPE, server2, user3, null);
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1User1, 0, 1000).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer1User2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(filterServer2User3, 0, 1000).size());

        assertEquals(1, alerts.delete(filterServer1User1));
        assertEquals(0, alerts.delete(filterServer1User1));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer1User1, 0, 1000).size());
        assertEquals(2, alerts.delete(filterServer2User1));
        assertEquals(0, alerts.delete(filterServer2User1));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer2User1, 0, 1000).size());
        assertEquals(1, alerts.delete(filterServer1User2));
        assertEquals(0, alerts.delete(filterServer1User2));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer1User2, 0, 1000).size());
        assertEquals(1, alerts.delete(filterServer2User3));
        assertEquals(0, alerts.delete(filterServer2User3));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(filterServer2User3, 0, 1000).size());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void deleteAlertsOfUserAndServerIdAndTickers(AlertsDao alerts, UsersDao users) {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        setUser(users, user1);
        setUser(users, user2);
        setUser(users, user3);
        long server1 = 789L;
        long server2 = 987L;

        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1));
        alerts.addAlert(createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2));


        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH/BTC"), 0, 1000).size());
        assertEquals(2, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1000).size());
        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1000).size());

        assertEquals(1, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH"), 0, 1000).size());


        assertEquals(1, alerts.delete(of(server1, user1, "ETH/BTC")));
        assertEquals(0, alerts.delete(of(server1, user1, "ETH/BTC")));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "ETH/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user1, "BTC"), 0, 1000).size());

        assertEquals(2, alerts.delete(of(server2, user1, "ETH")));
        assertEquals(0, alerts.delete(of(server2, user1, "ETH")));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "ETH/BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "USD"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user1, "BTC"), 0, 1000).size());

        assertEquals(1, alerts.delete(of(server1, user2, "DOT")));
        assertEquals(0, alerts.delete(of(server1, user2, "DOT")));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server1, user2, "DOT"), 0, 1000).size());

        assertEquals(1, alerts.delete(of(server2, user3, "BTC")));
        assertEquals(0, alerts.delete(of(server2, user3, "BTC")));
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "BTC"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH"), 0, 1000).size());
        assertEquals(0, alerts.getAlertsOrderByPairUserIdId(of(server2, user3, "ETH/BTC"), 0, 1000).size());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void matchedAlertBatchUpdates(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);

        assertThrows(NullPointerException.class, () -> alerts.matchedAlertBatchUpdates(DatesTest.nowUtc(), null));
        assertThrows(NullPointerException.class, () -> alerts.matchedAlertBatchUpdates(null, mock()));

        ZonedDateTime now = DatesTest.nowUtc().minusMinutes(7L).truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        ZonedDateTime lastTrigger = now.minusDays(3L);
        assertNotEquals(now, lastTrigger);

        Alert alert1 = createTestAlert().withSnooze((short) 17).withListeningDateLastTriggerMarginRepeat(null, lastTrigger, ONE, (short) 0);
        alert1 = setId(alert1, alerts.addAlert(alert1));
        Alert alert2 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger, TEN, (short) 1);
        alert2 = setId(alert2, alerts.addAlert(alert2));
        Alert alert3 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger, TWO, (short) -1);
        alert3 = setId(alert3, alerts.addAlert(alert3));
        Alert alert4 = createTestAlert().withListeningDateLastTriggerMarginRepeat(null, lastTrigger, TEN, (short) 21);
        alert4 = setId(alert4, alerts.addAlert(alert4));

        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).isPresent());
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().listeningDate);
        assertEquals(lastTrigger, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().lastTrigger);
        assertEquals(ONE, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertEquals(0, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().repeat);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).isPresent());
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().listeningDate);
        assertEquals(lastTrigger, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().lastTrigger);
        assertEquals(TEN, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().margin);
        assertEquals(1, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().repeat);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).isPresent());
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().listeningDate);
        assertEquals(lastTrigger, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().lastTrigger);
        assertEquals(TWO, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().margin);
        assertEquals(-1, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().repeat);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).isPresent());
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().listeningDate);
        assertEquals(lastTrigger, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().lastTrigger);
        assertEquals(TEN, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().margin);
        assertEquals(21, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().repeat);

        var alertIds = List.of(alert2.id, alert3.id, alert4.id);
        alerts.matchedAlertBatchUpdates(now, updater -> alertIds.forEach(updater::batchId));

        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().listeningDate);
        assertEquals(lastTrigger, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().lastTrigger);
        assertEquals(ONE, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertEquals(0, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().repeat);

        assertEquals(now.plusHours(DEFAULT_SNOOZE_HOURS), alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().listeningDate);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().margin);
        assertEquals(0, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().repeat);

        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().listeningDate);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().margin);
        assertEquals(-2, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().repeat);

        assertEquals(now.plusHours(DEFAULT_SNOOZE_HOURS), alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().listeningDate);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().margin);
        assertEquals(21-1, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().repeat);

        long alertId1 = alert1.id;
        long alertId2 = alert2.id;

        alerts.matchedAlertBatchUpdates(now.plusMinutes(3L), updater -> {
            updater.batchId(alertId1);
            updater.batchId(alertId2);
        });

        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().listeningDate);
        assertEquals(now.plusMinutes(3L), alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertEquals(-1, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().repeat);

        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().listeningDate);
        assertEquals(now.plusMinutes(3L), alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().margin);
        assertEquals(-1, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().repeat);

        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().listeningDate);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().margin);
        assertEquals(-2, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().repeat);

        assertEquals(now.plusHours(DEFAULT_SNOOZE_HOURS), alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().listeningDate);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().margin);
        assertEquals(21-1, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().repeat);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void marginAlertBatchUpdates(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);

        assertThrows(NullPointerException.class, () -> alerts.marginAlertBatchUpdates(DatesTest.nowUtc(), null));
        assertThrows(NullPointerException.class, () -> alerts.marginAlertBatchUpdates(null, mock()));

        Alert alert1 = createTestAlert().withLastTriggerMargin(null, ONE);
        alert1 = setId(alert1, alerts.addAlert(alert1));
        Alert alert2 = createTestAlert().withLastTriggerMargin(null, TWO);
        alert2 = setId(alert2, alerts.addAlert(alert2));
        Alert alert3 = createTestAlert().withLastTriggerMargin(null, TEN);
        alert3 = setId(alert3, alerts.addAlert(alert3));
        Alert alert4 = createTestAlert().withLastTriggerMargin(null, TEN);
        alert4 = setId(alert4, alerts.addAlert(alert4));

        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).isPresent());
        assertEquals(ONE, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().lastTrigger);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).isPresent());
        assertEquals(TWO, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().margin);
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().lastTrigger);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).isPresent());
        assertEquals(TEN, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().margin);
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().lastTrigger);
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).isPresent());
        assertEquals(TEN, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().margin);
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().lastTrigger);

        var alertIds = List.of(alert2.id, alert3.id, alert4.id);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(MILLIS); // clear the nanoseconds as sqlite save milliseconds
        alerts.marginAlertBatchUpdates(now, updater -> alertIds.forEach(updater::batchId));

        assertEquals(ONE, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertNull(alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().margin);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().margin);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().margin);
        assertEquals(now, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().lastTrigger);

        long alertId1 = alert1.id;
        alerts.marginAlertBatchUpdates(now.minusMinutes(3L), updater -> updater.batchId(alertId1));
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertEquals(now.minusMinutes(3L), alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert1.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert2.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert3.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(TEST_CLIENT_TYPE, alert4.id).get().margin);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void alertBatchDelete(AlertsDao alerts, UsersDao users) {
        setUser(users, TEST_USER_ID);

        assertThrows(NullPointerException.class, () -> alerts.delete((Consumer<BatchEntry>) null));

        long alertId1 = alerts.addAlert(createTestAlert());
        long alertId2 = alerts.addAlert(createTestAlert());
        long alertId3 = alerts.addAlert(createTestAlert());
        long alertId4 = alerts.addAlert(createTestAlert());

        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId1).isPresent());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId2).isPresent());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId3).isPresent());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId4).isPresent());

        alerts.delete(deleter -> deleter.batchId(alertId2));
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId2).isEmpty());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId1).isPresent());
        assertFalse(alerts.getAlert(TEST_CLIENT_TYPE, alertId2).isPresent());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId3).isPresent());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId4).isPresent());

        alerts.delete(deleter -> {
            deleter.batchId(alertId1);
            deleter.batchId(alertId3);
            deleter.batchId(alertId4);
        });
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId1).isEmpty());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId3).isEmpty());
        assertTrue(alerts.getAlert(TEST_CLIENT_TYPE, alertId4).isEmpty());
        assertFalse(alerts.getAlert(TEST_CLIENT_TYPE, alertId1).isPresent());
        assertFalse(alerts.getAlert(TEST_CLIENT_TYPE, alertId2).isPresent());
        assertFalse(alerts.getAlert(TEST_CLIENT_TYPE, alertId3).isPresent());
        assertFalse(alerts.getAlert(TEST_CLIENT_TYPE, alertId4).isPresent());
    }
}
