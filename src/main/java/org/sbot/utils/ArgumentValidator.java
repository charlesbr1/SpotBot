package org.sbot.utils;

import java.math.BigDecimal;

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
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Not a positive short value : " + value);
        }

        return (short) value;
    }
}
