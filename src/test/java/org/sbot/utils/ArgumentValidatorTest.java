package org.sbot.utils;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.math.BigDecimal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.DatesTest.nowUtc;

class ArgumentValidatorTest {

    @Test
    void BLANK_SPACES() {
        assertTrue(BLANK_SPACES.matcher(" ").matches());
        assertTrue(BLANK_SPACES.matcher("  ").matches());
        assertTrue(BLANK_SPACES.matcher("      ").matches());
        assertTrue(BLANK_SPACES.matcher("\t").matches());
        assertTrue(BLANK_SPACES.matcher("\t ").matches());
        assertTrue(BLANK_SPACES.matcher("\n").matches());
        assertTrue(BLANK_SPACES.matcher("\n   ").matches());
        assertTrue(BLANK_SPACES.matcher(" \t  \n   ").matches());

        assertFalse(BLANK_SPACES.matcher("").matches());
        assertFalse(BLANK_SPACES.matcher("a").matches());
        assertFalse(BLANK_SPACES.matcher("aabc").matches());
        assertFalse(BLANK_SPACES.matcher("aabc\tfe").matches());
        assertFalse(BLANK_SPACES.matcher("aabc  fe").matches());
        assertFalse(BLANK_SPACES.matcher("aabc  fe  ").matches());
        assertFalse(BLANK_SPACES.matcher("aa\n    \t   bc  fe  ").matches());
    }

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
        assertEquals(0L, ArgumentValidator.requirePositive(0L));
        assertEquals(1L, ArgumentValidator.requirePositive(1L));
        assertEquals(300L, ArgumentValidator.requirePositive(300L));
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
    void requireStrictlyPositive() {
        assertEquals(1, ArgumentValidator.requireStrictlyPositive(1));
        assertEquals(300, ArgumentValidator.requireStrictlyPositive(300));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireStrictlyPositive(0));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-300));
    }

    @Test
    void requireRepeat() {
        assertEquals(REPEAT_MIN, ArgumentValidator.requireRepeat(REPEAT_MIN));
        assertEquals(1, ArgumentValidator.requireRepeat(1));
        assertEquals(30, ArgumentValidator.requireRepeat(30));
        assertEquals(REPEAT_MAX, ArgumentValidator.requireRepeat(REPEAT_MAX));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireRepeat(REPEAT_MAX + 1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireRepeat(REPEAT_MIN - 1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireRepeat(-300));
    }

    @Test
    void requireSnooze() {
        assertEquals(SNOOZE_MIN, ArgumentValidator.requireSnooze(SNOOZE_MIN));
        assertEquals(30, ArgumentValidator.requireSnooze(30));
        assertEquals(SNOOZE_MAX, ArgumentValidator.requireSnooze(SNOOZE_MAX));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSnooze(SNOOZE_MAX + 1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSnooze(0));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSnooze(SNOOZE_MIN - 1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSnooze(-300));
    }

    @Test
    void requirePrice() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requirePrice(null));
        assertEquals(ONE, ArgumentValidator.requirePriceLength(ONE));
        assertEquals(ZERO, ArgumentValidator.requirePrice(ZERO));
        assertEquals(new BigDecimal("1234567890"), ArgumentValidator.requirePrice(new BigDecimal("1234567890")));
        assertEquals(new BigDecimal("12345678901234567890"), ArgumentValidator.requirePrice(new BigDecimal("12345678901234567890")));
        assertEquals(new BigDecimal("1234567890123456789.00000000000000000000000000000000000000000000"), ArgumentValidator.requirePrice(new BigDecimal("1234567890123456789.00000000000000000000000000000000000000000000")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePrice(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePrice(new BigDecimal("123456789012345678901")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePrice(new BigDecimal("-12345678901234567890")));
        assertDoesNotThrow(() -> ArgumentValidator.requirePrice(new BigDecimal("1.2345678901234567890")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePrice(new BigDecimal("1.2345678901234567891")));
    }

    @Test
    void requirePriceLength() {
        assertEquals(null, ArgumentValidator.requirePriceLength(null));
        assertEquals(ONE, ArgumentValidator.requirePriceLength(ONE));
        assertEquals(ZERO, ArgumentValidator.requirePriceLength(ZERO));
        assertEquals(new BigDecimal("1234567890"), ArgumentValidator.requirePriceLength(new BigDecimal("1234567890")));
        assertEquals(new BigDecimal("12345678901234567890"), ArgumentValidator.requirePriceLength(new BigDecimal("12345678901234567890")));
        assertEquals(new BigDecimal("1234567890123456789.00000000000000000000000000000000000000000000"), ArgumentValidator.requirePriceLength(new BigDecimal("1234567890123456789.00000000000000000000000000000000000000000000")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePriceLength(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePriceLength(new BigDecimal("123456789012345678901")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePriceLength(new BigDecimal("-12345678901234567890")));
        assertDoesNotThrow(() -> ArgumentValidator.requirePriceLength(new BigDecimal("1.2345678901234567890")));
        assertDoesNotThrow(() -> ArgumentValidator.requirePriceLength(new BigDecimal("1.23456789012345678900000000")));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePriceLength(new BigDecimal("1.2345678901234567891")));
    }

    @Test
    void requireNotBlank() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireNotBlank(null, "field"));
        assertEquals("a", ArgumentValidator.requireNotBlank("a", "field"));
        assertEquals(" a  ", ArgumentValidator.requireNotBlank(" a  ", "field"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireNotBlank("", "field"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireNotBlank(" \t", "field"));
    }

    @Test
    void requireBoolean() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireBoolean(null, "field"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireBoolean("null", "field"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireBoolean("a", "field"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireBoolean("a bf", "field"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireBoolean("2", "field"));

        assertTrue(ArgumentValidator.requireBoolean("true", "field"));
        assertTrue(ArgumentValidator.requireBoolean("trUe", "field"));
        assertTrue(ArgumentValidator.requireBoolean("TRUE", "field"));
        assertTrue(ArgumentValidator.requireBoolean("yes", "field"));
        assertTrue(ArgumentValidator.requireBoolean("Yes", "field"));
        assertTrue(ArgumentValidator.requireBoolean("YES", "field"));
        assertTrue(ArgumentValidator.requireBoolean("1", "field"));

        assertFalse(ArgumentValidator.requireBoolean("false", "field"));
        assertFalse(ArgumentValidator.requireBoolean("faLse", "field"));
        assertFalse(ArgumentValidator.requireBoolean("FALSE", "field"));
        assertFalse(ArgumentValidator.requireBoolean("no", "field"));
        assertFalse(ArgumentValidator.requireBoolean("No", "field"));
        assertFalse(ArgumentValidator.requireBoolean("NO", "field"));
        assertFalse(ArgumentValidator.requireBoolean("0", "field"));
    }

    @Test
    void requireSupportedLocale() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireSupportedLocale(null));
        for(var locale : DiscordLocale.values()) {
            if(DiscordLocale.UNKNOWN.equals(locale)) {
                assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSupportedLocale(locale.getLocale()));
            } else {
                assertEquals(locale.toLocale(), ArgumentValidator.requireSupportedLocale(locale.getLocale()));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSupportedLocale("fddada"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSupportedLocale("mars language"));
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
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("BT3456"));
        assertEquals("12345/ABCDE", ArgumentValidator.requireTickerPairLength("12345/ABCDE"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("56/ABC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("566/BC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("1234567/ABC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("123456/ABCDE"));
    }

    @Test
    void requireAlertMessageMaxLength() {
        assertEquals("message", ArgumentValidator.requireAlertMessageMaxLength("message"));
        assertEquals("1".repeat(MESSAGE_MAX_LENGTH), ArgumentValidator.requireAlertMessageMaxLength("1".repeat(MESSAGE_MAX_LENGTH)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireAlertMessageMaxLength("1".repeat(1 + MESSAGE_MAX_LENGTH)));
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

    @Test
    void asUser() {
        assertEquals(Optional.of(1234L), ArgumentValidator.asUser("<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(" <@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("a<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("1<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("&<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("@<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@1234> "));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@1234>a"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@1234>1"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@1234>&"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@1234"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@123a4>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@ 1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("< @1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@1234 >"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@abcd>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<@>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("<>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(""));
    }

    @Test
    void asType() {
        assertEquals(Optional.of(range), ArgumentValidator.asType("range"));
        assertEquals(Optional.of(trend), ArgumentValidator.asType("trend"));
        assertEquals(Optional.of(remainder), ArgumentValidator.asType("remainder"));
        assertEquals(Optional.empty(), ArgumentValidator.asType(null));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(""));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("1remainder"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("remainder2"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser("@<>"));
    }

    @Test
    void requireOneItem() {
        assertEquals("1", ArgumentValidator.requireOneItem(List.of("1")));
        assertEquals("test", ArgumentValidator.requireOneItem(List.of("test")));
        assertEquals(ONE, ArgumentValidator.requireOneItem(List.of(ONE)));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireOneItem(null));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireOneItem(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireOneItem(List.of("1", "2")));
    }
}