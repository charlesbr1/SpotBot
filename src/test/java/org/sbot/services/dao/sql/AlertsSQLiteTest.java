package org.sbot.services.dao.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.AlertsDao.UpdateField;
import org.sbot.services.dao.AlertsDaoTest;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.Fields.*;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.TICKER_OR_PAIR_ARGUMENT;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.fakeJdbi;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class AlertsSQLiteTest extends AlertsDaoTest {

    public static Stream<Arguments> provideDao() {
        return provideDao(null);
    }

    public static Stream<Arguments> provideDao(Consumer<JDBIRepository> usersDaoConstructor) {
        var initConstructors = new ArrayList<Consumer<JDBIRepository>>();
        Optional.ofNullable(usersDaoConstructor).ifPresent(initConstructors::add);
        initConstructors.add(AlertsSQLite::new);
        UsersSQLite[] userDao = new UsersSQLite[1];
        return Stream.of(Arguments.of(loadTransactionalDao((dao, handle) -> {
            try {
                var field = AbstractJDBI.class.getDeclaredField("transactionHandler");
                field.setAccessible(true);
                JDBITransactionHandler txHandler = (JDBITransactionHandler) field.get(dao);
                userDao[0] = new UsersSQLite(dao, txHandler);
                userDao[0].setupTable(handle);
                handle.execute(UsersSQLite.SQL.CREATE_TABLE);
                dao.setupTable(handle);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, (jdbi, txHandler) -> new AlertsSQLite(jdbi, txHandler, new AtomicLong(1)), initConstructors), userDao[0]));
    }

    @Test
    void withHandler() {
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(null));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(null, mock(), mock()));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(mock(), null, null));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(mock(), mock(), null));
        assertThrows(NullPointerException.class, () -> new AlertsSQLite(fakeJdbi(mock()), mock(), mock()).withHandler(null));
        assertDoesNotThrow(() -> new AlertsSQLite(fakeJdbi(mock()), mock(), mock()).withHandler(mock()));
    }

    @Test
    void selectionFilter() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.searchFilter(null));

        long serverId = 123L;
        long userId = 654L;

        var selection = SelectionFilter.ofServer(serverId, null);
        var map = AlertsSQLite.selectionFilter(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertNull(map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertNull(map.get(TYPE));

        selection = SelectionFilter.ofUser(userId, null);
        map = AlertsSQLite.selectionFilter(selection);
        assertNull(map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertNull(map.get(TYPE));

        selection = SelectionFilter.of(serverId, userId, null);
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertNull(map.get(TYPE));

        selection = SelectionFilter.of(serverId, userId, range);
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertEquals(range.name(), map.get(TYPE));

        selection = SelectionFilter.of(serverId, userId, remainder).withTickerOrPair("DOT/FTT");
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertEquals("DOT/FTT", map.get(TICKER_OR_PAIR_ARGUMENT));
        assertEquals(remainder.name(), map.get(TYPE));
    }


    @Test
    void searchFilter() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.searchFilter(null));
        assertEquals("1 = 1", AlertsSQLite.searchFilter(emptyMap()));

        long serverId = 123L;
        long userId = 654L;

        var selection = SelectionFilter.ofServer(serverId, null);
        var map = AlertsSQLite.selectionFilter(selection);
        assertEquals(SERVER_ID + "=:" + SERVER_ID, AlertsSQLite.searchFilter(map));

        selection = SelectionFilter.ofUser(userId, null);
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(USER_ID + "=:" + USER_ID, AlertsSQLite.searchFilter(map));

        selection = SelectionFilter.of(serverId, userId, null);
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(SERVER_ID + "=:" + SERVER_ID + " AND " + USER_ID + "=:" + USER_ID, AlertsSQLite.searchFilter(map));

        selection = SelectionFilter.of(serverId, userId, trend);
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(SERVER_ID + "=:" + SERVER_ID + " AND " + USER_ID + "=:" + USER_ID + " AND " + TYPE + " LIKE :" + TYPE, AlertsSQLite.searchFilter(map));

        selection = SelectionFilter.of(serverId, userId, remainder).withTickerOrPair("SOL/EUR");
        map = AlertsSQLite.selectionFilter(selection);
        assertEquals(SERVER_ID + "=:" + SERVER_ID + " AND " + USER_ID + "=:" + USER_ID + " AND " + TYPE + " LIKE :" + TYPE + " AND " + PAIR + " LIKE '%'||:" + TICKER_OR_PAIR_ARGUMENT + "||'%'", AlertsSQLite.searchFilter(map));
    }

    @Test
    void updateParameters() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(null));

        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.SERVER_ID, null)));
        assertDoesNotThrow(() -> AlertsSQLite.updateParameters(singletonMap(UpdateField.LISTENING_DATE, null)));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.FROM_PRICE, null)));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.TO_PRICE, null)));
        assertDoesNotThrow(() -> AlertsSQLite.updateParameters(singletonMap(UpdateField.FROM_DATE, null)));
        assertDoesNotThrow(() -> AlertsSQLite.updateParameters(singletonMap(UpdateField.TO_DATE, null)));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.MESSAGE, null)));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.MARGIN, null)));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.REPEAT, null)));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParameters(singletonMap(UpdateField.SNOOZE, null)));

        long serverId = 1123L;
        var parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.SERVER_ID, serverId));
        assertEquals(Map.of(SERVER_ID, serverId), parameters);
        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(ChronoUnit.MILLIS);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.LISTENING_DATE, date));
        assertEquals(Map.of(LISTENING_DATE, date), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.FROM_PRICE, new BigDecimal("34.3434")));
        assertEquals(Map.of(FROM_PRICE, new BigDecimal("34.3434")), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.TO_PRICE, new BigDecimal("134.3434")));
        assertEquals(Map.of(TO_PRICE, new BigDecimal("134.3434")), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.FROM_DATE, date));
        assertEquals(Map.of(FROM_DATE, date), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.TO_DATE, date.plusHours(3L)));
        assertEquals(Map.of(TO_DATE, date.plusHours(3L)), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.MESSAGE, "new messs test"));
        assertEquals(Map.of(MESSAGE, "new messs test"), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.MARGIN, new BigDecimal("4")));
        assertEquals(Map.of(MARGIN, new BigDecimal("4")), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.REPEAT, (short) 2));
        assertEquals(Map.of(REPEAT, (short) 2), parameters);
        parameters = AlertsSQLite.updateParameters(Map.of(UpdateField.SNOOZE, (short) 23));
        assertEquals(Map.of(SNOOZE, (short) 23), parameters);

        parameters = AlertsSQLite.updateParameters(Map.of(
                UpdateField.SNOOZE, (short) 223,
                UpdateField.FROM_PRICE, new BigDecimal("99"),
                UpdateField.TO_PRICE, new BigDecimal("199"),
                UpdateField.MESSAGE, "updated",
                UpdateField.SERVER_ID, 999L,
                UpdateField.TO_DATE, date.plusHours(31L),
                UpdateField.FROM_DATE, date.minusMinutes(7L)));
        assertEquals(Map.of(SNOOZE, (short) 223,
                FROM_PRICE, new BigDecimal("99"),
                TO_PRICE, new BigDecimal("199"),
                MESSAGE, "updated",
                SERVER_ID, 999L,
                TO_DATE, date.plusHours(31L),
                FROM_DATE, date.minusMinutes(7L)), parameters);

        parameters = AlertsSQLite.updateParameters(Map.of(
                UpdateField.LISTENING_DATE, date.plusMinutes(666L),
                UpdateField.REPEAT, (short) 3,
                UpdateField.SNOOZE, (short) 54,
                UpdateField.FROM_PRICE, new BigDecimal("1.2"),
                UpdateField.MESSAGE, "updated again"));
        assertEquals(Map.of(LISTENING_DATE, date.plusMinutes(666L),
                REPEAT, (short) 3,
                SNOOZE, (short) 54,
                FROM_PRICE, new BigDecimal("1.2"),
                MESSAGE, "updated again"), parameters);
    }

    @Test
    void updateQuery() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateQuery(null));
        assertThrows(IllegalArgumentException.class, () -> AlertsSQLite.updateQuery(emptyList()));

        assertEquals("field=:field", AlertsSQLite.updateQuery(List.of("field")));
        assertEquals("field=:field,other=:other", AlertsSQLite.updateQuery(List.of("field", "other")));
    }
}