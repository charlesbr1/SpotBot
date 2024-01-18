package org.sbot.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
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
        assertThrows(NullPointerException.class, () -> new RemainderAlert(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_PAIR, TEST_MESSAGE, null));
    }

    @Test
    void build() {
        RemainderAlert alert = createTestRemainderAlert();

        assertNotNull(alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(NullPointerException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, null, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, "bad exchange", TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                BigDecimal.ONE, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                BigDecimal.ONE, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, BigDecimal.ONE, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, ZonedDateTime.now(), null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, ZonedDateTime.now(),
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NULL_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, (short) (1 + REMAINDER_DEFAULT_REPEAT), DEFAULT_SNOOZE_HOURS));
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

        assertTrue(alert.match(TEST_FROM_DATE).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusMinutes(1L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusMinutes(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusHours(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusDays(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusMonths(3L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.plusYears(3L)).status().isMatched());

        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(1L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(29L)).status().isMatched());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(30L)).status().isMatched());
        assertFalse(alert.match(TEST_FROM_DATE.minusMinutes(31L)).status().isMatched());

        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(31L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(32L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(33L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(60L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusHours(3L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusDays(3L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusMonths(3L)).status().notMatching());
        assertTrue(alert.match(TEST_FROM_DATE.minusYears(3L)).status().notMatching());
    }
    @Test
    void asMessage() {
        Alert alert = createTestRemainderAlert().withId(() -> 456L);

        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null));
        String message = alert.asMessage(MatchingAlert.MatchingStatus.MATCHED, null);
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains(alert.message));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));

        assertEquals(message, alert.asMessage(MatchingAlert.MatchingStatus.MARGIN, null));

        message = alert.asMessage(MatchingAlert.MatchingStatus.NOT_MATCHING, null);
        assertNotNull(message);
        assertFalse(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("<@" + alert.userId + ">"));
        assertTrue(message.contains(alert.message));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatUTC(alert.fromDate)));
    }
}