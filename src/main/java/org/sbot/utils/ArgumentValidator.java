package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

import static org.sbot.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;
import static org.sbot.utils.Dates.formatUTC;

public interface ArgumentValidator {

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

    Pattern PAIR_PATTERN = Pattern.compile("^[A-Z]+/[A-Z]+$+"); // TICKER1/TICKER2 format

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

    static String requireAlertMessageLength(@NotNull String message) {
        if (message.length() > ALERT_MESSAGE_ARG_MAX_LENGTH) {
            throw new IllegalArgumentException("Provided message is too long (" + message.length() + " chars, max is " + ALERT_MESSAGE_ARG_MAX_LENGTH + ") : " + message);
        }
        return message;
    }

    static ZonedDateTime requireInPast(@NotNull ZonedDateTime zonedDateTime) {
        if (zonedDateTime.isAfter(ZonedDateTime.now())) {
            throw new IllegalArgumentException("Provided date should be in the past : " + formatUTC(zonedDateTime));
        }
        return zonedDateTime;
    }
}
