package org.sbot.entities.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.MatchingService;
import org.sbot.utils.Dates;

import java.time.ZonedDateTime;
import java.util.List;

import static java.math.BigDecimal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE;
import static org.sbot.utils.DatesTest.nowUtc;

class RemainderAlertTest {

    static RemainderAlert createTestRemainderAlert() {
        return new RemainderAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID,TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_PAIR, TEST_MESSAGE, TEST_FROM_DATE);
    }

    @Test
    void constructor() {
        RemainderAlert alert = createTestRemainderAlert();

        assertEquals(remainder, alert.type);
        assertEquals(REMAINDER_VIRTUAL_EXCHANGE, alert.getExchange());
        assertEquals(TEST_FROM_DATE.minusMinutes(1L), alert.creationDate);
        assertEquals(TEST_FROM_DATE, alert.listeningDate);
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
        assertDoesNotThrow(() -> new RemainderAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_PAIR, TEST_MESSAGE, TEST_FROM_DATE));
        // from date not null
        assertThrows(NullPointerException.class, () -> new RemainderAlert(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_PAIR, TEST_MESSAGE, null));
    }

    @Test
    void build() {
        RemainderAlert alert = createTestRemainderAlert();

        assertNotNull(alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // from date not null
        assertThrows(NullPointerException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, null, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // remainder exchange
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, "bad exchange", TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no margin
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                ONE, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no prices
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                ONE, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, ONE, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no to date
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, nowUtc(), null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // no last trigger
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, nowUtc(),
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // repeat 0 -> ok
        assertDoesNotThrow(() -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, (short) 0, DEFAULT_SNOOZE_HOURS));
        // bad repeat
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, (short) (1 + REMAINDER_DEFAULT_REPEAT), DEFAULT_SNOOZE_HOURS));
        // no snooze
        assertThrows(IllegalArgumentException.class, () -> alert.build(NEW_ALERT_ID, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, REMAINDER_VIRTUAL_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                null, null, TEST_FROM_DATE, null, null,
                MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, (short) (1 + DEFAULT_SNOOZE_HOURS)));
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
        Candlestick candlestick = new Candlestick(nowUtc(), nowUtc(), TWO, ONE, TEN, ONE);

        assertThrows(NullPointerException.class, () -> alert.asMessage(null, null, null));
        String message = alert.asMessage(MatchingService.MatchingAlert.MatchingStatus.MATCHED, null, null).getDescriptionBuilder().toString();
        assertNotNull(message);
        assertTrue(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatDiscord(alert.fromDate)));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.fromDate)));
        assertTrue(message.contains("created"));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.creationDate)));
        assertTrue(message.contains(alert.message));
        // no candlestick
        assertEquals(message, alert.asMessage(MatchingService.MatchingAlert.MatchingStatus.MATCHED, candlestick, null).getDescriptionBuilder().toString());

        assertEquals(message, alert.asMessage(MatchingService.MatchingAlert.MatchingStatus.MARGIN, null, null).getDescriptionBuilder().toString());

        message = alert.asMessage(MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING, null, null).getDescriptionBuilder().toString();
        assertNotNull(message);
        assertFalse(message.startsWith("<@" + alert.userId + ">"));
        assertTrue(message.contains("<@" + alert.userId + ">"));
        assertTrue(message.contains(String.valueOf(alert.id)));
        assertTrue(message.contains(Dates.formatDiscord(alert.fromDate)));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.fromDate)));
        assertTrue(message.contains(Dates.formatDiscordRelative(alert.creationDate)));
        assertTrue(message.contains("created"));
        assertTrue(message.contains(alert.message));
        assertEquals(message, alert.asMessage(MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING, candlestick, null).getDescriptionBuilder().toString());
    }
}