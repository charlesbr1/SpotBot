package org.sbot.entities.alerts;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.Candlestick;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static java.math.BigDecimal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.Dates.UTC;
import static org.sbot.utils.DatesTest.nowUtc;

class TrendAlertTest {

    private static final BigDecimal ONE_HOUR_SECONDS = new BigDecimal(Duration.ofHours(1L).toSeconds());

    static TrendAlert createTestTrendAlert() {
        return new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
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
        assertDoesNotThrow(() -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertDoesNotThrow(() -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), null, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // prices not null
        assertThrows(NullPointerException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, null, TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // positive prices
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.negate(), TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE.negate(), TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // dates not null
        assertThrows(NullPointerException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, null, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, null, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // same dates
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date after to date
        assertThrows(IllegalArgumentException.class, () -> new TrendAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_TO_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void build() {
        TrendAlert alert = createTestTrendAlert();

        assertNotNull(alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));

        assertThrows(NullPointerException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void match() {
        ZonedDateTime actualTime = nowUtc();
        assertThrows(NullPointerException.class, () -> createTestTrendAlert().match(actualTime, null, null));
        assertThrows(NullPointerException.class, () -> createTestTrendAlert().match(null, Collections.emptyList(), null));

        TrendAlert alert = createTestTrendAlert();
        assertEquals(alert, alert.match(actualTime, Collections.emptyList(), null).alert());
        assertEquals(NOT_MATCHING, alert.match(actualTime, Collections.emptyList(), null).status());

        // previousCandlestick null
        // priceOnTrend true -> MATCHED
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(1L)).withFromDate(actualTime); // test alert increment 1 by hour
        alert = (TrendAlert) alert.withFromPrice(TWO).withToPrice(BigDecimal.valueOf(3L)).withMargin(ZERO);

        Candlestick candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, new BigDecimal("2.5"), ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());

        Candlestick candlestick2 = new Candlestick(actualTime, actualTime.plusMinutes(1L), ONE, ONE, TWO.add(ONE), ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick2), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick2), null).matchingCandlestick());

        Candlestick candlestick3 = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick3), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick3), null).matchingCandlestick());

        assertEquals(candlestick, alert.match(actualTime, List.of(candlestick, candlestick3, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick, alert.match(actualTime, List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick2, alert.match(actualTime, List.of(candlestick3, candlestick2), null).matchingCandlestick());

        // test newer candlestick, should ignore candlestick with same closeTime
        candlestick3 = new Candlestick(actualTime, actualTime, TEN, TEN, TEN, TWO.add(ONE));
        assertEquals(candlestick2, alert.match(actualTime, List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        candlestick2 = new Candlestick(actualTime, actualTime, TWO, TWO, TEN, TWO);
        assertNull(alert.match(actualTime, List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick3, candlestick, candlestick2), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());

        // test current price changes
        alert = (TrendAlert) alert.withFromPrice(TWO).withToPrice(BigDecimal.valueOf(3L)).withMargin(ZERO);
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(2L)).withFromDate(actualTime.plusHours(1L));
        candlestick = new Candlestick(actualTime, actualTime, TWO, TWO, TWO, TWO);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime, TEN, TEN, TEN, new BigDecimal(3L));
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());

        // trend should be caped to ZERO on the south
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(2L).plusMinutes(10L)).withFromDate(actualTime.plusHours(2L)); // alert increment 6 by hour
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime, new BigDecimal("0.00000001"), new BigDecimal("0.00000001"), new BigDecimal("0.00000001"), new BigDecimal("0.00000001"));
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime, ZERO, ZERO, ZERO, ZERO);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());

        // trend should increase in the future
        alert = (TrendAlert) alert.withFromDate(actualTime.minusHours(3L).minusMinutes(40L)).withToDate(actualTime.minusHours(3L).minusMinutes(30L));
        candlestick = new Candlestick(actualTime, actualTime, // alert increment 6 by hour, so 18 + 3 for 3h30, = 21 + to price (3) = 24
                BigDecimal.valueOf(23L), BigDecimal.valueOf(23L), BigDecimal.valueOf(23L), BigDecimal.valueOf(23L));
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(ONE)).match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(actualTime, actualTime,
                BigDecimal.valueOf(25L), BigDecimal.valueOf(25L), BigDecimal.valueOf(25L), BigDecimal.valueOf(25L));
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(ONE)).match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(actualTime, actualTime,
                BigDecimal.valueOf(24L), BigDecimal.valueOf(24L), BigDecimal.valueOf(24L), BigDecimal.valueOf(24L));
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());

        // negative trend should decrease in the future, capped to zero
        alert = (TrendAlert) alert.withToDate(actualTime.minusHours(2L)).withFromDate(actualTime.minusHours(3L))
                .withFromPrice(TEN).withToPrice(BigDecimal.valueOf(7L));
        candlestick = new Candlestick(actualTime, actualTime, // alert decrease 3 by hour, so -6 since 2 hours, = -6 + to price (7) = 1
                BigDecimal.valueOf(0.9d), BigDecimal.valueOf(0.9d), BigDecimal.valueOf(0.9d), BigDecimal.valueOf(0.9d));
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(BigDecimal.valueOf(0.2d))).match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(actualTime, actualTime,
                TWO, TWO, TWO, TWO);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(ONE)).match(actualTime, List.of(candlestick), null).status());
        alert = (TrendAlert) alert.withFromPrice(TEN).withToPrice(BigDecimal.valueOf(5L));
        candlestick = new Candlestick(actualTime, actualTime, // capped to zero
                ZERO, ZERO, ZERO, ZERO);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());

        // priceOnTrend false, priceCrossedTrend true -> MATCHED
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(1L)).withFromDate(actualTime); // test alert increment 1 by hour
        alert = (TrendAlert) alert.withFromPrice(TWO).withToPrice(BigDecimal.valueOf(3L)).withMargin(ZERO);

        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        candlestick2 = new Candlestick(actualTime, actualTime.plusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick2), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        assertNull(alert.match(actualTime, List.of(candlestick2), null).matchingCandlestick());
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick, candlestick2), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick, candlestick2), null).matchingCandlestick());

        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick2, candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick2, candlestick), null).matchingCandlestick());
        candlestick2 = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick2, candlestick), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick2, candlestick), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend false  margin false -> NOT_MATCHED
        alert = (TrendAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        alert = (TrendAlert) alert.withMargin(new BigDecimal("0.99999999"));
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend false  margin true -> MARGIN
        alert = (TrendAlert) alert.withMargin(ONE);
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());

        alert = (TrendAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(actualTime, actualTime, TEN, TEN, TEN, TEN);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        assertNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());
        alert = (TrendAlert) alert.withMargin(BigDecimal.valueOf(8L));
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), null).status());
        assertNotNull(alert.match(actualTime, List.of(candlestick), null).matchingCandlestick());

        // previousCandlestick null
        // priceOnTrend true, listeningDate < openTime -> NOT_MATCHED
        candlestick = new Candlestick(alert.listeningDate, actualTime, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.plusMinutes(3L), actualTime, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), actualTime, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusMinutes(3L), actualTime, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());

        // previousCandlestick provided
        // priceOnTrend true -> MATCHED
        alert = (TrendAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), null).status());
        Candlestick previousCandlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // priceOnTrend false, priceCrossedTrend true -> MATCHED
        alert = (TrendAlert) alert.withMargin(ZERO);

        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        candlestick2 = new Candlestick(actualTime, actualTime.plusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick, candlestick2), null).status());
        previousCandlestick = new Candlestick(actualTime, actualTime.plusMinutes(3L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick, candlestick2), previousCandlestick).status());
        previousCandlestick = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick, candlestick2), previousCandlestick).status());

        // previousCandlestick with openTime before listeningDate
        // datesInLimits true, priceInRange false, priceCrossedRange true -> MATCHED
        candlestick = candlestick2;
        previousCandlestick = new Candlestick(alert.listeningDate, actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.minusMinutes(1L), actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.plusMinutes(1L), actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(MATCHED, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // priceOnTrend false, priceCrossedTrend false  margin true -> MARGIN
        alert = (TrendAlert) alert.withMargin(BigDecimal.valueOf(3L));
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), null).status());
        previousCandlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());

        // priceOnTrend false, priceCrossedTrend false, margin true, listeningDate < openTime -> NOT_MATCHED
        candlestick = new Candlestick(alert.listeningDate, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.plusMinutes(3L), actualTime, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        candlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), actualTime, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusMinutes(3L), actualTime, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(actualTime, List.of(candlestick), previousCandlestick).status());
    }

    @Test
    void currentTrendPrice() {
        ZonedDateTime actualTime = nowUtc();
        ZonedDateTime fromDate = actualTime.minusHours(1L);
        ZonedDateTime inOneHour = fromDate.plusHours(1L);

        assertEquals(ONE.add(ONE_HOUR_SECONDS), TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate, fromDate));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, ZERO, ZERO, fromDate, fromDate));
        assertEquals(ONE, TrendAlert.currentTrendPrice(actualTime, ONE, ONE, fromDate, fromDate));
        assertEquals(TWO, TrendAlert.currentTrendPrice(actualTime, TWO, TWO, fromDate, fromDate));
        assertEquals(ONE, TrendAlert.currentTrendPrice(actualTime, ZERO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(TWO, TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ONE, TrendAlert.currentTrendPrice(actualTime, TWO, ONE, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, TWO, ZERO, fromDate, inOneHour).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, new BigDecimal("134.1666666666666667"), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, new BigDecimal("134.1666666666666667"), new BigDecimal("134.1666666666666667").negate(), fromDate, inOneHour));

        assertEquals(new BigDecimal("1.5"), TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate, fromDate.plusHours(2L)).stripTrailingZeros());
        assertEquals(new BigDecimal("1.25"), TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate, fromDate.plusHours(4L)).stripTrailingZeros());
        assertEquals(new BigDecimal("0.5"), TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate, fromDate.minusHours(2L)).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate, fromDate.minusHours(1L)).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, ONE, TWO, fromDate.minusHours(1L), fromDate.minusHours(2L)).stripTrailingZeros());

        assertEquals(new BigDecimal("16.8096825403428571"), TrendAlert.currentTrendPrice(actualTime, new BigDecimal("1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("23.2353968260571429"), TrendAlert.currentTrendPrice(actualTime, new BigDecimal("-1.73"), new BigDecimal("7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, new BigDecimal("1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(ZERO, TrendAlert.currentTrendPrice(actualTime, new BigDecimal("-1.73"), new BigDecimal("-7.00788888912"), fromDate, fromDate.plusMinutes(21)));
        assertEquals(new BigDecimal("1.1572349203750545"), TrendAlert.currentTrendPrice(actualTime, new BigDecimal("1.00000074"), new BigDecimal("7.008967"), fromDate, fromDate.plusHours(38L).plusMinutes(13L)).stripTrailingZeros());
    }

    @Test
    void secondsBetween() {
        ZonedDateTime fromDate = Dates.parse(Locale.US, null, mock(), "01/01/2000-00:03");
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
        ZonedDateTime fromDate = Dates.parse(Locale.US, UTC, mock(), "01/01/2000-00:03");
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
        var now = nowUtc();
        Alert alert = createTestTrendAlert().withId(() -> 456L);
        ZonedDateTime closeTime = Dates.parse(Locale.US, UTC, mock(), "01/01/2000-00:03");
        Candlestick candlestick = new Candlestick(closeTime.minusHours(1L), closeTime, TWO, ONE, TEN, ONE);
        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null, now));

        var embed = alert.asMessage(MATCHED, null, now);
        String message = embed.getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("trend"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("from price"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("to price"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("to date"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("created"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(Dates.formatDiscordRelative(alert.creationDate)::equals));
        assertFalse(message.contains(alert.message));
        assertFalse(message.contains("threshold"));
        // with candlestick
        assertNotEquals(message, alert.asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString());
        assertTrue(alert.asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("" + closeTime.toEpochSecond()));
        assertTrue(alert.asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("close"));
        // disabled
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains("QUIET"));
        assertFalse(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("quiet for"));

        // MARGIN
        assertNotEquals(message, alert.asMessage(MARGIN, null, now).getDescriptionBuilder().toString());
        embed = alert.asMessage(MARGIN, null, now);
        message = embed.getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("trend"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("from price"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("to price"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("to date"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("created"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(Dates.formatDiscordRelative(alert.creationDate)::equals));
        assertFalse(message.contains(alert.message));
        assertTrue(message.contains("threshold"));
        // with candlestick
        assertNotEquals(message, alert.asMessage(MARGIN, candlestick, now));
        assertTrue(alert.asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("" + closeTime.toEpochSecond()));
        // disabled
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains("QUIET"));
        assertFalse(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));

        // NOT MATCHING
        embed = alert.asMessage(NOT_MATCHING, null, now);
        message = embed.getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("Trend Alert set by"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("from price"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("to price"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("to date"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getName).anyMatch("created"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(Dates.formatDiscordRelative(alert.creationDate)::equals));
        assertFalse(message.contains(alert.message));
        assertFalse(message.contains("threshold"));
        // with candlestick
        assertEquals(message, alert.asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString());
        assertFalse(alert.asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + closeTime.toEpochSecond()));
        // disabled
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains("QUIET"));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertTrue(alert.withListeningDateRepeat(null, alert.repeat)
                .asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(alert.withListeningDateRepeat(now, (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertTrue(alert.withListeningDateRepeat(now.plusSeconds(1L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("QUIET"));
        assertTrue(alert.withListeningDateRepeat(now.plusSeconds(1L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusSeconds(1L).plusNanos(1000000L).toEpochSecond()));
        assertTrue(alert.withListeningDateRepeat(now.plusSeconds(3L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusSeconds(3L).plusNanos(1000000L).toEpochSecond()));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(25L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusMinutes(25L).plusSeconds(1L).toEpochSecond()));
        assertTrue(alert.withListeningDateRepeat(now.plusHours(1L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusHours(1L).plusSeconds(1L).toEpochSecond()));
    }
}