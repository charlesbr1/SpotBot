package org.sbot.entities.chart;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;
import org.sbot.utils.MutableDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.Alert.NO_DATE;

class EpochMsPriceTest {

    @Test
    void constructorCheck() {
        var now = DatesTest.nowUtc();
        assertDoesNotThrow(() -> new EpochMsPrice(now, 1L, (byte) 0));
        assertThrows(IllegalArgumentException.class, () -> new EpochMsPrice(NO_DATE, 1L, (byte) 0));
        assertThrows(IllegalArgumentException.class, () -> new EpochMsPrice(now, -1L, (byte) 0));

        assertEquals(now, new EpochMsPrice(now, 1L, (byte) 0).dateTime());
        assertEquals(1L, new EpochMsPrice(now, 1L, (byte) 0).unscaledPrice());
        assertEquals((byte) 3, new EpochMsPrice(now, 1L, (byte) 3).priceScale());
    }

    @Test
    void getPrice() {
        var now = DatesTest.nowUtc();
        var epochPrice = new EpochMsPrice(now, 1L, (byte) 0);
        assertNotNull(epochPrice);
        assertThrows(NullPointerException.class, () -> epochPrice.getPrice(null));
        var priceBuffer = MutableDecimal.empty();
        epochPrice.getPrice(priceBuffer);
        assertEquals(1L, priceBuffer.value());
        assertEquals((byte) 0, priceBuffer.scale());

        var epochPrice2 = new EpochMsPrice(now, 123L, (byte) -32);
        epochPrice2.getPrice(priceBuffer);
        assertEquals(123L, priceBuffer.value());
        assertEquals((byte) -32, priceBuffer.scale());
    }
}