package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

import static org.sbot.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;

public interface ArgumentValidator {

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
        if(!SUPPORTED_EXCHANGES.contains(exchange.toLowerCase())) {
            throw new IllegalArgumentException("Provided exchange is not supported : " + exchange + " (expected [" + String.join(", ", SUPPORTED_EXCHANGES) + "])");
        }
        return exchange;
    }

    @NotNull
    static String requireTickerLength(@NotNull String ticker) {
        if (ticker.length() > ALERT_MAX_TICKER_LENGTH || ticker.length() < ALERT_MIN_TICKER_LENGTH) {
            throw new IllegalArgumentException("Provided ticker is invalid : " + ticker + " (expected " + ALERT_MIN_TICKER_LENGTH + " to " + ALERT_MAX_TICKER_LENGTH + " chars)");
        }
        return ticker;
    }

    @NotNull
    static String requireTickerPairLength(@NotNull String tickerPair) {
        if (tickerPair.length() > (1 + (2 * ALERT_MAX_TICKER_LENGTH)) || tickerPair.length() < ALERT_MIN_TICKER_LENGTH) {
            throw new IllegalArgumentException("Provided ticker or pair is invalid : " + tickerPair + " (expected " + ALERT_MIN_TICKER_LENGTH + " to " + (1 + (2 * ALERT_MAX_TICKER_LENGTH)) + " chars)");
        }
        return tickerPair;
    }

    static String requireMaxMessageArgLength(@NotNull String message) {
        if (message.length() > ALERT_MESSAGE_ARG_MAX_LENGTH) {
            throw new IllegalArgumentException("Provided message is too long : " + message.length() + " chars (max is "+ ALERT_MESSAGE_ARG_MAX_LENGTH + ")\n" + message);
        }
        return message;
    }
}
