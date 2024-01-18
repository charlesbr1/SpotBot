package org.sbot.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.range;
import static org.sbot.alerts.AlertTest.*;
import static org.sbot.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;

class RangeAlertTest {

    static RangeAlert createTestRangeAlert() {
        return new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Test
    void constructor() {
        RangeAlert alert = createTestRangeAlert();
        assertEquals(range, alert.type);
    }

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertDoesNotThrow(() -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, null, null, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertDoesNotThrow(() -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_FROM_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.add(BigDecimal.ONE), TEST_FROM_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.negate(), TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE.negate(), TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // check from et to date
    }

    @Test
    void build() {
        RangeAlert alert = createTestRangeAlert();

        assertNotNull(alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));

        assertThrows(NullPointerException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void match() {
    }

    @Test
    void datesInLimits() {
        ZonedDateTime closeTime = ZonedDateTime.now();
        Candlestick candlestick = new Candlestick(ZonedDateTime.now().minusMinutes(1L), closeTime,
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TEN);

        assertTrue(RangeAlert.datesInLimits(candlestick, null, null));

        // close is before from date
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime, null));
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime.minusMinutes(1L), null));
        assertFalse(RangeAlert.datesInLimits(candlestick, closeTime.plusMinutes(1L), null));

        // close is after to date
        assertTrue(RangeAlert.datesInLimits(candlestick, null, closeTime));
        assertTrue(RangeAlert.datesInLimits(candlestick, null, closeTime.plusMinutes(1L)));
        assertFalse(RangeAlert.datesInLimits(candlestick, null, ZonedDateTime.now().minusMinutes(1)));

        // close is between from and to date
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime, closeTime));
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime.minusMinutes(1L), closeTime.plusMinutes(1L)));
    }

    @Test
    void priceInRange() {

        Candlestick candlestick = new Candlestick(ZonedDateTime.now(), (ZonedDateTime.now().plusHours(1L)),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TEN);

        // Scénario 1: Les prix sont dans la plage avec une marge suffisante
        assertTrue(RangeAlert.priceInRange(
                candlestick,
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                new BigDecimal("5.00")
        ));

        // Scénario 2: Le prix le plus bas est en dehors de la plage avec une marge suffisante
        assertFalse(RangeAlert.priceInRange(
                candlestick,
                new BigDecimal("60.00"),
                new BigDecimal("100.00"),
                new BigDecimal("5.00")
        ));

        // Scénario 3: Le prix le plus haut est en dehors de la plage avec une marge suffisante
        assertFalse(RangeAlert.priceInRange(
                candlestick,
                new BigDecimal("50.00"),
                new BigDecimal("90.00"),
                new BigDecimal("5.00")
        ));

        // Scénario 4: Les prix sont en dehors de la plage avec une marge insuffisante
        assertFalse(RangeAlert.priceInRange(
                candlestick,
                new BigDecimal("60.00"),
                new BigDecimal("90.00"),
                new BigDecimal("5.00")
        ));
    }

    @Test
    void priceCrossedRange() {
    }

    @Test
    void asMessage() {
    }
}