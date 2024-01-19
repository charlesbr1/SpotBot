package org.sbot.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static java.math.BigDecimal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.trend;
import static org.sbot.alerts.AlertTest.*;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.alerts.TrendAlert.ONE_HOUR_SECONDS;

class TrendAlertTest {

    static TrendAlert createTestTrendAlert() {
        return new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Test
    void constructor() {
        TrendAlert alert = createTestTrendAlert();
        assertEquals(trend, alert.type);
    }

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // prices not null
        assertThrows(NullPointerException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // positive prices
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.negate(), TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE.negate(), TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // dates not null
        assertThrows(NullPointerException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, null, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, null, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // same dates
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date after to date
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_TO_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void build() {
        TrendAlert alert = createTestTrendAlert();

        assertNotNull(alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));

        assertThrows(NullPointerException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void match() {
        assertThrows(NullPointerException.class, () -> createTestTrendAlert().match(null, null));

        Alert alert = createTestTrendAlert();
        assertEquals(alert, alert.match(Collections.emptyList(), null).alert());
        assertEquals(NOT_MATCHING, alert.match(Collections.emptyList(), null).status());

        // previousCandlestick null
        // priceOnTrend true -> MATCHED
        ZonedDateTime now = ZonedDateTime.now();
        alert = alert.withToDate(now.plusHours(1L)).withFromDate(now); // ensure datesInLimits true
        alert = alert.withFromPrice(TWO).withToPrice(TWO.add(ONE)).withMargin(ZERO);

        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, new BigDecimal("2.5"), ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        Candlestick candlestick2 = new Candlestick(now, now.plusMinutes(1L), ONE, ONE, TWO.add(ONE), ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick2), null).matchingCandlestick());

        Candlestick candlestick3 = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick3), null).status());
        assertNull(alert.match(List.of(candlestick3), null).matchingCandlestick());

