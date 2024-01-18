package org.sbot.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static java.math.BigDecimal.*;
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
        // prices not null
        assertThrows(NullPointerException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // null dates ok
        assertDoesNotThrow(() -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, null, null, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // same from and to price
        assertDoesNotThrow(() -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_FROM_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from price above to price
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.add(ONE), TEST_FROM_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // positive prices
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.negate(), TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE.negate(), TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date after to date
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_TO_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));

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
                TWO, TWO, ONE, TEN);

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
        BigDecimal low = new BigDecimal(30L);
        BigDecimal high = new BigDecimal(40L);
        BigDecimal openClose = new BigDecimal(35L);

        Candlestick candlestick = new Candlestick(
                ZonedDateTime.now(), ZonedDateTime.now().plusHours(1L),
                openClose, openClose,
                high, low);

        assertThrows(NullPointerException.class, () -> RangeAlert.priceInRange(null, null, null, null));
        assertThrows(NullPointerException.class, () -> RangeAlert.priceInRange(candlestick, low, high, null));
        assertThrows(NullPointerException.class, () -> RangeAlert.priceInRange(candlestick, low, null, ZERO));
        assertThrows(NullPointerException.class, () -> RangeAlert.priceInRange(candlestick, null, high, ZERO));
        assertThrows(NullPointerException.class, () -> RangeAlert.priceInRange(null, low, high, ZERO));

        // prices in range
        BigDecimal fromPrice = low;
        BigDecimal toPrice = high;
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // prices inner range
        fromPrice = low.subtract(ONE);
        toPrice = high.add(ONE);
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // prices over range
        fromPrice = low.add(ONE);
        toPrice = high.subtract(ONE);
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // low price out of range
        fromPrice = low.add(ONE);
        toPrice = high.add(ONE);
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // high price out of range
        fromPrice = low.subtract(ONE);
        toPrice = high.subtract(ONE);
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // low and high price below range
        fromPrice = high.add(TEN);
        toPrice = fromPrice.add(TEN);
        assertFalse(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertFalse(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(9L)));
        // low and high price below range with enough margin
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(10L)));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // low and high price above range
        toPrice = low.subtract(TEN);
        fromPrice = toPrice.subtract(TEN);
        assertFalse(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertFalse(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(9L)));
        // low and high price above range with enough margin
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(10L)));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, BigDecimal.valueOf(1000L)));

        // line range
        fromPrice = low;
        toPrice = fromPrice;
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        fromPrice = low.add(ONE); // in range
        toPrice = fromPrice;
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        fromPrice = low.subtract(ONE); // above range
        toPrice = fromPrice;
        assertFalse(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ONE));

        toPrice = high;
        fromPrice = toPrice;
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        toPrice = high.subtract(ONE); // in range
        fromPrice = toPrice;
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        toPrice = high.add(ONE); // below range
        fromPrice = toPrice;
        assertFalse(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ZERO));
        assertTrue(RangeAlert.priceInRange(candlestick, fromPrice, toPrice, ONE));
    }

    @Test
    void priceCrossedRange() {

        BigDecimal fromPrice = new BigDecimal(30L);
        BigDecimal toPrice = new BigDecimal(35L);

        Function<BigDecimal, Candlestick> newCandle = price -> new Candlestick(
                ZonedDateTime.now(), ZonedDateTime.now().plusHours(1L),
                price.add(ONE), price.add(ONE),
                price.add(TEN), price);

        assertFalse(RangeAlert.priceCrossedRange(null, null, null, null));

        // prices are above the range
        Candlestick current = newCandle.apply(toPrice.add(TEN));
        Candlestick previous = newCandle.apply(toPrice.add(TWO));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));

        // prices are above the range since previous candlestick
        current = newCandle.apply(toPrice.add(TEN));
        previous = newCandle.apply(toPrice);
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));

        // prices crossed up the range since previous candlestick
        current = newCandle.apply(toPrice.add(TEN));
        previous = newCandle.apply(toPrice.subtract(ONE));
        assertTrue(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, null));

        newCandle = price -> new Candlestick(
                ZonedDateTime.now(), ZonedDateTime.now().plusHours(1L),
                price.add(ONE), price.add(ONE),
                price, price.subtract(TEN));

        // prices are below the range
        current = newCandle.apply(fromPrice.subtract(TEN));
        previous = newCandle.apply(fromPrice.subtract(TWO));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));

        // prices are below the range since previous candlestick
        current = newCandle.apply(fromPrice.subtract(TEN));
        previous = newCandle.apply(fromPrice);
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));

        // prices crossed down the range since previous candlestick
        current = newCandle.apply(fromPrice.subtract(TEN));
        previous = newCandle.apply(fromPrice.add(ONE));
        assertTrue(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, null));
    }

    @Test
    void asMessage() {
    }
}