package org.sbot.entities.chart;

import org.junit.jupiter.api.Test;
import org.sbot.utils.MutableDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class KLineTest {

    public static KLine of(long openTime, long closeTime, long openValue, long closeValue, long highValue, long lowValue) {
        KLine kLine = KLine.empty();
        kLine.openTime(openTime);
        kLine.closeTime(closeTime);
        kLine.openValue(openValue);
        kLine.closeValue(closeValue);
        kLine.highValue(highValue);
        kLine.lowValue(lowValue);
        return kLine;
    }

    @Test
    void emptyVolatile() {
        assertNotNull(KLine.emptyVolatile());
        assertTrue(KLine.emptyVolatile() != KLine.emptyVolatile());
    }

    @Test
    void empty() {
        assertNotNull(KLine.empty());
        assertTrue(KLine.empty() != KLine.empty());
    }

    @Test
    void openTime() {
        var kline = KLine.emptyVolatile();
        kline.openTime(123L);
        assertEquals(123L, kline.openTime());
        kline = KLine.empty();
        kline.openTime(123L);
        assertEquals(123L, kline.openTime());
    }

    @Test
    void closeTime() {
        var kline = KLine.emptyVolatile();
        kline.closeTime(123L);
        assertEquals(123L, kline.closeTime());
        kline = KLine.empty();
        kline.closeTime(123L);
        assertEquals(123L, kline.closeTime());
    }

    @Test
    void openValue() {
        var kline = KLine.emptyVolatile();
        kline.openValue(123L);
        assertEquals(123L, kline.openValue());
        kline = KLine.empty();
        kline.openValue(123L);
        assertEquals(123L, kline.openValue());
    }

    @Test
    void closeValue() {
        var kline = KLine.emptyVolatile();
        kline.closeValue(123L);
        assertEquals(123L, kline.closeValue());
        kline = KLine.empty();
        kline.closeValue(123L);
        assertEquals(123L, kline.closeValue());
    }

    @Test
    void highValue() {
        var kline = KLine.emptyVolatile();
        kline.highValue(123L);
        assertEquals(123L, kline.highValue());
        kline = KLine.empty();
        kline.highValue(123L);
        assertEquals(123L, kline.highValue());
    }

    @Test
    void lowValue() {
        var kline = KLine.emptyVolatile();
        kline.lowValue(123L);
        assertEquals(123L, kline.lowValue());
        kline = KLine.empty();
        kline.lowValue(123L);
        assertEquals(123L, kline.lowValue());
    }

    @Test
    void openScale() {
        var kline = KLine.emptyVolatile();
        kline.openScale((byte) 23);
        assertEquals((byte) 23, kline.openScale());
        kline = KLine.empty();
        kline.openScale((byte) 23);
        assertEquals((byte) 23, kline.openScale());
    }

    @Test
    void closeScale() {
        var kline = KLine.emptyVolatile();
        kline.closeScale((byte) 23);
        assertEquals((byte) 23, kline.closeScale());
        kline = KLine.empty();
        kline.closeScale((byte) 23);
        assertEquals((byte) 23, kline.closeScale());
    }

    @Test
    void highScale() {
        var kline = KLine.emptyVolatile();
        kline.highScale((byte) 23);
        assertEquals((byte) 23, kline.highScale());
        kline = KLine.empty();
        kline.highScale((byte) 23);
        assertEquals((byte) 23, kline.highScale());
    }

    @Test
    void lowScale() {
        var kline = KLine.emptyVolatile();
        kline.lowScale((byte) 23);
        assertEquals((byte) 23, kline.lowScale());
        kline = KLine.empty();
        kline.lowScale((byte) 23);
        assertEquals((byte) 23, kline.lowScale());
    }

    @Test
    void validate() {
        var buffer = MutableDecimal.empty();
        var kline = KLine.empty();
        kline.openTime(123L);
        kline.closeTime(124L);
        kline.openValue(123L);
        kline.closeValue(123L);
        kline.highValue(124L);
        kline.lowValue(123L);
        kline.openScale((byte) 7);
        kline.closeScale((byte) 7);
        kline.highScale((byte) 7);
        kline.lowScale((byte) 7);
        kline.validate(buffer);

        kline.openTime(-1L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.openTime(234L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.openTime(123L);
        kline.closeTime(-1L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.closeTime(4L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.closeTime(124L);
        kline.openValue(-1L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.openValue(123L);
        kline.closeValue(-1L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.closeValue(123L);
        kline.highValue(-1L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.highValue(123L);
        kline.lowValue(-1L);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.lowValue(kline.highValue() + 1);
        assertThrows(IllegalArgumentException.class, () -> kline.validate(buffer));
        kline.lowValue(123L);
        assertDoesNotThrow(() -> kline.validate(buffer));
    }


    @Test
    void reset() {
        var kline = KLine.empty();
        kline.openTime(123L);
        kline.closeTime(123L);
        kline.openValue(123L);
        kline.closeValue(123L);
        kline.highValue(123L);
        kline.lowValue(123L);
        kline.openScale((byte) 7);
        kline.closeScale((byte) 7);
        kline.highScale((byte) 7);
        kline.lowScale((byte) 7);
        kline.reset();
        assertEquals(0L, kline.openTime());
        assertEquals(0L, kline.closeTime());
        assertEquals(0L, kline.openValue());
        assertEquals(0L, kline.closeValue());
        assertEquals(0L, kline.highValue());
        assertEquals(0L, kline.lowValue());
        assertEquals((byte) 0, kline.openScale());
        assertEquals((byte) 0, kline.closeScale());
        assertEquals((byte) 0, kline.highScale());
        assertEquals((byte) 0, kline.lowScale());
    }

    @Test
    void withValues() {
        var kline = KLine.empty();
        kline.openTime(123L);
        kline.closeTime(123L);
        kline.openValue(123L);
        kline.closeValue(123L);
        kline.highValue(123L);
        kline.lowValue(123L);
        kline.openScale((byte) 7);
        kline.closeScale((byte) 7);
        kline.highScale((byte) 7);
        kline.lowScale((byte) 7);
        var kline2 = KLine.emptyVolatile();
        assertNotEquals(kline, kline2);
        kline2.withValues(kline);
        assertEquals(kline, kline2);
    }

    @Test
    void datedClose() {
        var kline = KLine.empty();
        kline.closeTime(123L);
        kline.closeValue(312L);
        kline.closeScale((byte) 7);
        var datedClose = kline.datedClose();
        assertNotNull(datedClose);
        assertEquals(123L, datedClose.dateTime());
        assertNotNull(datedClose.price());
        assertEquals(312L, datedClose.price().value());
        assertEquals((byte) 7, datedClose.price().scale());
    }

    @Test
    void getOpen() {
        var kline = KLine.empty();
        kline.openValue(312L);
        kline.openScale((byte) 7);
        var open = MutableDecimal.empty();
        kline.getOpen(open);
        assertEquals(312L, open.value());
        assertEquals((byte) 7, open.scale());
    }

    @Test
    void getClose() {
        var kline = KLine.empty();
        kline.closeValue(312L);
        kline.closeScale((byte) 7);
        var close = MutableDecimal.empty();
        kline.getClose(close);
        assertEquals(312L, close.value());
        assertEquals((byte) 7, close.scale());
    }

    @Test
    void getHigh() {
        var kline = KLine.empty();
        kline.highValue(312L);
        kline.highScale((byte) 7);
        var high = MutableDecimal.empty();
        kline.getHigh(high);
        assertEquals(312L, high.value());
        assertEquals((byte) 7, high.scale());
    }

    @Test
    void getLow() {
        var kline = KLine.empty();
        kline.lowValue(312L);
        kline.lowScale((byte) 7);
        var low = MutableDecimal.empty();
        kline.getLow(low);
        assertEquals(312L, low.value());
        assertEquals((byte) 7, low.scale());
    }

    @Test
    void testToString() {
        var kline = KLine.empty();
        kline.openTime(123L);
        kline.closeTime(456L);
        kline.openValue(789L);
        kline.closeValue(531L);
        kline.highValue(963L);
        kline.lowValue(926L);
        kline.openScale((byte) 7);
        kline.closeScale((byte) 11);
        kline.highScale((byte) 13);
        kline.lowScale((byte) 33);
        var string = kline.toString();
        assertNotNull(string);
        assertTrue(string.contains("123"));
        assertTrue(string.contains("456"));
        assertTrue(string.contains("789"));
        assertTrue(string.contains("531"));
        assertTrue(string.contains("963"));
        assertTrue(string.contains("926"));
        assertTrue(string.contains("7"));
        assertTrue(string.contains("11"));
        assertTrue(string.contains("13"));
        assertTrue(string.contains("33"));
    }

    @Test
    void testEquals() {
        assertThrows(UnsupportedOperationException.class, () -> KLine.empty().hashCode());
        assertThrows(UnsupportedOperationException.class, () -> KLine.emptyVolatile().hashCode());

        var kline = KLine.empty();
        kline.openTime(123L);
        kline.closeTime(456L);
        kline.openValue(789L);
        kline.closeValue(531L);
        kline.highValue(963L);
        kline.lowValue(926L);
        kline.openScale((byte) 7);
        kline.closeScale((byte) 11);
        kline.highScale((byte) 13);
        kline.lowScale((byte) 33);
        assertFalse(kline.equals(KLine.empty()));

        var kline2 = KLine.empty();
        kline2.openTime(123L);
        kline2.closeTime(456L);
        kline2.openValue(789L);
        kline2.closeValue(531L);
        kline2.highValue(963L);
        kline2.lowValue(926L);
        kline2.openScale((byte) 7);
        kline2.closeScale((byte) 11);
        kline2.highScale((byte) 13);
        kline2.lowScale((byte) 33);
        assertTrue(kline.equals(kline2));

        kline.openTime(1L);
        assertFalse(kline.equals(kline2));
        kline.openTime(123L);
        assertTrue(kline.equals(kline2));
        kline.closeTime(1L);
        assertFalse(kline.equals(kline2));
        kline.closeTime(456L);
        assertTrue(kline.equals(kline2));
        kline.openValue(1L);
        assertFalse(kline.equals(kline2));
        kline.openValue(789L);
        assertTrue(kline.equals(kline2));
        kline.closeValue(1L);
        assertFalse(kline.equals(kline2));
        kline.closeValue(531L);
        assertTrue(kline.equals(kline2));
        kline.highValue(1L);
        assertFalse(kline.equals(kline2));
        kline.highValue(963L);
        assertTrue(kline.equals(kline2));
        kline.lowValue(1L);
        assertFalse(kline.equals(kline2));
        kline.lowValue(926L);
        assertTrue(kline.equals(kline2));
        kline.openScale((byte) 1);
        assertFalse(kline.equals(kline2));
        kline.openScale((byte) 7);
        assertTrue(kline.equals(kline2));
        kline.closeScale((byte) 1);
        assertFalse(kline.equals(kline2));
        kline.closeScale((byte) 11);
        assertTrue(kline.equals(kline2));
        kline.highScale((byte) 1);
        assertFalse(kline.equals(kline2));
        kline.highScale((byte) 13);
        assertTrue(kline.equals(kline2));
        kline.lowScale((byte) 1);
        assertFalse(kline.equals(kline2));
        kline.lowScale((byte) 33);
        assertTrue(kline.equals(kline2));
    }
}