        assertEquals(candlestick, alert.match(List.of(candlestick, candlestick3, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick, alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick2, alert.match(List.of(candlestick3, candlestick2), null).matchingCandlestick());

        // test newer candlestick, should ignore candlestick with same closeTime
        candlestick3 = new Candlestick(now, now, TEN, TEN, TEN, TWO.add(ONE));
        assertEquals(candlestick2, alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        candlestick2 = new Candlestick(now, now, TWO, TWO, TEN, TWO);
        assertNull(alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick3, candlestick, candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend true -> MATCHED
        alert = alert.withMargin(ZERO);

        candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        candlestick2 = new Candlestick(now, now.plusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        assertNull(alert.match(List.of(candlestick2), null).matchingCandlestick());
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());

        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick2, candlestick), null).status());
        assertNull(alert.match(List.of(candlestick2, candlestick), null).matchingCandlestick());
        candlestick2 = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(MATCHED, alert.match(List.of(candlestick2, candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick2, candlestick), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend false  margin false -> NOT_MATCHED
        alert = alert.withMargin(ZERO);
        candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = alert.withMargin(new BigDecimal("0.99999999"));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend false  margin true -> MARGIN
        alert = alert.withMargin(ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        alert = alert.withMargin(ZERO);
        candlestick = new Candlestick(now, now, TEN, TEN, TEN, TEN);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = alert.withMargin(BigDecimal.valueOf(8L));
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // previousCandlestick provided
        // priceOnTrend true -> MATCHED
        alert = alert.withMargin(ZERO);
        candlestick = new Candlestick(now, now, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        Candlestick previousCandlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // priceOnTrend false, priceCrossedTrend true -> MATCHED
        alert = alert.withMargin(ZERO);

        candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        candlestick2 = new Candlestick(now, now.plusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        previousCandlestick = new Candlestick(now, now.plusMinutes(3L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        previousCandlestick = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());

        // previousCandlestick provided
        // priceOnTrend false, priceCrossedTrend false  margin true -> MARGIN
        alert = alert.withMargin(BigDecimal.valueOf(3L));
        candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        previousCandlestick = new Candlestick(now, now.plusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());
    }

    @Test
    void currentTrendPrice() {
        ZonedDateTime fromDate = ZonedDateTime.now().minusHours(1L);
        ZonedDateTime inOneHour = fromDate.plusHours(1L);

        assertEquals(ONE.add(ONE_HOUR_SECONDS), TrendAlert.currentTrendPrice(ONE, TWO, fromDate, fromDate));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(ZERO, ZERO, fromDate, fromDate));
        assertEquals(ONE, TrendAlert.currentTrendPrice(ONE, ONE, fromDate, fromDate));
        assertEquals(TWO, TrendAlert.currentTrendPrice(TWO, TWO, fromDate, fromDate));
        assertEquals(ONE, TrendAlert.currentTrendPrice(ZERO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(TWO, TrendAlert.currentTrendPrice(ONE, TWO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ONE, TrendAlert.currentTrendPrice(TWO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(TWO, ZERO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(new BigDecimal("134.1666666666666667"), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(new BigDecimal("134.1666666666666667"), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour));

        assertEquals(new BigDecimal("1.5"), TrendAlert.currentTrendPrice(ONE, TWO, fromDate, fromDate.plusHours(2L)).stripTrailingZeros());
        assertEquals(new BigDecimal("1.25"), TrendAlert.currentTrendPrice(ONE, TWO, fromDate, fromDate.plusHours(4L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.5"), TrendAlert.currentTrendPrice(ONE, TWO, fromDate, fromDate.minusHours(2L)).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(ONE, TWO, fromDate, fromDate.minusHours(1L)).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(ONE, TWO, fromDate.minusHours(1L), fromDate.minusHours(2L)).stripTrailingZeros());

        assertEquals(new BigDecimal("16.8096825403428571"), TrendAlert.currentTrendPrice(new BigDecimal("1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("23.2353968260571429"), TrendAlert.currentTrendPrice(new BigDecimal("-1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(new BigDecimal("1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(new BigDecimal("-1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("1.1572349203750545"), TrendAlert.currentTrendPrice(new BigDecimal("1.00000074"), new BigDecimal("7.008967"), fromDate, fromDate.plusHours(38L).plusMinutes(13L)).stripTrailingZeros());
    }

    @Test
    void secondsBetween() {
        ZonedDateTime fromDate = Dates.parseUTC("01/01/2000-00:03");
        assertThrows(NullPointerException.class, () -> TrendAlert.secondsBetween(null, null));
        assertThrows(NullPointerException.class, () -> TrendAlert.secondsBetween(fromDate, null));
        assertThrows(NullPointerException.class, () -> TrendAlert.secondsBetween(null, fromDate));

        assertEquals(ZERO, TrendAlert.secondsBetween(fromDate, fromDate));

        assertEquals(new BigDecimal(60), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(1L)));
        assertEquals(new BigDecimal(120), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(2L)));
        assertEquals(ONE_HOUR_SECONDS, TrendAlert.secondsBetween(fromDate, fromDate.plusHours(1L)));
        assertEquals(TWO.multiply(ONE_HOUR_SECONDS), TrendAlert.secondsBetween(fromDate, fromDate.plusHours(2L)));
        assertEquals(new BigDecimal(60), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(1L)));
        assertEquals(new BigDecimal(600), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(10L)));
        assertEquals(new BigDecimal(720), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(12L)));
        assertEquals(new BigDecimal(1800), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(30L)));
        assertEquals(new BigDecimal(3660), TrendAlert.secondsBetween(fromDate, fromDate.plusMinutes(61L)));
        assertEquals(new BigDecimal(24 * 3600), TrendAlert.secondsBetween(fromDate, fromDate.plusDays(1L)));
        assertEquals(new BigDecimal(3 * 24 * 3600), TrendAlert.secondsBetween(fromDate, fromDate.plusDays(3L)));
        assertEquals(new BigDecimal(26304 * 3600), TrendAlert.secondsBetween(fromDate, fromDate.plusYears(3L)));

        assertEquals(new BigDecimal(-1800), TrendAlert.secondsBetween(fromDate, fromDate.minusMinutes(30L)));
        assertEquals(new BigDecimal(-600), TrendAlert.secondsBetween(fromDate, fromDate.minusMinutes(10L)));
        assertEquals(new BigDecimal(-132 * 60), TrendAlert.secondsBetween(fromDate, fromDate.minusMinutes(132L)));
    }

    @Test
    void priceDelta() {
        ZonedDateTime fromDate = Dates.parseUTC("01/01/2000-00:03");
        ZonedDateTime inOneHour = fromDate.plusHours(1L);

        assertEquals(ONE_HOUR_SECONDS.stripTrailingZeros(), TrendAlert.secondsBetween(fromDate, inOneHour).stripTrailingZeros());
        assertThrows(NullPointerException.class, () -> TrendAlert.priceDelta(null, null, null, null, null));

        // test prices
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, ZERO, ZERO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(fromDate.plusMinutes(123L), ZERO, ZERO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, ONE, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, ONE.negate(), ONE.negate(), fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, TEN, TEN, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, TEN.negate(), TEN.negate(), fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, new BigDecimal("134.1666666666666667").negate(), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(fromDate.plusMinutes(321L), new BigDecimal("134.1666666666666667").negate(), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(fromDate.minusMinutes(12L), new BigDecimal("-0.1666666666666667").negate(), new BigDecimal("-0.1666666666666667").negate(), fromDate, inOneHour).stripTrailingZeros());

        assertEquals(ONE, TrendAlert.priceDelta(inOneHour, ZERO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(TWO, TrendAlert.priceDelta(inOneHour.plusHours(1L), ZERO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ONE, TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ONE.add(TWO), TrendAlert.priceDelta(inOneHour.plusHours(2L), ONE, TWO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ONE.negate(), TrendAlert.priceDelta(inOneHour, TWO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(TWO.negate(), TrendAlert.priceDelta(inOneHour, TWO, ZERO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(TWO.negate().multiply(new BigDecimal("134.1666666666666667")), TrendAlert.priceDelta(inOneHour, new BigDecimal("134.1666666666666667"), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour));
        assertEquals(TEN.negate().multiply(new BigDecimal("134.1666666666666667")), TrendAlert.priceDelta(inOneHour.plusHours(4L), new BigDecimal("134.1666666666666667"), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour));

        // test dates
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, ZERO, ZERO, fromDate, fromDate).stripTrailingZeros());
        assertEquals(ONE_HOUR_SECONDS.stripTrailingZeros(), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate).stripTrailingZeros());
        assertEquals(TWO.multiply(ONE_HOUR_SECONDS).stripTrailingZeros().negate(), TrendAlert.priceDelta(inOneHour, TWO, ZERO, fromDate, fromDate).stripTrailingZeros());

        assertEquals(new BigDecimal(60).stripTrailingZeros(), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.plusMinutes(1L)).stripTrailingZeros());
        assertEquals(new BigDecimal(-60).stripTrailingZeros(), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.minusMinutes(1L)).stripTrailingZeros());
        assertEquals(new BigDecimal(-120).stripTrailingZeros(), TrendAlert.priceDelta(inOneHour.plusMinutes(60L), ONE, TWO, fromDate, fromDate.minusMinutes(1L)).stripTrailingZeros());
        assertEquals(new BigDecimal(3), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.plusMinutes(20L)).stripTrailingZeros());
        assertEquals(new BigDecimal(9), TrendAlert.priceDelta(inOneHour.plusHours(2L), ONE, TWO, fromDate, fromDate.plusMinutes(20L)).stripTrailingZeros());
        assertEquals(new BigDecimal(-3), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.minusMinutes(20L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.5"), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.plusHours(2L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.25"), TrendAlert.priceDelta(inOneHour.minusMinutes(30L), ONE, TWO, fromDate, fromDate.plusHours(2L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.5"), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.minusHours(2L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.25"), TrendAlert.priceDelta(inOneHour.minusMinutes(30L), ONE, TWO, fromDate, fromDate.minusHours(2L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.3333333333333333"), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.plusHours(3L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.3333333333333333"), TrendAlert.priceDelta(inOneHour, ONE, TWO, fromDate, fromDate.minusHours(3L)).stripTrailingZeros());

        // test both
        assertEquals(new BigDecimal("15.0796825403428571"), TrendAlert.priceDelta(inOneHour, new BigDecimal("1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("24.9653968260571429"), TrendAlert.priceDelta(inOneHour, new BigDecimal("-1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("-24.9653968260571429"), TrendAlert.priceDelta(inOneHour, new BigDecimal("1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("-15.0796825403428571"), TrendAlert.priceDelta(inOneHour, new BigDecimal("-1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.plusMinutes(21)));

        assertEquals(new BigDecimal("-15.0796825403428571"), TrendAlert.priceDelta(inOneHour, new BigDecimal("1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.minusMinutes(21)));
        assertEquals(new BigDecimal("-24.9653968260571429"), TrendAlert.priceDelta(inOneHour, new BigDecimal("-1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.minusMinutes(21)));
        assertEquals(new BigDecimal("24.9653968260571429"), TrendAlert.priceDelta(inOneHour, new BigDecimal("1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.minusMinutes(21)));
        assertEquals(new BigDecimal("15.0796825403428571"), TrendAlert.priceDelta(inOneHour, new BigDecimal("-1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.minusMinutes(21)));

        assertEquals(new BigDecimal("-9.6253428571428571"), TrendAlert.priceDelta(inOneHour, new BigDecimal("6.00887"), new BigDecimal("2.64"), fromDate, fromDate.plusMinutes(21)).stripTrailingZeros());
        assertEquals(new BigDecimal("-96.2534285714285714"), TrendAlert.priceDelta(inOneHour.plusHours(9L), new BigDecimal("6.00887"), new BigDecimal("2.64"), fromDate, fromDate.plusMinutes(21)).stripTrailingZeros());
        assertEquals(new BigDecimal("9.6253428571428571"), TrendAlert.priceDelta(inOneHour, new BigDecimal("6.00887"), new BigDecimal("2.64"), fromDate, fromDate.minusMinutes(21)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.7237685950413223"), TrendAlert.priceDelta(inOneHour, new BigDecimal("3.55"), new BigDecimal("5.0096"), fromDate, fromDate.plusMinutes(121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.7237685950413223"), TrendAlert.priceDelta(inOneHour, new BigDecimal("3.55"), new BigDecimal("5.0096"), fromDate, fromDate.minusMinutes(121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-1.2195867768595041"), TrendAlert.priceDelta(inOneHour, new BigDecimal("4.46"), new BigDecimal("2.0005"), fromDate, fromDate.plusMinutes(121)).stripTrailingZeros());
        assertEquals(new BigDecimal("1.2195867768595041"), TrendAlert.priceDelta(inOneHour, new BigDecimal("4.46"), new BigDecimal("2.0005"), fromDate, fromDate.minusMinutes(121)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.05020385703322"), TrendAlert.priceDelta(inOneHour, new BigDecimal("5.37"), new BigDecimal("13.001823"), fromDate, fromDate.plusMinutes(9121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.05020385703322"), TrendAlert.priceDelta(inOneHour, new BigDecimal("5.37"), new BigDecimal("13.001823"), fromDate, fromDate.minusMinutes(9121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.0281388882797939"), TrendAlert.priceDelta(inOneHour, new BigDecimal("6.28"), new BigDecimal("2.00242"), fromDate, fromDate.plusMinutes(9121)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.0281388882797939"), TrendAlert.priceDelta(inOneHour, new BigDecimal("6.28"), new BigDecimal("2.00242"), fromDate, fromDate.minusMinutes(9121)).stripTrailingZeros());
        assertEquals(new BigDecimal("680.523287926877251"), TrendAlert.priceDelta(inOneHour, new BigDecimal("7.19"), new BigDecimal("1124243.00371"), fromDate, fromDate.plusMinutes(99121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-680.523287926877251"), TrendAlert.priceDelta(inOneHour, new BigDecimal("7.19"), new BigDecimal("1124243.00371"), fromDate, fromDate.minusMinutes(99121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-1361.0465758537545021"), TrendAlert.priceDelta(inOneHour.plusMinutes(60L), new BigDecimal("7.19"), new BigDecimal("1124243.00371"), fromDate, fromDate.minusMinutes(99121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-68.0523287926877251"), TrendAlert.priceDelta(inOneHour.minusMinutes(54L), new BigDecimal("7.19"), new BigDecimal("1124243.00371"), fromDate, fromDate.minusMinutes(99121)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.0048431407269902"), TrendAlert.priceDelta(inOneHour, new BigDecimal("8.001"), new BigDecimal("0.0000508"), fromDate, fromDate.plusMinutes(99121)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.0048431407269902"), TrendAlert.priceDelta(inOneHour, new BigDecimal("8.001"), new BigDecimal("0.0000508"), fromDate, fromDate.minusMinutes(99121)).stripTrailingZeros());
        assertEquals(new BigDecimal("3.0022999743066333"), TrendAlert.priceDelta(inOneHour, new BigDecimal("0.0000000770801"), new BigDecimal("9.0069"), fromDate, fromDate.plusHours(3L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-3.0022999743066333"), TrendAlert.priceDelta(inOneHour, new BigDecimal("0.0000000770801"), new BigDecimal("9.0069"), fromDate, fromDate.minusHours(3L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-3.1023766401168544"), TrendAlert.priceDelta(inOneHour.plusMinutes(2L), new BigDecimal("0.0000000770801"), new BigDecimal("9.0069"), fromDate, fromDate.minusHours(3L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.0000000258573633"), TrendAlert.priceDelta(inOneHour, new BigDecimal("0.00000000750801"), new BigDecimal("0.0000000850801"), fromDate, fromDate.plusHours(3L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.0000000258573633"), TrendAlert.priceDelta(inOneHour, new BigDecimal("0.00000000750801"), new BigDecimal("0.0000000850801"), fromDate, fromDate.minusHours(3L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.1572341803750545"), TrendAlert.priceDelta(inOneHour, new BigDecimal("1.00000074"), new BigDecimal("7.008967"), fromDate, fromDate.plusHours(38L).plusMinutes(13L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.191301586122983"), TrendAlert.priceDelta(inOneHour.plusMinutes(13L), new BigDecimal("1.00000074"), new BigDecimal("7.008967"), fromDate, fromDate.plusHours(38L).plusMinutes(13L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.1572341803750545"), TrendAlert.priceDelta(inOneHour, new BigDecimal("1.00000074"), new BigDecimal("7.008967"), fromDate, fromDate.minusHours(38L).minusMinutes(13L)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.0698138199468085"), TrendAlert.priceDelta(inOneHour, new BigDecimal("6.29"), new BigDecimal("1.04000074"), fromDate, fromDate.plusDays(3L).plusHours(3L).plusMinutes(12L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.0698138199468085"), TrendAlert.priceDelta(inOneHour, new BigDecimal("6.29"), new BigDecimal("1.04000074"), fromDate, fromDate.minusDays(3L).minusHours(3L).minusMinutes(12L)).stripTrailingZeros());
    }

    @Test
    void asMessage() {
        Alert alert = createTestTrendAlert().withId(() -> 456L);
        ZonedDateTime closeTime = Dates.parseUTC("01/01/2000-00:03");
        Candlestick candlestick = new Candlestick(closeTime.minusHours(1L), closeTime, TWO, ONE, TEN, ONE);
        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null));

        String message = alert.asMessage(MATCHED, null);
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("trend"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(alert.fromPrice.toPlainString()));
        assertTrue(message.contains(alert.toPrice.toPlainString()));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));
        assertTrue(message.contains(Dates.formatUTC(alert.toDate)));
        assertFalse(message.contains(alert.message));
        assertFalse(message.contains("threshold"));
        // with candlestick
        assertNotEquals(message, alert.asMessage(MATCHED, candlestick));
        assertTrue(alert.asMessage(MATCHED, candlestick).contains(Dates.formatUTC(closeTime)));
        // with no repeat or last trigger
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains(Dates.formatUTC(alert.lastTrigger)));
        assertFalse(alert.withLastTriggerMarginRepeat(null, alert.margin, (short) 0)
                .asMessage(MATCHED, candlestick).contains("DISABLED"));

        // MARGIN
        assertNotEquals(message, alert.asMessage(MARGIN, null));
        message = alert.asMessage(MARGIN, null);
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("trend"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(alert.fromPrice.toPlainString()));
        assertTrue(message.contains(alert.toPrice.toPlainString()));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));
        assertTrue(message.contains(Dates.formatUTC(alert.toDate)));
        assertFalse(message.contains(alert.message));
        assertTrue(message.contains("threshold"));
        // with candlestick
        assertNotEquals(message, alert.asMessage(MARGIN, candlestick));
        assertTrue(alert.asMessage(MARGIN, candlestick).contains(Dates.formatUTC(closeTime)));
        // with no repeat or last trigger
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains(Dates.formatUTC(alert.lastTrigger)));
        assertFalse(alert.withLastTriggerMarginRepeat(null, alert.margin, (short) 0)
                .asMessage(MARGIN, candlestick).contains("DISABLED"));

        // NOT MATCHING
        message = alert.asMessage(NOT_MATCHING, null);
        assertNotNull(message);
        assertTrue(message.startsWith("Trend Alert set by"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(alert.fromPrice.toPlainString()));
        assertTrue(message.contains(alert.toPrice.toPlainString()));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));
        assertTrue(message.contains(Dates.formatUTC(alert.toDate)));
        assertFalse(message.contains(alert.message));
        assertFalse(message.contains("threshold"));
        // with candlestick
        assertEquals(message, alert.asMessage(NOT_MATCHING, candlestick));
        assertFalse(alert.asMessage(NOT_MATCHING, candlestick).contains(Dates.formatUTC(closeTime)));
        // with no repeat or last trigger
        assertFalse(message.contains("DISABLED"));
        assertTrue(message.contains(Dates.formatUTC(alert.lastTrigger)));
        assertTrue(alert.withLastTriggerMarginRepeat(null, alert.margin, (short) 0)
                .asMessage(NOT_MATCHING, candlestick).contains("DISABLED"));
    }
}