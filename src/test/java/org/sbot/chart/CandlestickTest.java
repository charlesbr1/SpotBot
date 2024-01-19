package org.sbot.chart;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandlestickTest {

    static final ZonedDateTime TEST_OPEN_TIME = Dates.parseUTC("01/01/2000-20:00");
    static final ZonedDateTime TEST_CLOSE_TIME = TEST_OPEN_TIME.plusHours(1L);
    static final BigDecimal TEST_OPEN = ONE;
    static final BigDecimal TEST_CLOSE = BigDecimal.TWO;
    static final BigDecimal TEST_HIGH = BigDecimal.TEN;
    static final BigDecimal TEST_LOW = BigDecimal.valueOf(0.5d);

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, TEST_OPEN, TEST_CLOSE, TEST_HIGH, TEST_LOW));
        assertThrows(NullPointerException.class, () -> new Candlestick(null, TEST_CLOSE_TIME, TEST_OPEN, TEST_CLOSE, TEST_HIGH, TEST_LOW));
        assertThrows(NullPointerException.class, () -> new Candlestick(TEST_OPEN_TIME, null, TEST_OPEN, TEST_CLOSE, TEST_HIGH, TEST_LOW));
        assertThrows(NullPointerException.class, () -> new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, null, TEST_CLOSE, TEST_HIGH, TEST_LOW));
        assertThrows(NullPointerException.class, () -> new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, TEST_OPEN, null, TEST_HIGH, TEST_LOW));
        assertThrows(NullPointerException.class, () -> new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, TEST_OPEN, TEST_CLOSE, null, TEST_LOW));
        assertThrows(NullPointerException.class, () -> new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, TEST_OPEN, TEST_CLOSE, TEST_HIGH, null));

        assertThrows(IllegalArgumentException.class, () -> new Candlestick(TEST_OPEN_TIME, TEST_OPEN_TIME.minusMinutes(1L), TEST_OPEN, TEST_CLOSE, TEST_HIGH, TEST_LOW));
        assertThrows(IllegalArgumentException.class, () -> new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, TEST_OPEN, TEST_CLOSE, TEST_HIGH, TEST_HIGH.add(ONE)));
    }
}