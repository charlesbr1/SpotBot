package org.sbot.entities.chart;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;


import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.Alert.NO_DATE;
import static org.sbot.utils.MutableDecimalTest.ONE;

class DatedPriceTest {

    @Test
    void constructorCheck() {
        var now = DatesTest.nowUtc();
        assertDoesNotThrow(() -> new DatedPrice(ONE, now));
        assertThrows(NullPointerException.class, () -> new DatedPrice(null, now));
        assertThrows(IllegalArgumentException.class, () -> new DatedPrice(ONE, NO_DATE));
        assertEquals(ONE, new DatedPrice(ONE, now).price());
        assertEquals(now, new DatedPrice(ONE, now).dateTime());
    }
}