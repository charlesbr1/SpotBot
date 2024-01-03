package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

import static org.sbot.alerts.Alert.ALERT_MESSAGE_ARG_MAX_LENGTH;

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
    static String requireMaxMessageArgLength(@NotNull String value) {
        if (value.length() > ALERT_MESSAGE_ARG_MAX_LENGTH) {
            throw new IllegalArgumentException("Provided argument is too long : " + value.length() + " chars (max is "+ ALERT_MESSAGE_ARG_MAX_LENGTH + ")\n" + value);
        }
        return value;
    }
}
