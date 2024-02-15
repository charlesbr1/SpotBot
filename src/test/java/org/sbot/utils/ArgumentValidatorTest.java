package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;

import static java.math.BigDecimal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.DatesTest.nowUtc;

class ArgumentValidatorTest {

    @Test
    void PAIR_PATTERN() {
        assertTrue(PAIR_PATTERN.matcher("ETH/BTC").matches());
        assertTrue(PAIR_PATTERN.matcher("ETHHH/BTCCC").matches());
        assertFalse(PAIR_PATTERN.matcher("eth/btc").matches());
        assertTrue(PAIR_PATTERN.matcher("1INCH/BTC").matches());
        assertTrue(PAIR_PATTERN.matcher("123/123").matches());
        assertFalse(PAIR_PATTERN.matcher("12/123").matches());
        assertFalse(PAIR_PATTERN.matcher("1/123").matches());
        assertFalse(PAIR_PATTERN.matcher("/123").matches());
        assertFalse(PAIR_PATTERN.matcher("/").matches());
        assertFalse(PAIR_PATTERN.matcher("").matches());
        assertFalse(PAIR_PATTERN.matcher("123").matches());
        assertFalse(PAIR_PATTERN.matcher("123/12").matches());
        assertFalse(PAIR_PATTERN.matcher("123/1").matches());
        assertFalse(PAIR_PATTERN.matcher("123/").matches());
        assertTrue(PAIR_PATTERN.matcher("12345/ABCDE").matches());
        assertFalse(PAIR_PATTERN.matcher("123456/12345").matches());
        assertFalse(PAIR_PATTERN.matcher("12345/ABCDEF").matches());
    }

