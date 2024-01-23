package org.sbot.chart;

import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick.CandlestickPeriod;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.Dates.nowUtc;

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

    @Test
    void periodSince() {
        assertEquals(0, CandlestickPeriod.ONE_MINUTE.daily());
        assertEquals(0, CandlestickPeriod.ONE_MINUTE.hourly());
        assertEquals(1, CandlestickPeriod.ONE_MINUTE.minutes());

        assertThrows(NullPointerException.class, () -> Candlestick.periodSince(null));
        assertThrows(IllegalArgumentException.class, () -> Candlestick.periodSince(nowUtc().plusMinutes(1L)));

        assertEquals(Candlestick.periodSince(nowUtc().minusMinutes(60)), Candlestick.periodSince(nowUtc().minusHours(1)));

        var period = Candlestick.periodSince(nowUtc());
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(1, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusMinutes(2));
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(3, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusMinutes(23));
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(24, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusMinutes(59));
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusDays(1).minusMinutes(59));
        assertEquals(2, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusMinutes(60));
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusMinutes(61));
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusDays(2).minusMinutes(61));
        assertEquals(3, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusHours(1).minusMinutes(10));
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusHours(1).minusMinutes(59));
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusDays(4).minusHours(1).minusMinutes(59));
        assertEquals(5, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusHours(1).minusMinutes(60));
        assertEquals(0, period.daily());
        assertEquals(3, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusDays(5).minusHours(1).minusMinutes(60));
        assertEquals(6, period.daily());
        assertEquals(3, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusHours(2).minusMinutes(10));
        assertEquals(0, period.daily());
        assertEquals(3, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(nowUtc().minusDays(3).minusHours(7).minusMinutes(44));
        assertEquals(4, period.daily());
        assertEquals(8, period.hourly());
        assertEquals(60, period.minutes());
    }
}