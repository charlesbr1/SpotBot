package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;

class AlertTest {

    static final Type TEST_TYPE = Type.range;
    static final long TEST_USER_ID = 1234L;
    static final long TEST_SERVER_ID = 4321L;
    static final String TEST_EXCHANGE = SUPPORTED_EXCHANGES.get(0);
    static final String TEST_PAIR = "btc/usd";
    static final String TEST_MESSAGE = "test message";
    static final BigDecimal TEST_FROM_PRICE = BigDecimal.valueOf(10L);
    static final BigDecimal TEST_TO_PRICE = BigDecimal.valueOf(20L);
    static final ZonedDateTime TEST_FROM_DATE = Dates.parseUTC("01/01/2000-20:00");
    static final ZonedDateTime TEST_TO_DATE = TEST_FROM_DATE.plusDays(1L);
    static final ZonedDateTime TEST_LAST_TRIGGER = TEST_FROM_DATE.plusHours(1L);
    static final BigDecimal TEST_MARGIN = BigDecimal.TEN;


    private static final class TestAlert extends Alert {

        TestAlert(long id, @NotNull Type type, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message, @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice, @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate, @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
            super(id, type, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
        }
        @NotNull
        @Override
        protected Alert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message, BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate, ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short snooze) {
            return new TestAlert(id, type, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
        }
        @NotNull
        @Override
        public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
            return new MatchingAlert(this, NOT_MATCHING, null);
        }
        @NotNull
        @Override
        protected String asMessage(@NotNull MatchingAlert.MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
            return matchingStatus.name();
        }
    }

