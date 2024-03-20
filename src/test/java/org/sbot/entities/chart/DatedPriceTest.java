package org.sbot.entities.chart;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DatedPriceTest {

    @Test
    void constructorCheck() {
        var now = DatesTest.nowUtc();
        assertDoesNotThrow(() -> new DatedPrice(BigDecimal.ONE, now));
        assertThrows(NullPointerException.class, () -> new DatedPrice(null, null));
        assertThrows(NullPointerException.class, () -> new DatedPrice(null, now));
        assertThrows(NullPointerException.class, () -> new DatedPrice(BigDecimal.ONE, null));
        assertEquals(BigDecimal.ONE, new DatedPrice(BigDecimal.ONE, now).price());
        assertEquals(now, new DatedPrice(BigDecimal.ONE, now).dateTime());
    }
}