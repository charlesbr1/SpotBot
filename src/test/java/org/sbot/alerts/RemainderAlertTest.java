package org.sbot.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;
import org.sbot.utils.Dates;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.math.BigDecimal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.SpotBot.ALERTS_CHECK_PERIOD_MIN;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.AlertTest.*;
import static org.sbot.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.alerts.RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE;

class RemainderAlertTest {

    static RemainderAlert createTestRemainderAlert() {
        return new RemainderAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_PAIR, TEST_MESSAGE, TEST_FROM_DATE);
    }

    @Test
    void constructor() {
        RemainderAlert alert = createTestRemainderAlert();

        assertEquals(remainder, alert.type);
        assertEquals(REMAINDER_VIRTUAL_EXCHANGE, alert.getExchange());
        assertNull(alert.fromPrice);
        assertNull(alert.toPrice);
        assertNull(alert.toDate);
        assertNull(alert.lastTrigger);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(REMAINDER_DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);
    }

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new RemainderAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_PAIR, TEST_MESSAGE, TEST_TO_DATE));
        // from date not null
        assertThrows(NullPointerException.class, () -> new RemainderAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_PAIR, TEST_MESSAGE, null));
    }

    @Test
    void build() {
        RemainderAlert alert = createTestRemainderAlert();

        assertNotNull(alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date not null
        assertThrows(NullPointerException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, null, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // remainder exchange
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, "bad exchange", TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no margin
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                ONE, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no prices
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                ONE, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, ONE, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no to date
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, ZonedDateTime.now(), null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no last trigger
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, ZonedDateTime.now(),
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no repeat
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, (short) (1 + REMAINDER_DEFAULT_REPEAT), DEFAULT_SNOOZE_HOURS));
        // no snooze
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, (short) (1 + DEFAULT_SNOOZE_HOURS)));
    }

    @Test
    void match() {
        RemainderAlert alert = createTestRemainderAlert();

        assertDoesNotThrow(() -> alert.match(null, null));
        assertNotNull(alert.match(null, null));
        assertNull(alert.match(null, null).matchingCandlestick());
        assertNotNull(alert.match(null, null).alert());
        assertNotNull(alert.match(null, null).status());
        assertTrue(alert.match(null, null).status().isMatched());

        ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
        assertNotNull(alert.match(now));
        assertTrue(alert.match(now).status().isMatched());

        // match since any time before the date
        assertTrue(alert.match(TEST_FROM_DATE).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusMinutes(1L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusMinutes(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusHours(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusDays(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusMonths(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusYears(3L)).status().isMatched());

        // match until ALERTS_CHECK_PERIOD_MIN / 2 + 1 minutes after date
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(1L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) - 1)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(ALERTS_CHECK_PERIOD_MIN / 2)).status().isMatched());
        assertFalse(alert.match(TEST_FROM_DATE.minusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 1)).status().isMatched());

        // never match a date that is above 31 minutes later in the future
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 2)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 3)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 7)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 1 + 60)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(ALERTS_CHECK_PERIOD_MIN / 2).minusHours(3L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(ALERTS_CHECK_PERIOD_MIN / 2).minusDays(3L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(ALERTS_CHECK_PERIOD_MIN / 2).minusMonths(3L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(ALERTS_CHECK_PERIOD_MIN / 2).minusYears(3L)).status().notMatching());
    }
    @Test
    void asMessage() {
        Alert alert = createTestRemainderAlert().withId(() -> 456L);
        Candlestick candlestick = new Candlestick(ZonedDateTime.now(), ZonedDateTime.now(), TWO, ONE, TEN, ONE);

        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null));
        String message = alert.asMessage(MatchingAlert.MatchingStatus.MATCHED, null);
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));
        assertTrue(message.contains(alert.message));
        // no candlestick
        assertEquals(message, alert.asMessage(MatchingAlert.MatchingStatus.MATCHED, candlestick));

        assertEquals(message, alert.asMessage(MatchingAlert.MatchingStatus.MARGIN, null));

        message = alert.asMessage(MatchingAlert.MatchingStatus.NOT_MATCHING, null);
        assertNotNull(message);
        assertFalse(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("<@" + alert.userId + ">"));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));
        assertTrue(message.contains(alert.message));
        assertEquals(message, alert.asMessage(MatchingAlert.MatchingStatus.NOT_MATCHING, candlestick));
    }
}