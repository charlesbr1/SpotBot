package org.sbot.entities;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;
import org.sbot.utils.MutableDecimalParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.ClientType.DISCORD;

class FieldParserTest {

    @Test
    void parseALERT_TYPE() {
        assertThrows(NullPointerException.class, () -> FieldParser.Type.ALERT_TYPE.parse(null));
        assertEquals(range, FieldParser.Type.ALERT_TYPE.parse(range.toString()));
    }

    @Test
    void parseALERT_CLIENT_TYPE() {
        assertThrows(NullPointerException.class, () -> FieldParser.Type.ALERT_CLIENT_TYPE.parse(null));
        assertEquals(DISCORD, FieldParser.Type.ALERT_CLIENT_TYPE.parse(DISCORD.shortName));
    }

    @Test
    void parseSTRING() {
        assertThrows(NullPointerException.class, () -> FieldParser.Type.STRING.parse(null));
        assertEquals("AZE", FieldParser.Type.STRING.parse("AZE"));
    }

    @Test
    void parseBYTE() {
        assertThrows(NumberFormatException.class, () -> FieldParser.Type.BYTE.parse(null));
        assertThrows(NumberFormatException.class, () -> FieldParser.Type.BYTE.parse("300"));
        assertEquals((byte) 123, FieldParser.Type.BYTE.parse("123"));
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
        assertEquals(MutableDecimalParser.parse("123"), FieldParser.Type.DECIMAL.parse("123"));
    }

    @Test
    void format() {
        assertEquals("", FieldParser.format(null));
        assertEquals("123", FieldParser.format("123"));
        assertEquals("123", FieldParser.format(MutableDecimalParser.parse("123")));
        assertEquals("123", FieldParser.format((short) 123));
        assertEquals("123", FieldParser.format(123L));
        var now = DatesTest.nowUtc();
        assertEquals("" + now, FieldParser.format(now));
    }
}