package org.sbot.entities.chart;

import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.Candlestick.CandlestickPeriod;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Locale;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.utils.DatesTest.nowUtc;

class CandlestickTest {

    static final ZonedDateTime TEST_OPEN_TIME = Dates.parse(Locale.US, null, mock(), "01/01/2000-20:00");
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
    void datedClose() {
        var candlestick = new Candlestick(TEST_OPEN_TIME, TEST_CLOSE_TIME, TEST_OPEN, TEST_CLOSE, TEST_HIGH, TEST_LOW);
        var datedClose = candlestick.datedClose();
        assertEquals(TEST_CLOSE, datedClose.price());
        assertEquals(TEST_CLOSE_TIME, datedClose.dateTime());
    }

    @Test
    void periodSince() {
        assertEquals(0, CandlestickPeriod.ONE_MINUTE.daily());
        assertEquals(0, CandlestickPeriod.ONE_MINUTE.hourly());
        assertEquals(1, CandlestickPeriod.ONE_MINUTE.minutes());

        var now = nowUtc();
        assertThrows(NullPointerException.class, () -> Candlestick.periodSince(null, now));
        assertThrows(NullPointerException.class, () -> Candlestick.periodSince(now, null));
        assertThrows(IllegalArgumentException.class, () -> Candlestick.periodSince(now.plusSeconds(1L), now));
        assertThrows(IllegalArgumentException.class, () -> Candlestick.periodSince(now.plusMinutes(1L), now));

        assertEquals(Candlestick.periodSince(now.minusMinutes(60), now), Candlestick.periodSince(now.minusHours(1), now));

        var period = Candlestick.periodSince(now, now);
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(1, period.minutes());

        period = Candlestick.periodSince(now.minusMinutes(2), now);
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(3, period.minutes());

        period = Candlestick.periodSince(now.minusMinutes(23), now);
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(24, period.minutes());

        period = Candlestick.periodSince(now.minusMinutes(59), now);
        assertEquals(0, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusDays(1).minusMinutes(59), now);
        assertEquals(2, period.daily());
        assertEquals(0, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusMinutes(60), now);
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusMinutes(61), now);
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusDays(2).minusMinutes(61), now);
        assertEquals(3, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusHours(1).minusMinutes(10), now);
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusHours(1).minusMinutes(59), now);
        assertEquals(0, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusDays(4).minusHours(1).minusMinutes(59), now);
        assertEquals(5, period.daily());
        assertEquals(2, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusHours(1).minusMinutes(60), now);
        assertEquals(0, period.daily());
        assertEquals(3, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusDays(5).minusHours(1).minusMinutes(60), now);
        assertEquals(6, period.daily());
        assertEquals(3, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusHours(2).minusMinutes(10), now);
        assertEquals(0, period.daily());
        assertEquals(3, period.hourly());
        assertEquals(60, period.minutes());

        period = Candlestick.periodSince(now.minusDays(3).minusHours(7).minusMinutes(44), now);
        assertEquals(4, period.daily());
        assertEquals(8, period.hourly());
        assertEquals(60, period.minutes());
    }
}