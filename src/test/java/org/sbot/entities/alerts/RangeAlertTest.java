package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.Candlestick;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static java.math.BigDecimal.*;
import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.DatesTest.nowUtc;

class RangeAlertTest {

    static RangeAlert createTestRangeAlert() {
        return new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
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
        assertDoesNotThrow(() -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertDoesNotThrow(() -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), null, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // prices not null
        assertThrows(NullPointerException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, null, TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // null dates ok
        assertDoesNotThrow(() -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, null, null, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date after to date
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_TO_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // same dates
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_FROM_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // same from and to price
        assertDoesNotThrow(() -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_FROM_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from price above to price
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.add(ONE), TEST_FROM_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // positive prices
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE.negate(), TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> new RangeAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE.negate(), TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void build() {
        RangeAlert alert = createTestRangeAlert();

        assertNotNull(alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));

        assertThrows(NullPointerException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void match() {
        assertThrows(NullPointerException.class, () -> createTestRangeAlert().match(null, null));

        RangeAlert alert = createTestRangeAlert();
        assertEquals(alert, alert.match(Collections.emptyList(), null).alert());
        assertEquals(NOT_MATCHING, alert.match(Collections.emptyList(), null).status());

        // previousCandlestick null
        // datesInLimits true, priceInRange true -> MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withFromPrice(ONE).withToPrice(TWO).withMargin(ZERO);
        ZonedDateTime now = nowUtc();

        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        Candlestick candlestick2 = new Candlestick(now, now.plusMinutes(1L), TWO, TWO, TEN, TWO);
        assertEquals(MATCHED, alert.match(List.of(candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick2), null).matchingCandlestick());

        Candlestick candlestick3 = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), TEN, TEN, TEN, TWO.add(ONE));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick3), null).status());
        assertNull(alert.match(List.of(candlestick3), null).matchingCandlestick());

        assertEquals(candlestick, alert.match(List.of(candlestick, candlestick3, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick, alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        assertEquals(candlestick2, alert.match(List.of(candlestick3, candlestick2), null).matchingCandlestick());

        // test newer candlestick, should ignore candlestick with same closeTime
        candlestick3 = new Candlestick(now, now, TEN, TEN, TEN, TWO.add(ONE));
        assertEquals(candlestick2, alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());
        candlestick2 = new Candlestick(now, now, TWO, TWO, TEN, TWO);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick3, candlestick, candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick3, candlestick, candlestick2), null).matchingCandlestick());

        // previousCandlestick null
        // datesInLimits true, priceInRange true, listeningDate < openTime -> NOT_MATCHED
        candlestick = new Candlestick(alert.listeningDate, now, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.plusMinutes(3L), now, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), now, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusMinutes(3L), now, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());

        // datesInLimits true, priceInRange false, priceCrossedRange true -> MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withToPrice(BigDecimal.valueOf(6L)).withFromPrice(BigDecimal.valueOf(5L)).withMargin(ZERO);

        candlestick = new Candlestick(now, now, BigDecimal.valueOf(7L), BigDecimal.valueOf(7L), BigDecimal.valueOf(8L), BigDecimal.valueOf(7L));
        candlestick2 = new Candlestick(now, now.plusMinutes(1L), BigDecimal.valueOf(3L), BigDecimal.valueOf(4L), BigDecimal.valueOf(4L), BigDecimal.valueOf(3L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        assertNull(alert.match(List.of(candlestick2), null).matchingCandlestick());
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());

        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick2, candlestick), null).status());
        assertNull(alert.match(List.of(candlestick2, candlestick), null).matchingCandlestick());
        candlestick2 = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), BigDecimal.valueOf(3L), BigDecimal.valueOf(4L), BigDecimal.valueOf(4L), BigDecimal.valueOf(3L));
        assertEquals(MATCHED, alert.match(List.of(candlestick2, candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick2, candlestick), null).matchingCandlestick());


        // datesInLimits true, priceInRange false, priceCrossedRange false  margin false -> NOT_MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withFromPrice(BigDecimal.valueOf(3L)).withToPrice(BigDecimal.valueOf(4L)).withMargin(ZERO);
        candlestick = new Candlestick(now, now, BigDecimal.valueOf(7L), BigDecimal.valueOf(7L), BigDecimal.valueOf(8L), BigDecimal.valueOf(7L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withMargin(TWO);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // datesInLimits true, priceInRange false, priceCrossedRange false  margin true -> MARGIN
        alert = (RangeAlert) alert.withMargin(BigDecimal.valueOf(3L));
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        alert = (RangeAlert) alert.withMargin(ZERO);
        candlestick = new Candlestick(now, now, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withMargin(TWO);
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // datesInLimits false, priceInRange true -> NOT_MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withFromPrice(ONE).withToPrice(TWO).withMargin(ZERO);
        candlestick = new Candlestick(now, now, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(now.minusMinutes(1L));
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(now.plusMinutes(1L)); // ensure datesInLimits false
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(now.plusHours(1L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(null).withToDate(now);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        alert = (RangeAlert) alert.withToDate(now.plusSeconds(1L));
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withToDate(now.plusMinutes(1L));
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withToDate(now.minusMinutes(1L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withToDate(now.minusHours(1L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // datesInLimits false, priceInRange false, priceCrossedRange true -> NOT_MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withToPrice(BigDecimal.valueOf(6L)).withFromPrice(BigDecimal.valueOf(5L)).withMargin(ZERO);
        candlestick = new Candlestick(now, now, BigDecimal.valueOf(7L), BigDecimal.valueOf(7L), BigDecimal.valueOf(8L), BigDecimal.valueOf(7L));
        candlestick2 = new Candlestick(now, now.plusMinutes(1L), BigDecimal.valueOf(3L), BigDecimal.valueOf(4L), BigDecimal.valueOf(4L), BigDecimal.valueOf(3L));
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(now.plusMinutes(2L)); // ensure datesInLimits false
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(null).withToDate(now.plusMinutes(1L).plusSeconds(1L));
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNotNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());
        alert = (RangeAlert) alert.withToDate(now);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), null).status());
        assertNull(alert.match(List.of(candlestick, candlestick2), null).matchingCandlestick());

        // datesInLimits false, priceInRange false, priceCrossedRange false  margin true -> NOT_MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withFromPrice(BigDecimal.valueOf(3L)).withToPrice(BigDecimal.valueOf(4L));
        candlestick = new Candlestick(now, now, BigDecimal.valueOf(7L), BigDecimal.valueOf(7L), BigDecimal.valueOf(8L), BigDecimal.valueOf(7L));
        alert = (RangeAlert) alert.withMargin(BigDecimal.valueOf(3L));
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        assertNotNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(now.plusMinutes(1L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());
        alert = (RangeAlert) alert.withFromDate(null).withToDate(now.minusMinutes(1L));
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        assertNull(alert.match(List.of(candlestick), null).matchingCandlestick());

        // previousCandlestick provided
        // datesInLimits true, priceInRange true -> MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withFromPrice(ONE).withToPrice(TWO).withMargin(ZERO);
        candlestick = new Candlestick(now, now, ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), null).status());
        Candlestick previousCandlestick = new Candlestick(now, now, ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // datesInLimits true, priceInRange false, priceCrossedRange true -> MATCHED
        alert = (RangeAlert) alert.withFromDate(null).withToDate(null); // ensure datesInLimits true
        alert = (RangeAlert) alert.withToPrice(BigDecimal.valueOf(6L)).withFromPrice(BigDecimal.valueOf(5L)).withMargin(ZERO);

        candlestick = new Candlestick(now, now, BigDecimal.valueOf(7L), BigDecimal.valueOf(7L), BigDecimal.valueOf(8L), BigDecimal.valueOf(7L));
        candlestick2 = new Candlestick(now, now.plusMinutes(1L), BigDecimal.valueOf(3L), BigDecimal.valueOf(4L), BigDecimal.valueOf(4L), BigDecimal.valueOf(3L));
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), null).status());
        previousCandlestick = new Candlestick(now, now.plusMinutes(3L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        previousCandlestick = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());

        // previousCandlestick with openTime before listeningDate
        // datesInLimits true, priceInRange false, priceCrossedRange true -> MATCHED
        candlestick2 = candlestick;
        previousCandlestick = new Candlestick(alert.listeningDate, now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.minusMinutes(1L), now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(alert.listeningDate.plusMinutes(1L), now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MATCHED, alert.match(List.of(candlestick, candlestick2), previousCandlestick).status());
        assertEquals(MATCHED, alert.match(List.of(candlestick), previousCandlestick).status());

        // previousCandlestick provided
        // datesInLimits true, priceInRange false, priceCrossedRange false  margin true -> MARGIN
        alert = (RangeAlert) alert.withFromPrice(BigDecimal.valueOf(3L)).withToPrice(BigDecimal.valueOf(4L));
        alert = (RangeAlert) alert.withMargin(BigDecimal.valueOf(3L));
        candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        previousCandlestick = new Candlestick(now, now.plusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        previousCandlestick = new Candlestick(now.minusMinutes(2L), now.minusMinutes(1L), ONE, ONE, TWO, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());

        // datesInLimits true, priceInRange false, margin true, listeningDate < openTime -> NOT_MATCHED
        candlestick = new Candlestick(alert.listeningDate, now, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());
        assertEquals(MARGIN, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.plusMinutes(3L), now, ONE, ONE, ONE, ONE);
        assertEquals(MARGIN, alert.match(List.of(candlestick), previousCandlestick).status());
        candlestick = new Candlestick(alert.listeningDate.minusSeconds(1L), now, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), null).status());
        candlestick = new Candlestick(alert.listeningDate.minusMinutes(3L), now, ONE, ONE, ONE, ONE);
        assertEquals(NOT_MATCHING, alert.match(List.of(candlestick), previousCandlestick).status());
    }

    @Test
    void datesInLimits() {
        ZonedDateTime closeTime = now();
        Candlestick candlestick = new Candlestick(now().minusMinutes(1L), closeTime, TWO, TWO, TEN, ONE);

        assertTrue(RangeAlert.datesInLimits(candlestick, null, null));

        // close is before from date
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime, null));
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime.minusMinutes(1L), null));
        assertFalse(RangeAlert.datesInLimits(candlestick, closeTime.plusMinutes(1L), null));

        // close is strictly after to date
        assertFalse(RangeAlert.datesInLimits(candlestick, null, closeTime));
        assertTrue(RangeAlert.datesInLimits(candlestick, null, closeTime.plusSeconds(1L)));
        assertTrue(RangeAlert.datesInLimits(candlestick, null, closeTime.plusMinutes(1L)));
        assertFalse(RangeAlert.datesInLimits(candlestick, null, now().minusMinutes(1)));

        // close is between from and to date
        assertFalse(RangeAlert.datesInLimits(candlestick, closeTime, closeTime));
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime, closeTime.plusSeconds(1L)));
        assertTrue(RangeAlert.datesInLimits(candlestick, closeTime.minusMinutes(1L), closeTime.plusMinutes(1L)));
    }

    @Test
    void priceInRange() {
        BigDecimal low = new BigDecimal(30L);
        BigDecimal high = new BigDecimal(40L);
        BigDecimal openClose = new BigDecimal(35L);

        Candlestick candlestick = new Candlestick(
                now(), now().plusHours(1L),
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
                now(), now().plusHours(1L),
                price.add(ONE), price.add(ONE),
                price.add(TEN), price);

        assertFalse(RangeAlert.priceCrossedRange(null, null, null, null));

        // prices are above the range
        Candlestick current = newCandle.apply(toPrice.add(TEN));
        Candlestick previous = newCandle.apply(toPrice.add(TWO));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, toPrice, toPrice, previous));

        // prices are above the range since previous candlestick
        current = newCandle.apply(toPrice.add(TEN));
        previous = newCandle.apply(toPrice);
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, toPrice, toPrice, previous));

        // prices crossed up the range since previous candlestick
        current = newCandle.apply(toPrice.add(TEN));
        previous = newCandle.apply(toPrice.subtract(ONE));
        assertTrue(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertTrue(RangeAlert.priceCrossedRange(current, toPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, null));
        assertFalse(RangeAlert.priceCrossedRange(current, toPrice, toPrice, null));

        newCandle = price -> new Candlestick(
                now(), now().plusHours(1L),
                price.add(ONE), price.add(ONE),
                price, price.subtract(TEN));

        // prices are below the range
        current = newCandle.apply(fromPrice.subtract(TEN));
        previous = newCandle.apply(fromPrice.subtract(TWO));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, fromPrice, previous));

        // prices are below the range since previous candlestick
        current = newCandle.apply(fromPrice.subtract(TEN));
        previous = newCandle.apply(fromPrice);
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, fromPrice, previous));

        // prices crossed down the range since previous candlestick
        current = newCandle.apply(fromPrice.subtract(TEN));
        previous = newCandle.apply(fromPrice.add(ONE));
        assertTrue(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, previous));
        assertTrue(RangeAlert.priceCrossedRange(current, fromPrice, fromPrice, previous));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, toPrice, null));
        assertFalse(RangeAlert.priceCrossedRange(current, fromPrice, fromPrice, null));
    }

    @Test
    void asMessage() {
        var now = nowUtc();
        Alert alert = createTestRangeAlert().withId(() -> 456L);
        ZonedDateTime closeTime = Dates.parse(Locale.US, mock(), "01/01/2000-00:03");
        Candlestick candlestick = new Candlestick(closeTime.minusHours(1L), closeTime, TWO, ONE, TEN, ONE);
        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null, now));

        EmbedBuilder embed = alert.asMessage(MATCHED, null, now);
        assertNotNull(embed);
        String message = embed.getDescriptionBuilder().toString();
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("range"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("low"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("high"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("created"::equals));
        assertTrue(embed.getFields().stream().map(Field::getValue).anyMatch(Dates.formatDiscordRelative(alert.creationDate)::equals));
        assertFalse(message.contains(alert.message));
        assertFalse(message.contains("threshold"));
        // with candlestick
        assertNotEquals(message, alert.asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString());
        assertTrue(alert.asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("close"));
        assertTrue(alert.asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("" + closeTime.toEpochSecond()));
        // with no repeat or last trigger
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(message.contains("quiet for"));
        assertFalse(alert.withListeningDateSnooze(now(), (short) 2).asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(alert.withListeningDateSnooze(now(), (short) 2).asMessage(MATCHED, candlestick, now).getDescriptionBuilder().toString().contains("quiet for"));
        // with dates
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.fromDate))));
        assertTrue(alert.withFromDate(null).asMessage(MATCHED, null, now)
                .getFields().stream().map(Field::getName).noneMatch("from date"::equals));

        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("to date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.toDate))));
        assertTrue(embed.getFields().stream().map(Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.toDate))));
        assertTrue(alert.withToDate(null).asMessage(MATCHED, null, now)
                .getFields().stream().map(Field::getName).noneMatch("to date"::equals));

        // MARGIN
        assertNotEquals(message, alert.asMessage(MARGIN, null, now));
        embed = alert.asMessage(MARGIN, null, now);
        message = embed.getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("range"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("to date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("created"::equals));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.fromDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscord(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(v -> v.contains(Dates.formatDiscordRelative(alert.toDate))));
        assertTrue(embed.getFields().stream().map(MessageEmbed.Field::getValue).anyMatch(Dates.formatDiscordRelative(alert.creationDate)::equals));
        assertFalse(message.contains(alert.message));
        assertTrue(message.contains("threshold"));
        // with candlestick
        assertNotEquals(message, alert.asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString());
        assertTrue(alert.asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("" + closeTime.toEpochSecond()));
        // with no repeat or last trigger
        assertFalse(message.contains("DISABLED"));
        assertFalse(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertFalse(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(message.contains("quiet for"));
        assertFalse(alert.withListeningDateSnooze(now(), (short) 2).asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(alert.withListeningDateSnooze(now(), (short) 2).asMessage(MARGIN, candlestick, now).getDescriptionBuilder().toString().contains("quiet for"));
        // with dates
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("from date"::equals));
        assertTrue(alert.withFromDate(null).asMessage(MARGIN, null, now)
                .getFields().stream().map(Field::getName).noneMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("to date"::equals));
        assertTrue(alert.withToDate(null).asMessage(MARGIN, null, now)
                .getFields().stream().map(Field::getName).noneMatch("to date"::equals));

        // NOT MATCHING
        embed = alert.asMessage(NOT_MATCHING, null, now);
        message = embed.getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("Range Alert set by"));
        assertTrue(message.contains(alert.exchange));
        assertTrue(message.contains(alert.pair));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("to date"::equals));
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
        // with no repeat or no listening date
        assertFalse(message.contains("DISABLED"));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.lastTrigger)));
        assertTrue(alert.withListeningDateRepeat(null, alert.repeat)
                .asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertTrue(alert.withListeningDateRepeat(null, (short) 0)
                .asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertFalse(message.contains("QUIET"));

        assertFalse(alert.withListeningDateSnooze(now, (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("DISABLED"));
        assertTrue(alert.withListeningDateSnooze(now.plusSeconds(1L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("QUIET"));
        assertTrue(alert.withListeningDateSnooze(now.plusSeconds(1L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusSeconds(1L).plusNanos(1000000L).toEpochSecond()));
        assertTrue(alert.withListeningDateSnooze(now.plusSeconds(3L).plusNanos(1000000L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusSeconds(3L).plusNanos(1000000L).toEpochSecond()));
        assertTrue(alert.withListeningDateSnooze(now.plusMinutes(25L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusMinutes(25L).plusSeconds(1L).toEpochSecond()));
        assertTrue(alert.withListeningDateSnooze(now.plusHours(1L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusHours(1L).plusSeconds(1L).toEpochSecond()));
        assertTrue(alert.withListeningDateSnooze(now.plusHours(1L).plusMinutes(1L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusHours(1L).plusMinutes(1L).plusSeconds(1L).toEpochSecond()));
        assertTrue(alert.withListeningDateSnooze(now.plusHours(1L).plusMinutes(59L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusHours(1L).plusMinutes(59L).plusSeconds(1L).toEpochSecond()));
        assertTrue(alert.withListeningDateSnooze(now.plusHours(3L).plusSeconds(1L), (short) 1).asMessage(NOT_MATCHING, candlestick, now).getDescriptionBuilder().toString().contains("" + now.plusHours(3L).plusSeconds(1L).toEpochSecond()));
        // with dates
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("from date"::equals));
        assertTrue(alert.withFromDate(null).asMessage(MARGIN, null, now)
                .getFields().stream().map(Field::getName).noneMatch("from date"::equals));
        assertTrue(embed.getFields().stream().map(Field::getName).anyMatch("to date"::equals));
        assertTrue(alert.withToDate(null).asMessage(MARGIN, null, now)
                .getFields().stream().map(Field::getName).noneMatch("to date"::equals));
    }
}