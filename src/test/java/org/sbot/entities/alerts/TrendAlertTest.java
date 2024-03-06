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
// TODO
    @Test
    void match() {
        assertThrows(NullPointerException.class, () -> createTestTrendAlert().match(null, null));

        ZonedDateTime actualTime = nowUtc();
        TrendAlert alert = createTestTrendAlert();
        assertEquals(alert, alert.match(Collections.emptyList(), null).alert());
        assertEquals(NOT_MATCHING, alert.match(Collections.emptyList(), null).status());

        // previousCandlestick null
        // priceOnTrend true -> MATCHED
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(1L)).withFromDate(actualTime); // test alert increment 1 by hour
        alert = (TrendAlert) alert.withFromPrice(TWO).withToPrice(BigDecimal.valueOf(3L)).withMargin(ZERO);

        Candlestick candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), ONE, ONE, new BigDecimal("2.5"), ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        Candlestick candlestick2 = new Candlestick(actualTime, actualTime.plusMinutes(1L), ONE, ONE, TWO.add(ONE), ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick2), null).matchingCandlestick());

        Candlestick candlestick3 = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick3), null).status());
        assertNull(alert.match(List.of(candlestick3), null).matchingCandlestick());

        assertEquals(candlestick, alert.match(List.of(candlestick, candlestick3, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick, alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick2, alert.match(List.of(candlestick3, candlestick2), null).matchingCandlestick());

        // test newer candlestick, should ignore candlestick with same closeTime
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, new BigDecimal("2.5"), ONE);
        candlestick3 = new Candlestick(actualTime, actualTime, TEN, TEN, TEN, TWO.add(ONE));
        assertEquals(candlestick2, alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        candlestick2 = new Candlestick(actualTime, actualTime, TWO, TWO, TEN, TWO);
        assertNull(alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick3, candlestick, candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());

        // test current price changes
        alert = (TrendAlert) alert.withFromPrice(TWO).withToPrice(BigDecimal.valueOf(3L)).withMargin(ZERO);
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(2L)).withFromDate(actualTime.plusHours(1L));
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), TWO, TWO, TWO, TWO);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), TEN, TEN, TEN, new BigDecimal(3L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // trend should be caped to ZERO on the south
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(2L).plusMinutes(10L)).withFromDate(actualTime.plusHours(2L)); // alert increment 6 by hour
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), new BigDecimal("0.00000001"), new BigDecimal("0.00000001"), new BigDecimal("0.00000001"), new BigDecimal("0.00000001"));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), ZERO, ZERO, ZERO, ZERO);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // trend should increase in the future
        alert = (TrendAlert) alert.withFromDate(actualTime.minusHours(3L).minusMinutes(40L)).withToDate(actualTime.minusHours(3L).minusMinutes(30L));
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), // alert increment 6 by hour, so 18 + 3 for 3h30, = 21 + to price (3) = 24
                BigDecimal.valueOf(23L), BigDecimal.valueOf(23L), BigDecimal.valueOf(23L), BigDecimal.valueOf(23L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(ONE)).match(List.of(candlestick), null).status());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L),
                BigDecimal.valueOf(25L), BigDecimal.valueOf(25L), BigDecimal.valueOf(25L), BigDecimal.valueOf(25L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(ONE)).match(List.of(candlestick), null).status());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L),
                BigDecimal.valueOf(24L), BigDecimal.valueOf(24L), BigDecimal.valueOf(24L), BigDecimal.valueOf(24L));
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());

        // negative trend should decrease in the future, capped to zero
        alert = (TrendAlert) alert.withToDate(actualTime.minusHours(2L)).withFromDate(actualTime.minusHours(3L))
                .withFromPrice(TEN).withToPrice(BigDecimal.valueOf(7L)); // alert decrease 3 by hour, so -6 since 2 hours, = -6 + to price (7) = 1
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L),
                BigDecimal.valueOf(0.9d), BigDecimal.valueOf(0.9d), BigDecimal.valueOf(0.9d), BigDecimal.valueOf(0.9d));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(BigDecimal.valueOf(0.2d))).match(List.of(candlestick), null).status());
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L),
                TWO, TWO, TWO, TWO);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(MARGIN, ((TrendAlert) alert.withMargin(ONE)).match(List.of(candlestick), null).status());
        alert = (TrendAlert) alert.withFromPrice(TEN).withToPrice(BigDecimal.valueOf(5L));
        candlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), // capped to zero
                ZERO, ZERO, ZERO, ZERO);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());

        // priceOnTrend false, priceCrossedTrend true -> MATCHED
        alert = (TrendAlert) alert.withToDate(actualTime.plusHours(1L)).withFromDate(actualTime); // test alert increment 1 by hour
        alert = (TrendAlert) alert.withFromPrice(TWO).withToPrice(BigDecimal.valueOf(3L)).withMargin(ZERO);

        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        candlestick2 = new Candlestick(actualTime, actualTime.plusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        assertNull(alert.match(List.of(candlestick2), null).matchingCandlestick());
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());

        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick2, candlestick), null).status());
        assertNull(alert.match(List.of(candlestick2, candlestick), null).matchingCandlestick());
        candlestick2 = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(MATCHED, alert.match(List.of(candlestick2, candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick2, candlestick), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend false  margin false -> NOT_MATCHED
        alert = (TrendAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (TrendAlert) alert.withMargin(new BigDecimal("0.99999999"));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // priceOnTrend false, priceCrossedTrend false  margin true -> MARGIN
        alert = (TrendAlert) alert.withMargin(ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        alert = (TrendAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(actualTime, actualTime, TEN, TEN, TEN, TEN);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (TrendAlert) alert.withMargin(BigDecimal.valueOf(8L));
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // previousCandlestick null
        // priceOnTrend true, listeningDate < openTime -> NOT_MATCHED
        candlestick = new Candlestick(alert.listeningDate, actualTime, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.plusMinutes(3L), actualTime, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), actualTime, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusMinutes(3L), actualTime, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());

        // previousCandlestick provided
        // priceOnTrend true -> MATCHED
        alert = (TrendAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        Candlestick previousCandlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // priceOnTrend false, priceCrossedTrend true -> MATCHED
        alert = (TrendAlert) alert.withMargin(ZERO);

        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        candlestick2 = new Candlestick(actualTime, actualTime.plusMinutes(1L), TEN, TEN, TEN, TEN);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        previousCandlestick = new Candlestick(actualTime, actualTime.plusMinutes(3L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        previousCandlestick = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());

        // previousCandlestick with openTime before listeningDate
        // datesInLimits true, priceInRange false, priceCrossedRange true -> MATCHED
        candlestick = candlestick2;
        previousCandlestick = new Candlestick(alert.listeningDate, actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.minusMinutes(1L), actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.plusMinutes(1L), actualTime.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // priceOnTrend false, priceCrossedTrend false  margin true -> MARGIN
        alert = (TrendAlert) alert.withMargin(BigDecimal.valueOf(3L));
        candlestick = new Candlestick(actualTime, actualTime, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        previousCandlestick = new Candlestick(actualTime, actualTime.plusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(actualTime.minusMinutes(2L), actualTime.minusMinutes(1L), ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());

        // priceOnTrend false, priceCrossedTrend false, margin true, listeningDate < openTime -> NOT_MATCHED
        previousCandlestick = new Candlestick(alert.listeningDate.plusMinutes(1L), alert.listeningDate.plusMinutes(2L), ONE, ONE, TWO, ONE);
        candlestick = new Candlestick(alert.listeningDate, alert.listeningDate.plusMinutes(2L).plusSeconds(1L), ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.plusMinutes(3L), alert.listeningDate.plusMinutes(4L), ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());
        candlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), alert.listeningDate, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusMinutes(3L), alert.listeningDate, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
    }

    @Test
    void trendPriceAt() {
        ZonedDateTime fromDate = TEST_FROM_DATE;
        var deltaMinutes = Duration.between(fromDate, TEST_TO_DATE).toMinutes();
        ZonedDateTime inOneHour = fromDate.plusHours(1L);
        Alert alert = createTestTrendAlert();
        assertEquals(TEST_FROM_PRICE.stripTrailingZeros(), TrendAlert.trendPriceAt(fromDate, alert).stripTrailingZeros());
        assertEquals(TEST_TO_PRICE.stripTrailingZeros(), TrendAlert.trendPriceAt(TEST_TO_DATE, alert).stripTrailingZeros());
        assertEquals(TEST_TO_PRICE.subtract(TEST_FROM_PRICE).multiply(TWO).add(TEST_FROM_PRICE).stripTrailingZeros(), TrendAlert.trendPriceAt(TEST_TO_DATE.plusMinutes(deltaMinutes), alert).stripTrailingZeros());
        assertEquals(TEST_TO_PRICE.subtract(TEST_FROM_PRICE).multiply(TEN).add(TEST_FROM_PRICE).stripTrailingZeros(), TrendAlert.trendPriceAt(TEST_TO_DATE.plusMinutes(9L * deltaMinutes), alert).stripTrailingZeros());
        assertEquals(TEST_TO_PRICE.add(TEST_TO_PRICE.subtract(TEST_FROM_PRICE).multiply(new BigDecimal("1.1"))).stripTrailingZeros(), TrendAlert.trendPriceAt(TEST_TO_DATE.plusMinutes((long) (((double) deltaMinutes) * 1.1d)), alert).stripTrailingZeros());
        assertEquals(TEST_TO_PRICE.add(TEST_TO_PRICE.subtract(TEST_FROM_PRICE).multiply(new BigDecimal("-1.1"))).stripTrailingZeros(), TrendAlert.trendPriceAt(TEST_TO_DATE.plusMinutes((long) (((double) deltaMinutes) * -1.1d)), alert).stripTrailingZeros());

        assertEquals(ONE, TrendAlert.trendPriceAt(fromDate, ONE, fromDate, ONE.subtract(TWO), ZERO));
        assertEquals(ONE.add(new BigDecimal(24L).multiply(ONE_HOUR_SECONDS)), TrendAlert.trendPriceAt(TEST_TO_DATE, ONE, fromDate, TWO.subtract(ONE), ZERO));
        assertEquals(ZERO, TrendAlert.trendPriceAt(TEST_TO_DATE, ONE, fromDate, ONE.subtract(TWO), ZERO));
        assertEquals(ONE, TrendAlert.trendPriceAt(fromDate, ONE, fromDate, ZERO, ZERO));
        assertEquals(ZERO, TrendAlert.trendPriceAt(fromDate, ZERO, fromDate, ZERO, ZERO));

        var deltaSeconds = TrendAlert.secondsBetween(fromDate, inOneHour);
        // test prices
        assertEquals(ZERO, TrendAlert.trendPriceAt(inOneHour, ZERO, fromDate, ZERO, deltaSeconds).stripTrailingZeros());
        assertEquals(TWO, TrendAlert.trendPriceAt(inOneHour, ONE, fromDate, ONE, deltaSeconds).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.trendPriceAt(inOneHour, ONE.negate(), fromDate, ONE.negate(), deltaSeconds).stripTrailingZeros());
        assertEquals(TWO.multiply(TEN.stripTrailingZeros()), TrendAlert.trendPriceAt(inOneHour, TEN, fromDate, TEN, deltaSeconds).stripTrailingZeros());
        assertEquals(TWO.multiply(new BigDecimal("134.1666666666666667")), TrendAlert.trendPriceAt(inOneHour, new BigDecimal("134.1666666666666667"), fromDate, new BigDecimal("134.1666666666666667"), deltaSeconds).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.trendPriceAt(inOneHour, new BigDecimal("-0.1666666666666667"), fromDate, new BigDecimal("-0.1666666666666667"), deltaSeconds).stripTrailingZeros());

        // test dates
        assertEquals(ZERO, TrendAlert.trendPriceAt(inOneHour, ZERO, fromDate, ZERO, ZERO).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.trendPriceAt(inOneHour.plusMinutes(123L), ZERO, fromDate, ZERO, deltaSeconds.multiply(TWO)).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.trendPriceAt(inOneHour, ZERO, fromDate.plusMinutes(123L), ZERO, deltaSeconds.multiply(TEN)).stripTrailingZeros());
        assertEquals(ONE.add(ONE_HOUR_SECONDS).stripTrailingZeros(), TrendAlert.trendPriceAt(inOneHour, ONE, fromDate, ONE, ZERO).stripTrailingZeros());
        assertEquals(TWO.add(TWO.multiply(ONE_HOUR_SECONDS)).stripTrailingZeros(), TrendAlert.trendPriceAt(inOneHour, TWO, fromDate, TWO, ZERO).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.trendPriceAt(fromDate, TWO, inOneHour, TWO, ZERO).stripTrailingZeros());
   }

    @Test
    void priceDelta() {
        ZonedDateTime dateTime = Dates.parse(Locale.FRANCE, UTC, mock(), "08/01/2000-00:03");
        ZonedDateTime fromDate = Dates.parse(Locale.FRANCE, UTC, mock(), "01/01/2000-00:03");
        ZonedDateTime inOneHour = fromDate.plusHours(1L);
        assertThrows(NullPointerException.class, () -> TrendAlert.priceDelta(dateTime, fromDate, ONE, null));
        assertThrows(NullPointerException.class, () -> TrendAlert.priceDelta(dateTime, fromDate, null, ONE));
        assertThrows(NullPointerException.class, () -> TrendAlert.priceDelta(dateTime, null, ONE, ONE));
        assertThrows(NullPointerException.class, () -> TrendAlert.priceDelta(null, fromDate, ONE, ONE));

        var deltaSeconds = TrendAlert.secondsBetween(fromDate, inOneHour);
        // test prices
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, fromDate, ZERO, deltaSeconds).stripTrailingZeros());
        assertEquals(ONE, TrendAlert.priceDelta(inOneHour, fromDate, ONE, deltaSeconds).stripTrailingZeros());
        assertEquals(ONE.negate(), TrendAlert.priceDelta(inOneHour, fromDate, ONE.negate(), deltaSeconds).stripTrailingZeros());
        assertEquals(TEN.stripTrailingZeros(), TrendAlert.priceDelta(inOneHour, fromDate, TEN, deltaSeconds).stripTrailingZeros());
        assertEquals(TEN.stripTrailingZeros().negate(), TrendAlert.priceDelta(inOneHour, fromDate, TEN.negate(), deltaSeconds).stripTrailingZeros());
        assertEquals(new BigDecimal("134.1666666666666667"), TrendAlert.priceDelta(inOneHour, fromDate, new BigDecimal("134.1666666666666667"), deltaSeconds).stripTrailingZeros());
        assertEquals(new BigDecimal("134.1666666666666667").negate(), TrendAlert.priceDelta(inOneHour, fromDate, new BigDecimal("134.1666666666666667").negate(), deltaSeconds).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.1666666666666667").negate(), TrendAlert.priceDelta(inOneHour, fromDate, new BigDecimal("-0.1666666666666667").negate(), deltaSeconds).stripTrailingZeros());

        // test dates
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, fromDate, ZERO, ZERO).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour.plusMinutes(123L), fromDate, ZERO, deltaSeconds.multiply(TWO)).stripTrailingZeros());
        assertEquals(ZERO, TrendAlert.priceDelta(inOneHour, fromDate.plusMinutes(123L), ZERO, deltaSeconds.multiply(TEN)).stripTrailingZeros());
        assertEquals(ONE_HOUR_SECONDS.stripTrailingZeros(), TrendAlert.priceDelta(inOneHour, fromDate, ONE, ZERO).stripTrailingZeros());
        assertEquals(TWO.multiply(ONE_HOUR_SECONDS).stripTrailingZeros().negate(), TrendAlert.priceDelta(fromDate, inOneHour, TWO, ZERO).stripTrailingZeros());

        // test both
        assertEquals(new BigDecimal("290.64"), TrendAlert.priceDelta(dateTime, fromDate, new BigDecimal("1.73"), deltaSeconds).stripTrailingZeros());
        assertEquals(new BigDecimal("7.00788888912"), TrendAlert.priceDelta(inOneHour, fromDate, new BigDecimal("7.00788888912"), deltaSeconds).stripTrailingZeros());
        assertEquals(TWO.multiply(new BigDecimal("7.00788888912")), TrendAlert.priceDelta(inOneHour, fromDate, new BigDecimal("7.00788888912"), deltaSeconds.divide(TWO)).stripTrailingZeros());
        assertEquals(new BigDecimal("-0.03").multiply(new BigDecimal("1177.32533337216")), TrendAlert.priceDelta(dateTime, fromDate, new BigDecimal("-7.00788888912"), deltaSeconds.divide(new BigDecimal("0.03"))).stripTrailingZeros());
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
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));
        assertFalse(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertFalse(alert.withListeningDateRepeat(null, (short) -1)
                .asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains(DISABLED));
        assertFalse(alert.withListeningDateRepeat(null, (short) -1)
                .asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("SNOOZE for"));

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
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));
        assertFalse(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains(DISABLED));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains(DISABLED));

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
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertTrue(alert.withListeningDateRepeat(null, alert.repeat)
                .asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains(DISABLED));
        assertFalse(alert.withListeningDateRepeat(now.plusMinutes(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains(DISABLED));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(1L).plusSeconds(1L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("SNOOZE"));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(1L).plusSeconds(1L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusMinutes(1L).plusSeconds(1L).plusNanos(1000000L).toEpochSecond()));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(1L).plusSeconds(3L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusMinutes(1L).plusSeconds(3L).plusNanos(1000000L).toEpochSecond()));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(25L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusMinutes(25L).plusSeconds(1L).toEpochSecond()));
        assertTrue(alert.withListeningDateRepeat(now.plusHours(1L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusHours(1L).plusSeconds(1L).toEpochSecond()));
    }
}