    @Test
    void DISCORD_USER_ID_PATTERN() {
        assertTrue(DISCORD_USER_ID_PATTERN.matcher("<@1234>").matches());
        assertEquals("aze", DISCORD_USER_ID_PATTERN.matcher("<@1234>aze").replaceFirst(""));
        assertEquals("abcaze", DISCORD_USER_ID_PATTERN.matcher("abc<@1234>aze").replaceFirst(""));
        assertFalse(DISCORD_USER_ID_PATTERN.matcher(" <@1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("a<@1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("1<@1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("&<@1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("@<@1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@1234> ").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@1234>a").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@1234>1").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@1234>&").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@1234").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@123a4>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("@1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@ 1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("< @1234>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@1234 >").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@abcd>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<@>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("<>").matches());
        assertFalse(DISCORD_USER_ID_PATTERN.matcher("").matches());
    }

    @Test
    void START_WITH_DISCORD_USER_ID_PATTERN() {
        assertTrue(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234>").matches());
        assertEquals("aze", START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234>aze").replaceFirst(""));
        assertNotEquals("abcaze", START_WITH_DISCORD_USER_ID_PATTERN.matcher("abc<@1234>aze").replaceFirst(""));
        assertEquals("abc<@1234>aze", START_WITH_DISCORD_USER_ID_PATTERN.matcher("abc<@1234>aze").replaceFirst(""));
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("1<@1234").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher(" <@1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("a<@1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("1<@1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("&<@1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("@<@1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234> ").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234>a").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234>1").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234>&").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@123a4>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("@1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@ 1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("< @1234>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@1234 >").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@abcd>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<@>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("<>").matches());
        assertFalse(START_WITH_DISCORD_USER_ID_PATTERN.matcher("").matches());
    }


    @Test
    void requirePositive() {
        assertEquals(0, ArgumentValidator.requirePositive(0));
        assertEquals(1, ArgumentValidator.requirePositive(1));
        assertEquals(300, ArgumentValidator.requirePositive(300));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-300));
        assertEquals(0, ArgumentValidator.requirePositive(0L));
        assertEquals(1, ArgumentValidator.requirePositive(1L));
        assertEquals(300, ArgumentValidator.requirePositive(300L));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-1L));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-300L));
        assertEquals(ZERO, ArgumentValidator.requirePositive(ZERO));
        assertEquals(ZERO, ArgumentValidator.requirePositive(ZERO.negate()));
        assertEquals(ONE, ArgumentValidator.requirePositive(ONE));
        assertEquals(TEN, ArgumentValidator.requirePositive(TEN));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(ONE.negate()));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(TEN.negate()));
    }

    @Test
    void requirePositiveShort() {
        assertEquals(0, ArgumentValidator.requirePositiveShort((short) 0));
        assertEquals(1, ArgumentValidator.requirePositiveShort((short) 1));
        assertEquals(300, ArgumentValidator.requirePositiveShort((short) 300));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositiveShort((short) -1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositiveShort((short) -300));
    }

    @Test
    void requireSupportedExchange() {
        SUPPORTED_EXCHANGES.forEach(exchange -> assertEquals(exchange, ArgumentValidator.requireSupportedExchange(exchange)));
        VIRTUAL_EXCHANGES.forEach(exchange -> assertEquals(exchange, ArgumentValidator.requireSupportedExchange(exchange)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSupportedExchange("exchange"));
    }

    @Test
    void requirePairFormat() {
        assertEquals("ETH/BTC", ArgumentValidator.requirePairFormat("ETH/BTC"));
        assertEquals("ETHHH/BTCCC", ArgumentValidator.requirePairFormat("ETHHH/BTCCC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("eth/btc"));
        assertEquals("1INCH/BTC", ArgumentValidator.requirePairFormat("1INCH/BTC"));
        assertEquals("123/123", ArgumentValidator.requirePairFormat("123/123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("12/123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("1/123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("/123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("/"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat(""));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123/12"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123/1"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123/"));
        assertEquals("12345/ABCDE", ArgumentValidator.requirePairFormat("12345/ABCDE"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123456/12345"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("12345/ABCDEF"));
    }

    @Test
    void requireTickerPairLength() {
        assertEquals("1INCH/BTC", ArgumentValidator.requireTickerPairLength("1INCH/BTC"));
        assertEquals("BTC", ArgumentValidator.requireTickerPairLength("BTC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("BT"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("BT"));
        assertEquals("12345/ABCDE", ArgumentValidator.requireTickerPairLength("12345/ABCDE"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("123456/ABCDE"));
    }

    @Test
    void requireAlertMessageMaxLength() {
        assertEquals("message", ArgumentValidator.requireAlertMessageMaxLength("message"));
        assertEquals("1".repeat(ALERT_MESSAGE_ARG_MAX_LENGTH), ArgumentValidator.requireAlertMessageMaxLength("1".repeat(ALERT_MESSAGE_ARG_MAX_LENGTH)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireAlertMessageMaxLength("1".repeat(1 + ALERT_MESSAGE_ARG_MAX_LENGTH)));
    }

    @Test
    void requireInPast() {
        ZonedDateTime now = nowUtc();
        assertEquals(now, ArgumentValidator.requireInPast(Clock.systemUTC(), now));
        assertEquals(now.minusSeconds(1L), ArgumentValidator.requireInPast(Clock.systemUTC(), now.minusSeconds(1L)));
        assertEquals(now.minusMinutes(1L), ArgumentValidator.requireInPast(Clock.systemUTC(), now.minusMinutes(1L)));
        assertEquals(now.minusDays(3L), ArgumentValidator.requireInPast(Clock.systemUTC(), now.minusDays(3L)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInPast(Clock.systemUTC(), now.plusSeconds(1L)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInPast(Clock.systemUTC(), now.plusMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInPast(Clock.systemUTC(), now.plusDays(3L)));
    }

    @Test
    void requireInFuture() {
        ZonedDateTime now = nowUtc();
        assertEquals(now, ArgumentValidator.requireInFuture(now, now));
        assertEquals(now.plusSeconds(1L), ArgumentValidator.requireInFuture(now, now.plusSeconds(1L)));
        assertEquals(now.plusMinutes(1L), ArgumentValidator.requireInFuture(now, now.plusMinutes(1L)));
        assertEquals(now.plusDays(3L), ArgumentValidator.requireInFuture(now, now.plusDays(3L)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInFuture(now, now.minusSeconds(1L)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInFuture(now, now.minusMinutes(1L)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInFuture(now, now.minusDays(3L)));
    }

    @Test
    void requireUser() {
        assertEquals(1234L, ArgumentValidator.requireUser("<@1234>"));
        assertEquals("aze", DISCORD_USER_ID_PATTERN.matcher("<@1234>aze").replaceFirst(""));
        assertEquals("abcaze", DISCORD_USER_ID_PATTERN.matcher("abc<@1234>aze").replaceFirst(""));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(" <@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("a<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("1<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("&<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("@<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@1234> "));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@1234>a"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@1234>1"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@1234>&"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@1234"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@123a4>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@ 1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("< @1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@1234 >"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@abcd>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<@>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser("<>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(""));
    }
}