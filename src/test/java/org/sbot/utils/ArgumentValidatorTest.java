package org.sbot.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.Test;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.binance.BinanceClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.sbot.entities.alerts.Alert.NO_DATE;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.DatesTest.nowUtc;
import static org.sbot.utils.MutableDecimal.ImmutableDecimal.ZERO;
import static org.sbot.utils.MutableDecimalTest.ONE;
import static org.sbot.utils.MutableDecimalTest.TEN;

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
        assertTrue(PAIR_PATTERN.matcher("12/123").matches());
        assertTrue(PAIR_PATTERN.matcher("1/123").matches());
        assertFalse(PAIR_PATTERN.matcher("/123").matches());
        assertFalse(PAIR_PATTERN.matcher("/").matches());
        assertFalse(PAIR_PATTERN.matcher("").matches());
        assertFalse(PAIR_PATTERN.matcher("123").matches());
        assertTrue(PAIR_PATTERN.matcher("123/12").matches());
        assertFalse(PAIR_PATTERN.matcher("123/").matches());
        assertTrue(PAIR_PATTERN.matcher("12345/ABCDE").matches());
        assertTrue(PAIR_PATTERN.matcher("12345678/12345678").matches());
        assertFalse(PAIR_PATTERN.matcher("123456/123456789").matches());
        assertFalse(PAIR_PATTERN.matcher("123456789/12345678").matches());
        assertFalse(PAIR_PATTERN.matcher("12345/ABCDEFGHI").matches());
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
        assertEquals(ZERO, ArgumentValidator.requirePositive(MutableDecimal.of(-0L, (byte) 0)));
        assertEquals(ONE, ArgumentValidator.requirePositive(ONE));
        assertEquals(TEN, ArgumentValidator.requirePositive(TEN));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(MutableDecimal.of(-1L, (byte) 0)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(MutableDecimal.of(-10L, (byte) 0)));
    }

    @Test
    void requireStrictlyPositive() {
        assertEquals(1, ArgumentValidator.requireStrictlyPositive(1));
        assertEquals(300, ArgumentValidator.requireStrictlyPositive(300));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireStrictlyPositive(0));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-300));

        assertEquals(1L, ArgumentValidator.requireStrictlyPositive(1L));
        assertEquals(300L, ArgumentValidator.requireStrictlyPositive(300L));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireStrictlyPositive(0L));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-1L));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePositive(-300L));
    }

    @Test
    void requireEpoch() {
        assertEquals(123L, ArgumentValidator.requireEpoch(123L));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireEpoch(NO_DATE));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireEpoch(-1L));
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
        assertEquals(ZERO, ArgumentValidator.requirePrice(ZERO));
        assertEquals(MutableDecimal.of(1234567890L, (byte) 0), ArgumentValidator.requirePrice(MutableDecimal.of(1234567890L, (byte) 0)));
        assertEquals(MutableDecimal.of(1234567890123456789L, (byte) 0), ArgumentValidator.requirePrice(MutableDecimal.of(1234567890123456789L, (byte) 0)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePrice(MutableDecimal.of(-1L, (byte) 0)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePrice(MutableDecimal.of(-1234567890123456789L, (byte) 0)));
        assertDoesNotThrow(() -> ArgumentValidator.requirePrice(MutableDecimal.of(1234567890123456789L, (byte) 18)));
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
    void requireRealExchange() {
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireRealExchange("exchange"));
        assertEquals(Exchange.BINANCE, ArgumentValidator.requireRealExchange(BinanceClient.NAME));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireRealExchange(Exchange.VIRTUAL.shortName));
    }

    @Test
    void requireAnyExchange() {
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireAnyExchange("exchange"));
        assertEquals(Exchange.BINANCE, ArgumentValidator.requireAnyExchange(BinanceClient.NAME));
        assertEquals(Exchange.VIRTUAL, ArgumentValidator.requireAnyExchange(Exchange.VIRTUAL.shortName));
    }

    @Test
    void requireExchangePairOrFormat() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireExchangePairOrFormat(null, "btc/usdt"));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireExchangePairOrFormat(Exchange.BINANCE, null));
        assertEquals("BTC/USDT", ArgumentValidator.requireExchangePairOrFormat(Exchange.BINANCE, "btc/usdt"));
        assertEquals("NOP/NOPNOP", ArgumentValidator.requireExchangePairOrFormat(Exchange.BINANCE, "nop/nopnop"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireExchangePairOrFormat(Exchange.BINANCE, "nop/"));
    }

    @Test
    void requireExchangePair() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireExchangePair(null, "btc/usdt"));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireExchangePair(Exchange.BINANCE, null));
        assertEquals("BTC/USDT", ArgumentValidator.requireExchangePair(Exchange.BINANCE, "btc/usdt"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireExchangePair(Exchange.BINANCE, "nop/nopnop"));
    }

    @Test
    void requirePairFormat() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requirePairFormat(null));
        assertEquals("ETH/BTC", ArgumentValidator.requirePairFormat("ETH/BTC"));
        assertEquals("ETHHH/BTCCC", ArgumentValidator.requirePairFormat("ETHHH/BTCCC"));
        assertEquals("ETH/BTC", ArgumentValidator.requirePairFormat("eth/btc"));
        assertEquals("1INCH/BTC", ArgumentValidator.requirePairFormat("1INCH/BTC"));
        assertEquals("123/123", ArgumentValidator.requirePairFormat("123/123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123/"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("/123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("/"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat(""));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123/123456789"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123456789/1"));
        assertEquals("12345/ABCDE", ArgumentValidator.requirePairFormat("12345/ABCDE"));
        assertEquals("12345678/12345678", ArgumentValidator.requirePairFormat("12345678/12345678"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requirePairFormat("123456789/123456789"));
    }

    @Test
    void requireTickerPairLength() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireTickerPairLength(null));
        assertEquals("1INCH/BTC", ArgumentValidator.requireTickerPairLength("1INCH/BTC"));
        assertEquals("BTC", ArgumentValidator.requireTickerPairLength("BTC"));
        assertEquals("AR", ArgumentValidator.requireTickerPairLength("AR"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength(""));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("123456789"));
        assertEquals("12345678", ArgumentValidator.requireTickerPairLength("12345678"));
        assertEquals("12345/ABCDE", ArgumentValidator.requireTickerPairLength("12345/ABCDE"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("/ABC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("BTC/"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("/BTC"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("/"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("123456789/ABC"));
        assertEquals("12345678/12345678", ArgumentValidator.requireTickerPairLength("12345678/12345678"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireTickerPairLength("12345678/123456789"));
    }

    @Test
    void requireAlertMessageMaxLength() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireAlertMessageMaxLength(null));
        assertEquals("message", ArgumentValidator.requireAlertMessageMaxLength("message"));
        assertEquals("1".repeat(MESSAGE_MAX_LENGTH), ArgumentValidator.requireAlertMessageMaxLength("1".repeat(MESSAGE_MAX_LENGTH)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireAlertMessageMaxLength("1".repeat(1 + MESSAGE_MAX_LENGTH)));
    }

    @Test
    void requireSettingsMaxLength() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireSettingsMaxLength(null));
        assertEquals("message", ArgumentValidator.requireSettingsMaxLength("message"));
        assertEquals("1".repeat(SETTINGS_MAX_LENGTH), ArgumentValidator.requireSettingsMaxLength("1".repeat(SETTINGS_MAX_LENGTH)));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireSettingsMaxLength("1".repeat(1 + SETTINGS_MAX_LENGTH)));
    }

    @Test
    void requireInFuture() {
        var now = nowUtc();
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInFuture(now, now - 1));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireInFuture(now, now - 15));
        assertEquals(now, ArgumentValidator.requireInFuture(now, now));
        assertEquals(now + 1 , ArgumentValidator.requireInFuture(now, now + 1));
        assertEquals(now + 1002 , ArgumentValidator.requireInFuture(now, now + 1002));
    }

    @Test
    void requireUser() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireUser(DISCORD, null));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireUser(null, "<@1234>"));

        assertEquals(1234L, ArgumentValidator.requireUser(DISCORD, "<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, " <@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "a<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "1<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "&<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "@<@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@1234> "));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@1234>a"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@1234>1"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@1234>&"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@1234"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@123a4>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "@1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@ 1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "< @1234>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@1234 >"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@abcd>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<@>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, "<>"));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireUser(DISCORD, ""));
    }

    @Test
    void requireGuildMember() {
        assertDoesNotThrow(() -> ArgumentValidator.requireGuildMember(null, 0L));
        assertDoesNotThrow(() -> ArgumentValidator.requireGuildMember(null, 123L));
        CacheRestAction<Member> restAction = mock();
        Guild guild = mock();
        when(guild.retrieveMemberById(123L)).thenReturn(restAction);
        when(restAction.complete()).thenReturn(mock());
        assertDoesNotThrow(() -> ArgumentValidator.requireGuildMember(guild, 123L));
        verify(guild).retrieveMemberById(123L);

        when(guild.retrieveMemberById(456L)).thenReturn(restAction);
        when(restAction.complete()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireGuildMember(guild, 456L));
        verify(guild).retrieveMemberById(456L);
    }

    @Test
    void asUser() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.asUser(DISCORD, null));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.asUser(null, "<@1234>"));

        assertEquals(Optional.of(1234L), ArgumentValidator.asUser(DISCORD, "<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, " <@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "a<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "1<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "&<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "@<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@1234> "));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@1234>a"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@1234>1"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@1234>&"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@1234"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@123a4>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@ 1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "< @1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@1234 >"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@abcd>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<@>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, "<>"));
        assertEquals(Optional.empty(), ArgumentValidator.asUser(DISCORD, ""));
    }

    @Test
    void asChannel() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.asChannel(DISCORD, null));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.asChannel(null, "<@1234>"));

        assertEquals(Optional.of(1234L), ArgumentValidator.asChannel(DISCORD, "<#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, " <#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "a<#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "1<#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "&<#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "#<#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#1234> "));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#1234>a"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#1234>1"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#1234>&"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#1234"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#123a4>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "#1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<# 1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "< #1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#1234 >"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#abcd>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<#>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, "<>"));
        assertEquals(Optional.empty(), ArgumentValidator.asChannel(DISCORD, ""));
    }

    @Test
    void asRole() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.asRole(DISCORD, null));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.asRole(null, "<@1234>"));

        assertEquals(Optional.of(1234L), ArgumentValidator.asRole(DISCORD, "<@&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, " <@&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "a<@&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "1<@&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "&<@&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "@<@&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@&1234> "));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@&1234>a"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@&1234>1"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@&1234>&"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@&1234"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<@&123a4>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<&1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<& 1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "< &1234>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<&1234 >"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<&abcd>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<&>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, "<>"));
        assertEquals(Optional.empty(), ArgumentValidator.asRole(DISCORD, ""));
    }

    @Test
    void asType() {
        assertEquals(Optional.of(range), ArgumentValidator.asType("range"));
        assertEquals(Optional.of(trend), ArgumentValidator.asType("trend"));
        assertEquals(Optional.of(remainder), ArgumentValidator.asType("remainder"));
        assertEquals(Optional.empty(), ArgumentValidator.asType(null));
        assertEquals(Optional.empty(), ArgumentValidator.asType(""));
        assertEquals(Optional.empty(), ArgumentValidator.asType("1remainder"));
        assertEquals(Optional.empty(), ArgumentValidator.asType("remainder2"));
        assertEquals(Optional.empty(), ArgumentValidator.asType("@<>"));
    }

    @Test
    void requireOneItem() {
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireOneItem(null));

        assertEquals("1", ArgumentValidator.requireOneItem(List.of("1")));
        assertEquals("test", ArgumentValidator.requireOneItem(List.of("test")));
        assertEquals(ONE, ArgumentValidator.requireOneItem(List.of(ONE)));
        assertThrows(NullPointerException.class, () -> ArgumentValidator.requireOneItem(null));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireOneItem(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> ArgumentValidator.requireOneItem(List.of("1", "2")));
    }
}