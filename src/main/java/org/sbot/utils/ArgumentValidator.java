package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;
import static org.sbot.utils.Dates.formatUTC;

public interface ArgumentValidator {


    int ALERT_MESSAGE_ARG_MAX_LENGTH = 210;
    int ALERT_MIN_TICKER_LENGTH = 3;
    int ALERT_MAX_TICKER_LENGTH = 5;
    int ALERT_MIN_PAIR_LENGTH = 7;
    int ALERT_MAX_PAIR_LENGTH = 11;

    Pattern PAIR_PATTERN = Pattern.compile("^[A-Z0-9]{" + ALERT_MIN_TICKER_LENGTH + ',' + ALERT_MAX_TICKER_LENGTH +
            "}/[A-Z0-9]{" + ALERT_MIN_TICKER_LENGTH + ',' + ALERT_MAX_TICKER_LENGTH + "}$"); // TICKER/TICKER format

    Pattern DISCORD_USER_ID_PATTERN = Pattern.compile("<@(\\d+)>"); // matches id from discord user mention
    Pattern START_WITH_DISCORD_USER_ID_PATTERN = Pattern.compile("^<@(\\d+)>"); // matches id from discord user mention

    static int requirePositive(int value) {
        return (int) requirePositive((long) value);
    }

    static long requirePositive(long value) {
        if(value < 0) {
            throw new IllegalArgumentException("Negative value : " + value);
        }
        return value;
    }

    static BigDecimal requirePositive(BigDecimal value) {
        if(value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Negative value : " + value);
        }
        return value;
    }

    static short requirePositiveShort(long value) {
        if (value < 0 || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Not a positive short value : " + value);
        }

        return (short) value;
    }

    @NotNull
    static String requireSupportedExchange(@NotNull String exchange) {
        if(!VIRTUAL_EXCHANGES.contains(exchange.toLowerCase()) && !SUPPORTED_EXCHANGES.contains(exchange.toLowerCase())) {
            throw new IllegalArgumentException("Provided exchange is not supported : " + exchange + " (expected " + String.join(", ", SUPPORTED_EXCHANGES) + ')');
        }
        return exchange;
    }

    @NotNull
    static String requirePairFormat(@NotNull String pair) {
        if(!PAIR_PATTERN.matcher(pair).matches()) {
            throw new IllegalArgumentException("Invalid pair : " + pair  + ", should be like EUR/USD");
        }
        return pair;
    }

    @NotNull
    static String requireTickerPairLength(@NotNull String tickerPair) {
        if (tickerPair.length() > ALERT_MAX_PAIR_LENGTH || tickerPair.length() < ALERT_MIN_TICKER_LENGTH) {
            throw new IllegalArgumentException("Provided ticker or pair is invalid : " + tickerPair + " (expected " + ALERT_MIN_TICKER_LENGTH + " to " + ALERT_MAX_PAIR_LENGTH + " chars)");
        }
        return tickerPair;
    }

    static String requireAlertMessageMaxLength(@NotNull String message) {
        if (message.length() > ALERT_MESSAGE_ARG_MAX_LENGTH) {
            throw new IllegalArgumentException("Provided message is too long (" + message.length() + " chars, max is " + ALERT_MESSAGE_ARG_MAX_LENGTH + ") : " + message);
        }
        return message;
    }

    static ZonedDateTime requireInPast(@NotNull ZonedDateTime zonedDateTime) {
        if (zonedDateTime.isAfter(ZonedDateTime.now())) {
            throw new IllegalArgumentException("Provided date should be before actual UTC time : " + formatUTC(zonedDateTime) +
                    " (actual UTC time : " + formatUTC(ZonedDateTime.now()) + ')');
        }
        return zonedDateTime;
    }

    static ZonedDateTime requireInFuture(@NotNull ZonedDateTime zonedDateTime) {
        if (zonedDateTime.isBefore(ZonedDateTime.now())) {
            throw new IllegalArgumentException("Provided date should be after actual UTC time : " + formatUTC(zonedDateTime) +
                    " (actual UTC time : " + formatUTC(ZonedDateTime.now()) + ')');
        }
        return zonedDateTime;
    }

    static long requireUser(@NotNull String userMention) {
        Matcher matcher = DISCORD_USER_ID_PATTERN.matcher(userMention);
        if(!matcher.matches())
            throw new IllegalArgumentException("Provided string is not an user mention : " + userMention);
        return Long.parseLong(matcher.group(1));
    }

    static int stringLength(long[] values) {
        return LongStream.of(values).mapToInt(number -> number == 0 ? 1 :
                ((int) (Math.log10(Math.abs(number)) + (number < 0 ? 2 : 1)))) // < 0 need + 1 char : '-'
                .sum();
    }
}
