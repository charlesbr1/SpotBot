package org.sbot.entities;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.entities.alerts.Alert.Type.range;

class FieldParserTest {

    @Test
    void parseALERT_TYPE() {
        assertThrows(NullPointerException.class, () -> FieldParser.Type.ALERT_TYPE.parse(null));
        assertEquals(range, FieldParser.Type.ALERT_TYPE.parse("range"));
    }

    @Test
    void parseSTRING() {
        assertThrows(NullPointerException.class, () -> FieldParser.Type.STRING.parse(null));
        assertEquals("AZE", FieldParser.Type.STRING.parse("AZE"));
    }

    @Test
    void parseSHORT() {
        assertThrows(NumberFormatException.class, () -> FieldParser.Type.SHORT.parse(null));
        assertEquals((short) 123, FieldParser.Type.SHORT.parse("123"));
    }

    @Test
    void parseLONG() {
        assertThrows(NumberFormatException.class, () -> FieldParser.Type.LONG.parse(null));
        assertEquals(123L, FieldParser.Type.LONG.parse("123"));
    }

    @Test
    void parseDECIMAL() {
        assertThrows(NullPointerException.class, () -> FieldParser.Type.DECIMAL.parse(null));
        assertEquals(new BigDecimal("123"), FieldParser.Type.DECIMAL.parse("123"));
    }

    @Test
    void parseZONED_DATE_TIME() {
        var now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MILLIS);
        assertThrows(NumberFormatException.class, () -> FieldParser.Type.ZONED_DATE_TIME.parse(null));
        assertEquals(now, FieldParser.Type.ZONED_DATE_TIME.parse("" + now.toInstant().toEpochMilli()));
    }

    @Test
    void format() {
        assertEquals("", FieldParser.format(null));
        assertEquals("123", FieldParser.format("123"));
        assertEquals("123", FieldParser.format(new BigDecimal("123")));
        assertEquals("123", FieldParser.format((short) 123));
        assertEquals("123", FieldParser.format(123L));
        var now = DatesTest.nowUtc();
        assertEquals("" + now.toInstant().toEpochMilli(), FieldParser.format(now));
    }
}