    static Alert createTestAlert() {
        return new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Test
    void constructor() {
        Alert alert = createTestAlert();
        assertEquals(NULL_ALERT_ID, alert.id);
        assertEquals(NULL_ALERT_ID, alert.getId());
        assertEquals(TEST_TYPE, alert.type);
        assertEquals(TEST_USER_ID, alert.userId);
        assertEquals(TEST_USER_ID, alert.getUserId());
        assertEquals(TEST_SERVER_ID, alert.serverId);
        assertEquals(TEST_EXCHANGE, alert.exchange);
        assertEquals(TEST_EXCHANGE, alert.getExchange());
        assertNotEquals(TEST_PAIR, alert.pair);
        assertEquals(TEST_PAIR.toUpperCase(), alert.pair);
        assertEquals(TEST_PAIR.toUpperCase(), alert.getPair());
        assertEquals(TEST_MESSAGE, alert.message);
        assertEquals(TEST_MESSAGE, alert.getMessage());
        assertEquals(TEST_FROM_PRICE.stripTrailingZeros(), alert.fromPrice);
        assertEquals(TEST_TO_PRICE.stripTrailingZeros(), alert.toPrice);
        assertEquals(TEST_FROM_DATE, alert.fromDate);
        assertEquals(TEST_TO_DATE, alert.toDate);
        assertEquals(TEST_LAST_TRIGGER, alert.lastTrigger);
        assertEquals(TEST_MARGIN.stripTrailingZeros(), alert.margin);
        assertEquals(DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);
    }

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TestAlert(NULL_ALERT_ID, null, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, "bad exchange", TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, "bad pair", TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, null,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, "null".repeat(10000),
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, ZonedDateTime.now().plusMinutes(1L),
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                BigDecimal.valueOf(-1L), DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, (short) -1, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, (short) -1));
    }

    @Test
    void isPrivate() {
        assertTrue(Alert.isPrivate(Alert.PRIVATE_ALERT));
        assertFalse(Alert.isPrivate(1234L));
    }

    @Test
    void hasRepeat() {
        assertTrue(Alert.hasRepeat(10L));
        assertTrue(Alert.hasRepeat(1L));
        assertFalse(Alert.hasRepeat(0L));
        assertFalse(Alert.hasRepeat(-1L));
    }

    @Test
    void hasMargin() {
        assertFalse(Alert.hasMargin(MARGIN_DISABLED));
        assertFalse(Alert.hasMargin(MARGIN_DISABLED.subtract(BigDecimal.valueOf(1L))));
        assertFalse(Alert.hasMargin(MARGIN_DISABLED.subtract(new BigDecimal("0.000001"))));
        assertTrue(Alert.hasMargin(MARGIN_DISABLED.add(BigDecimal.valueOf(1L))));
        assertTrue(Alert.hasMargin(MARGIN_DISABLED.add(new BigDecimal("0.000001"))));
    }

    @Test
    void isSnoozeOver() {
        Alert alert = createTestAlert();
        assertFalse(alert.isSnoozeOver(0L));
        assertTrue(new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS).isSnoozeOver(0L));
        assertFalse(alert.isSnoozeOver(TEST_LAST_TRIGGER.toEpochSecond()));
        assertFalse(alert.isSnoozeOver(TEST_LAST_TRIGGER.toEpochSecond() - 10000 + (3600L * alert.snooze)));
        assertFalse(alert.isSnoozeOver(TEST_LAST_TRIGGER.toEpochSecond() - 1 + (3600L * alert.snooze)));
        assertTrue(alert.isSnoozeOver(TEST_LAST_TRIGGER.toEpochSecond() + (3600L * alert.snooze)));
        assertTrue(alert.isSnoozeOver(TEST_LAST_TRIGGER.toEpochSecond() + 1 + (3600L * alert.snooze)));
        assertTrue(alert.isSnoozeOver(TEST_LAST_TRIGGER.toEpochSecond() + 10000 + (3600L * alert.snooze)));
    }


    @Test
    void getTicker2() {
        Alert alert = createTestAlert();
        assertEquals("USD", alert.getTicker2());
        assertEquals("TEST", new TestAlert(NULL_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, "PAIR/TEST", TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS).getTicker2());
    }

    @Test
    void withId() {
        Alert alert = createTestAlert().withId(() -> 333L);
        assertEquals(333L, alert.getId());
        assertThrows(IllegalStateException.class, () -> alert.withId(() -> 123L));
    }

    @Test
    void withServerId() {
        Alert alert = createTestAlert();
        assertEquals(333L, alert.withServerId(333L).serverId);
    }

    @Test
    void withFromPrice() {
        Alert alert = createTestAlert();
        BigDecimal fromPrice = BigDecimal.valueOf(1234L);
        assertEquals(fromPrice, alert.withFromPrice(fromPrice).fromPrice);
    }

    @Test
    void withToPrice() {
        Alert alert = createTestAlert();
        BigDecimal toPrice = BigDecimal.valueOf(1234L);
        assertEquals(toPrice, alert.withToPrice(toPrice).toPrice);
    }

    @Test
    void withFromDate() {
        Alert alert = createTestAlert();
        ZonedDateTime fromDate = ZonedDateTime.now();
        assertEquals(fromDate, alert.withFromDate(fromDate).fromDate);
        assertNull(alert.withFromDate(null).fromDate);
    }

    @Test
    void withToDate() {
        Alert alert = createTestAlert();
        ZonedDateTime toDate = ZonedDateTime.now();
        assertEquals(toDate, alert.withToDate(toDate).toDate);
        assertNull(alert.withToDate(null).toDate);
    }

    @Test
    void withMessage() {
        Alert alert = createTestAlert();
        assertEquals("new message", alert.withMessage("new message").getMessage());
        assertThrows(IllegalArgumentException.class, () -> alert.withMessage("abcd".repeat(10000)));
    }

    @Test
    void withMargin() {
        Alert alert = createTestAlert();
        assertEquals(BigDecimal.ZERO, alert.withMargin(BigDecimal.ZERO).margin);
        BigDecimal margin = BigDecimal.valueOf(1234L);
        assertEquals(margin, alert.withMargin(margin).margin);
        assertThrows(IllegalArgumentException.class, () -> alert.withMargin(BigDecimal.valueOf(-1L)));
    }

    @Test
    void withLastTriggerRepeatSnooze() {
        Alert alert = createTestAlert();
        assertNull(alert.withLastTriggerRepeatSnooze(null, (short) 0, (short) 0).lastTrigger);
        assertEquals(0, alert.withLastTriggerRepeatSnooze(null, (short) 0, (short) 0).repeat);
        assertEquals(0, alert.withLastTriggerRepeatSnooze(null, (short) 0, (short) 0).snooze);

        ZonedDateTime lastTrigger = ZonedDateTime.now().minusMinutes(1L);
        short repeat = 1;
        short snooze = 2;
        assertEquals(lastTrigger, alert.withLastTriggerRepeatSnooze(lastTrigger, repeat, snooze).lastTrigger);
        assertEquals(repeat, alert.withLastTriggerRepeatSnooze(lastTrigger, repeat, snooze).repeat);
        assertEquals(snooze, alert.withLastTriggerRepeatSnooze(lastTrigger, repeat, snooze).snooze);
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerRepeatSnooze(ZonedDateTime.now().plusMinutes(1), repeat, snooze));
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerRepeatSnooze(lastTrigger, (short) -1, snooze));
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerRepeatSnooze(lastTrigger, repeat, (short) -1));
    }

    @Test
    void withLastTriggerMarginRepeat() {
        Alert alert = createTestAlert();
        assertNull(alert.withLastTriggerMarginRepeat(null, BigDecimal.ZERO, (short) 0).lastTrigger);
        assertThrows(NullPointerException.class, () -> alert.withLastTriggerMarginRepeat(null, null, (short) 0));
        assertEquals(0, alert.withLastTriggerMarginRepeat(null, BigDecimal.ZERO, (short) 0).repeat);

        ZonedDateTime lastTrigger = ZonedDateTime.now().minusMinutes(1L);
        BigDecimal margin = BigDecimal.valueOf(120L).stripTrailingZeros();
        short repeat = 1;
        assertEquals(lastTrigger, alert.withLastTriggerMarginRepeat(lastTrigger, margin, repeat).lastTrigger);
        assertEquals(margin, alert.withLastTriggerMarginRepeat(lastTrigger, margin, repeat).margin);
        assertEquals(repeat, alert.withLastTriggerMarginRepeat(lastTrigger, margin, repeat).repeat);
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerMarginRepeat(ZonedDateTime.now().plusMinutes(1), margin, repeat));
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerMarginRepeat(lastTrigger, BigDecimal.valueOf(-1L), repeat));
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerMarginRepeat(lastTrigger, margin, (short) -1));
    }

    @Test
    void isNewerCandleStick() {
        ZonedDateTime closeTime = ZonedDateTime.now();
        Candlestick candlestick = new Candlestick(ZonedDateTime.now().minusMinutes(1L), closeTime,
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TEN);

        assertThrows(NullPointerException.class, () -> Alert.isNewerCandleStick(null, candlestick));
        assertTrue(Alert.isNewerCandleStick(candlestick, null));
        assertFalse(Alert.isNewerCandleStick(candlestick, candlestick));

        Candlestick olderCandlestick = new Candlestick(candlestick.openTime(), closeTime.minusMinutes(1L),
                candlestick.open(), candlestick.close(), candlestick.high(), candlestick.low());
        assertTrue(Alert.isNewerCandleStick(candlestick, olderCandlestick));

        Candlestick newerCandlestick = new Candlestick(candlestick.openTime(), closeTime.plusMinutes(1L),
                candlestick.open(), candlestick.close(), candlestick.high(), candlestick.low());
        assertFalse(Alert.isNewerCandleStick(candlestick, newerCandlestick));
    }

    @Test
    void onRaiseMessage() {
        Alert alert = createTestAlert();
        assertEquals(NOT_MATCHING.name(), alert.onRaiseMessage(NOT_MATCHING, null));
        assertEquals(MATCHED.name(), alert.onRaiseMessage(MATCHED, null));
        assertEquals(MARGIN.name(), alert.onRaiseMessage(MARGIN, null));
    }

    @Test
    void descriptionMessage() {
        Alert alert = createTestAlert();
        assertEquals(NOT_MATCHING.name(), alert.descriptionMessage());
    }

    @Test
    void testEquals() {
        Alert alert = createTestAlert();
        Alert otherAlert = alert.withId(() -> 1L);
        assertEquals(alert, alert);
        assertEquals(otherAlert, otherAlert);
        assertNotEquals(alert, otherAlert);
    }

    @Test
    void testHashCode() {
        Alert alert = createTestAlert();
        Alert otherAlert = alert.withId(() -> 1L);
        assertEquals(alert.hashCode(), alert.hashCode());
        assertEquals(otherAlert.hashCode(), otherAlert.hashCode());
        assertNotEquals(alert.hashCode(), otherAlert.hashCode());
    }

    @Test
    void testToString() {
        Alert alert = createTestAlert();
        assertEquals(alert.descriptionMessage(), alert.toString());
    }
}