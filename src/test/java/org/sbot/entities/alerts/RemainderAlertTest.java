package org.sbot.entities.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.ZonedDateTime;
import java.util.List;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.DatesTest.nowUtc;

class RemainderAlertTest {

    static RemainderAlert createTestRemainderAlert() {
        return new RemainderAlert(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(10L), TEST_FROM_DATE, TEST_PAIR, TEST_MESSAGE, TEST_FROM_DATE, TEST_LAST_TRIGGER, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Test
    void constructor() {
        RemainderAlert alert = createTestRemainderAlert();

        assertEquals(remainder, alert.type);
        assertEquals(REMAINDER_VIRTUAL_EXCHANGE, alert.getExchange());
        assertEquals(TEST_FROM_DATE.minusMinutes(10L), alert.creationDate);
        assertEquals(TEST_FROM_DATE, alert.listeningDate);
        assertNull(alert.fromPrice);
        assertNull(alert.toPrice);
        assertNull(alert.toDate);
        assertNotNull(alert.lastTrigger);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(REMAINDER_DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);
    }

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new RemainderAlert(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_PAIR, TEST_MESSAGE, TEST_FROM_DATE, TEST_LAST_TRIGGER, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date not null
        assertThrows(NullPointerException.class, () -> new RemainderAlert(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_PAIR, TEST_MESSAGE, null, TEST_LAST_TRIGGER, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
    }

    @Test
    void build() {
        RemainderAlert alert = createTestRemainderAlert();

        assertNotNull(alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // clientType not null
        assertThrows(NullPointerException.class, () -> alert.build(NEW_ALERT_ID, null, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date not null
        assertThrows(NullPointerException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, null, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // remainder exchange
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, "bad exchange", TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no margin
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                ONE, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no prices
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                ONE, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, ONE, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no to date
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, nowUtc(), null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // snooze 0 -> ok
        assertDoesNotThrow(() -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, (short) 0, (short) 12));
        // bad snooze
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_CLIENT_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, (short) (1 + REMAINDER_DEFAULT_REPEAT), (short) - 1));
    }

    @Test
    void match() {
        RemainderAlert alert = createTestRemainderAlert();

        assertThrows(NullPointerException.class, () -> alert.match(null, 15));

        ZonedDateTime now = nowUtc();
        assertNotNull(alert.match(now, 15));
        assertTrue(alert.match(now, 15).status().isMatched());

        var checkPeriodMin = List.of(0, 1, 2, 3, 10, 15, 16, 63, 64, 65, 200);
        for(int period : checkPeriodMin) {
            // match since any time before the date
            assertTrue(alert.match(TEST_FROM_DATE, period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.plusMinutes(1L), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.plusMinutes(3L), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.plusHours(3L), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.plusDays(3L), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.plusMonths(3L), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.plusYears(3L), period).status().isMatched());

            // match until period / 2 + 1 minute after date
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(1L).plusSeconds(1L), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((period / 2) - 1), period).status().isMatched());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(period / 2), period).status().isMatched());
            assertFalse(alert.match(TEST_FROM_DATE.minusMinutes((period / 2) + 1), period).status().isMatched());

            // never match a date that is above period / 2 + 2 minutes later in the future
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((period / 2) + 2), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((period / 2) + 3), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((period / 2) + 7), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes((period / 2) + 1 + 60), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(period / 2).minusHours(3L), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(period / 2).minusDays(3L), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(period / 2).minusMonths(3L), period).status().notMatching());
            assertTrue(alert.match(TEST_FROM_DATE.minusMinutes(period / 2).minusYears(3L), period).status().notMatching());
        }
    }

    @Test
    void asMessage() {
        RemainderAlert alert = (RemainderAlert) createTestRemainderAlert().withId(() -> 456L);
        DatedPrice previousClose = new DatedPrice(ONE, nowUtc());

        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null, TEST_FROM_DATE));
        assertThrows(NullPointerException.class, () -> alert.asMessage(mock(), null, null));

        String message = alert.asMessage(MATCHED, null, TEST_FROM_DATE).getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatDiscord(alert.fromDate)));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.fromDate)));
        assertTrue(message.contains("created"));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.creationDate)));
        assertTrue(message.contains(alert.message));
        assertFalse(message.contains(DISABLED));

        // no candlestick
        assertEquals(message, alert.asMessage(MATCHED, previousClose, TEST_FROM_DATE).getDescriptionBuilder().toString());
        assertEquals(message, alert.asMessage(MARGIN, null, TEST_FROM_DATE).getDescriptionBuilder().toString());

        message = alert.withListeningDateRepeat(null, alert.repeat).asMessage(NOT_MATCHING, null, TEST_FROM_DATE).getDescriptionBuilder().toString();
        assertTrue(message.contains(DISABLED));

        message = alert.asMessage(NOT_MATCHING, null, TEST_FROM_DATE).getDescriptionBuilder().toString();
        assertNotNull(message);
        assertFalse(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("<@" + alert.userId + ">"));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatDiscord(alert.fromDate)));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.fromDate)));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.creationDate)));
        assertTrue(message.contains("created"));
        assertFalse(message.contains(alert.message));
        assertFalse(message.contains(DISABLED));
        assertEquals(message, alert.asMessage(NOT_MATCHING, previousClose, TEST_FROM_DATE).getDescriptionBuilder().toString());

        message = alert.withListeningDateRepeat(null, alert.repeat).asMessage(NOT_MATCHING, null, TEST_FROM_DATE).getDescriptionBuilder().toString();
        assertTrue(message.contains(DISABLED));

        message = alert.withListeningDateRepeat(alert.fromDate, alert.repeat).asMessage(NOT_MATCHING, null, TEST_FROM_DATE).getDescriptionBuilder().toString();
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));

        var now = DatesTest.nowUtc();
        message = alert.withListeningDateRepeat(alert.fromDate.minusMinutes(1L), alert.repeat).asMessage(NOT_MATCHING, null, now).getDescriptionBuilder().toString();
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));

        message = alert.withListeningDateRepeat(alert.fromDate.minusMinutes(5L), alert.repeat).asMessage(NOT_MATCHING, null, now).getDescriptionBuilder().toString();
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));

        message = alert.withFromDate(now).withListeningDateRepeat(now.plusMinutes(1L).minusSeconds(1L), alert.repeat).asMessage(NOT_MATCHING, null, now).getDescriptionBuilder().toString();
        assertFalse(message.contains(DISABLED));
        assertFalse(message.contains("SNOOZE"));

        message = alert.withFromDate(now).withListeningDateRepeat(now.plusMinutes(1L), alert.repeat).asMessage(NOT_MATCHING, null, now).getDescriptionBuilder().toString();
        assertFalse(message.contains(DISABLED));
        assertTrue(message.contains("SNOOZE"));

        message = alert.withFromDate(now).withListeningDateRepeat(now.plusMinutes(10L), alert.repeat).asMessage(NOT_MATCHING, null, now).getDescriptionBuilder().toString();
        assertFalse(message.contains(DISABLED));
        assertTrue(message.contains("SNOOZE"));
    }
}