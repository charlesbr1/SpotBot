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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;
import static org.sbot.entities.alerts.AlertTest.createTestAlert;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.Fields.*;
import static org.sbot.services.dao.sql.AlertsSQLite.SQL.TICKER_OR_PAIR_ARGUMENT;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.fakeJdbi;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.loadTransactionalDao;

class AlertsSQLiteTest extends AlertsDaoTest {

    public static Stream<Arguments> provideDao() {
        return provideDao(null);
    }

    public static Stream<Arguments> provideDao(Consumer<JDBIRepository> settingsDaoConstructor) {
        var initConstructors = new ArrayList<Consumer<JDBIRepository>>();
        Optional.ofNullable(settingsDaoConstructor).ifPresent(initConstructors::add);
        initConstructors.add(AlertsSQLite::new);
        UserSettingsSQLite[] settingsDao = new UserSettingsSQLite[1];
        return Stream.of(Arguments.of(loadTransactionalDao((dao, handle) -> {
            try {
                var field = AbstractJDBI.class.getDeclaredField("transactionHandler");
                field.setAccessible(true);
                JDBITransactionHandler txHandler = (JDBITransactionHandler) field.get(dao);
                settingsDao[0] = new UserSettingsSQLite(dao, txHandler);
                settingsDao[0].setupTable(handle);
                handle.execute(UserSettingsSQLite.SQL.CREATE_TABLE);
                dao.setupTable(handle);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, (jdbi, txHandler) -> new AlertsSQLite(jdbi, txHandler, new AtomicLong(1)), initConstructors), settingsDao[0]));
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
    void parametersOf() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.parametersOf(null));

        long serverId = 123L;
        long userId = 654L;

        var selection = SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null);
        var map = AlertsSQLite.parametersOf(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertNull(map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertNull(map.get(TYPE));

        selection = SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null);
        map = AlertsSQLite.parametersOf(selection);
        assertNull(map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertNull(map.get(TYPE));

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null);
        map = AlertsSQLite.parametersOf(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertNull(map.get(TYPE));

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, range);
        map = AlertsSQLite.parametersOf(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertNull(map.get(TICKER_OR_PAIR_ARGUMENT));
        assertEquals(range.name(), map.get(TYPE));

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder).withTickerOrPair("DOT/FTT");
        map = AlertsSQLite.parametersOf(selection);
        assertEquals(serverId, map.get(SERVER_ID));
        assertEquals(userId, map.get(USER_ID));
        assertEquals("DOT/FTT", map.get(TICKER_OR_PAIR_ARGUMENT));
        assertEquals(remainder.name(), map.get(TYPE));
    }


    @Test
    void asSearchFilter() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.asSearchFilter(null));
        assertEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE, AlertsSQLite.asSearchFilter(new SelectionFilter(TEST_CLIENT_TYPE, null, null, null, null)).toString());

        long serverId = 123L;
        long userId = 654L;

        var selection = SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null);
        assertInstanceOf(CharSequence.class, AlertsSQLite.asSearchFilter(selection));
        assertNotEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE + " AND " + SERVER_ID + "=:" + SERVER_ID, AlertsSQLite.asSearchFilter(selection));
        assertEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE + " AND " + SERVER_ID + "=:" + SERVER_ID, AlertsSQLite.asSearchFilter(selection).toString());

        selection = SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null);
        assertEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE + " AND " + USER_ID + "=:" + USER_ID, AlertsSQLite.asSearchFilter(selection).toString());

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null);
        assertEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE + " AND " + SERVER_ID + "=:" + SERVER_ID + " AND " + USER_ID + "=:" + USER_ID, AlertsSQLite.asSearchFilter(selection).toString());

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, trend);
        assertEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE + " AND " + SERVER_ID + "=:" + SERVER_ID + " AND " + USER_ID + "=:" + USER_ID + " AND " + TYPE + "=:" + TYPE, AlertsSQLite.asSearchFilter(selection).toString());

        selection = SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder).withTickerOrPair("SOL/EUR");
        assertEquals(CLIENT_TYPE + "=:" + CLIENT_TYPE + " AND " + SERVER_ID + "=:" + SERVER_ID + " AND " + USER_ID + "=:" + USER_ID + " AND " + TYPE + "=:" + TYPE + " AND " + PAIR + " LIKE '%'||:" + TICKER_OR_PAIR_ARGUMENT + "||'%'", AlertsSQLite.asSearchFilter(selection).toString());
    }

    @Test
    void updateParametersOf() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParametersOf(null, null));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParametersOf(null, mock()));
        assertThrows(NullPointerException.class, () -> AlertsSQLite.updateParametersOf(mock(), null));

        long serverId = 1123L;
        var alert = createTestAlert();
        var parameters = AlertsSQLite.updateParametersOf(alert.withServerId(serverId), EnumSet.of(UpdateField.SERVER_ID));
        assertEquals(Map.of(SERVER_ID, serverId), parameters);
        ZonedDateTime date = DatesTest.nowUtc().truncatedTo(ChronoUnit.MILLIS);
        parameters = AlertsSQLite.updateParametersOf(alert.withListeningDateRepeat(date, alert.repeat), EnumSet.of(UpdateField.LISTENING_DATE));
        assertEquals(Map.of(LISTENING_DATE, date), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withFromPrice(new BigDecimal("34.3434")), EnumSet.of(UpdateField.FROM_PRICE));
        assertEquals(Map.of(FROM_PRICE, new BigDecimal("34.3434")), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withToPrice(new BigDecimal("134.3434")), Set.of(UpdateField.TO_PRICE));
        assertEquals(Map.of(TO_PRICE, new BigDecimal("134.3434")), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withFromDate(date), EnumSet.of(UpdateField.FROM_DATE));
        assertEquals(Map.of(FROM_DATE, date), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withToDate(date.plusHours(3L)), Set.of(UpdateField.TO_DATE));
        assertEquals(Map.of(TO_DATE, date.plusHours(3L)), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withMessage("new messs test"), EnumSet.of(UpdateField.MESSAGE));
        assertEquals(Map.of(MESSAGE, "new messs test"), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withMargin(new BigDecimal("4")), Set.of(UpdateField.MARGIN));
        assertEquals(Map.of(MARGIN, new BigDecimal("4")), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withListeningDateRepeat(alert.listeningDate, (short) 2), EnumSet.of(UpdateField.REPEAT));
        assertEquals(Map.of(REPEAT, (short) 2), parameters);
        parameters = AlertsSQLite.updateParametersOf(alert.withSnooze((short) 23), Set.of(UpdateField.SNOOZE));
        assertEquals(Map.of(SNOOZE, (short) 23), parameters);

        parameters = AlertsSQLite.updateParametersOf(alert
                        .withSnooze((short) 223)
                        .withFromPrice(new BigDecimal("99"))
                        .withToPrice(new BigDecimal("199"))
                        .withMessage("updated")
                        .withServerId(999L)
                        .withToDate(date.plusHours(31L))
                        .withFromDate(date.minusMinutes(7L)),
                EnumSet.of(
                        UpdateField.SNOOZE,
                        UpdateField.FROM_PRICE,
                        UpdateField.TO_PRICE,
                        UpdateField.MESSAGE,
                        UpdateField.SERVER_ID,
                        UpdateField.TO_DATE,
                        UpdateField.FROM_DATE));
        assertEquals(Map.of(SNOOZE, (short) 223,
                FROM_PRICE, new BigDecimal("99"),
                TO_PRICE, new BigDecimal("199"),
                MESSAGE, "updated",
                SERVER_ID, 999L,
                TO_DATE, date.plusHours(31L),
                FROM_DATE, date.minusMinutes(7L)), parameters);

        parameters = AlertsSQLite.updateParametersOf(alert
                        .withListeningDateRepeat(date.plusMinutes(666L), (short) 3)
                        .withSnooze((short) 54)
                        .withFromPrice(new BigDecimal("1.2"))
                        .withMessage("updated again"),
                Set.of(
                        UpdateField.LISTENING_DATE,
                        UpdateField.REPEAT,
                        UpdateField.SNOOZE,
                        UpdateField.FROM_PRICE,
                        UpdateField.MESSAGE));
        assertEquals(Map.of(LISTENING_DATE, date.plusMinutes(666L),
                REPEAT, (short) 3,
                SNOOZE, (short) 54,
                FROM_PRICE, new BigDecimal("1.2"),
                MESSAGE, "updated again"), parameters);
    }

    @Test
    void asUpdateQuery() {
        assertThrows(NullPointerException.class, () -> AlertsSQLite.asUpdateQuery(null));
        assertThrows(IllegalArgumentException.class, () -> AlertsSQLite.asUpdateQuery(emptyList()));

        assertInstanceOf(CharSequence.class, AlertsSQLite.asUpdateQuery(List.of("field")));
        assertNotEquals("field=:field", AlertsSQLite.asUpdateQuery(List.of("field")));
        assertEquals("field=:field", AlertsSQLite.asUpdateQuery(List.of("field")).toString());
        assertEquals("field=:field,other=:other", AlertsSQLite.asUpdateQuery(List.of("field", "other")).toString());
    }
}