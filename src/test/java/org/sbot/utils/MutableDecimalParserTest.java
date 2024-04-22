package org.sbot.utils;

import org.junit.jupiter.api.Test;
import org.sbot.utils.MutableDecimal.ImmutableDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.MutableDecimal.ImmutableDecimal.ZERO;
import static org.sbot.utils.MutableDecimalParser.MAX_COMPACT_DIGITS;
import static org.sbot.utils.MutableDecimalTest.ONE;
import static org.sbot.utils.MutableDecimalTest.TEST_LOOP;

class MutableDecimalParserTest {

    @Test
    void parseCharSequence() {
        assertThrows(NullPointerException.class, () -> MutableDecimalParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> MutableDecimalParser.parse(""));
        assertDoesNotThrow(() -> MutableDecimalParser.parse("1".repeat(MAX_COMPACT_DIGITS)));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("1".repeat(MAX_COMPACT_DIGITS) + 1));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("1e2"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("1 23"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("1a23"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("1.2.3"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("12..3"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("a"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("12x"));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("z12"));

        var value = "0";
        assertEquals(ZERO, MutableDecimalParser.parse(value));
        value = "-0";
        assertEquals(ZERO, MutableDecimalParser.parse(value));
        value = "-000";
        assertEquals(ZERO, MutableDecimalParser.parse(value));
        value = "+0";
        assertEquals(ZERO, MutableDecimalParser.parse(value));
        value = "+00";
        assertEquals(ZERO, MutableDecimalParser.parse(value));
        value = "1";
        assertEquals(ONE, MutableDecimalParser.parse(value));
        value = "+1";
        assertEquals(ONE, MutableDecimalParser.parse(value));
        value = "+0000000000000000000000000000000000000000001";
        assertEquals(ONE, MutableDecimalParser.parse(value));
        var MINUS_ONE = ImmutableDecimal.of(-1L, (byte) 0);
        value = "-1";
        assertEquals(MINUS_ONE, MutableDecimalParser.parse(value));
        value = "-0000000000000000000000000000000000000000001";
        assertEquals(MINUS_ONE, MutableDecimalParser.parse(value));
        var D123 = ImmutableDecimal.of(123L, (byte) 0);
        value = "123";
        assertEquals(D123, MutableDecimalParser.parse(value));
        value = "000000000000000000000000000000000000000000123";
        assertEquals(D123, MutableDecimalParser.parse(value));
        var MD123 = ImmutableDecimal.of(-123L, (byte) 0);
        value = "-123";
        assertEquals(MD123, MutableDecimalParser.parse(value));
        value = "-000000000000000000000000000000000000000000123";
        assertEquals(MD123, MutableDecimalParser.parse(value));
        var DP123 = ImmutableDecimal.of(123456L, (byte) 3);
        value = "123.456";
        assertEquals(DP123, MutableDecimalParser.parse(value));
        value = "+000000000000000000000000000000000000000000123.456";
        assertEquals(DP123, MutableDecimalParser.parse(value));
        var MDP123 = ImmutableDecimal.of(-123456L, (byte) 3);
        value = "-123.456";
        assertEquals(MDP123, MutableDecimalParser.parse(value));
        value = "-000000000000000000000000000000000000000000123.456";
        assertEquals(MDP123, MutableDecimalParser.parse(value));

        var B123 = ImmutableDecimal.of(1234567890123456L, (byte) 1);
        value = "123456789012345.6";
        assertEquals(B123, MutableDecimalParser.parse(value));
        value = "000000000000000000123456789012345.6";
        assertEquals(B123, MutableDecimalParser.parse(value));

        var NB123 = ImmutableDecimal.of(-1234567890123456L, (byte) 1);
        value = "-123456789012345.6";
        assertEquals(NB123, MutableDecimalParser.parse(value));
        value = "-000000000000000000123456789012345.6";
        assertEquals(NB123, MutableDecimalParser.parse(value));

        var L123 = ImmutableDecimal.of(123456L, (byte) 16);
        value = "0.0000000000123456";
        assertEquals(L123, MutableDecimalParser.parse(value));
        value = "+00000000000.0000000000123456";
        assertEquals(L123, MutableDecimalParser.parse(value));

        var NL123 = ImmutableDecimal.of(-123456L, (byte) 16);
        value = "-0.0000000000123456";
        assertEquals(NL123, MutableDecimalParser.parse(value));
        assertNotEquals(ImmutableDecimal.of(-123456L, (byte) 17), MutableDecimalParser.parse(value));
        assertNotEquals(ImmutableDecimal.of(-123456L, (byte) 15), MutableDecimalParser.parse(value));
        value = "-00000000000.0000000000123456";
        assertEquals(NL123, MutableDecimalParser.parse(value));

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10000;
            value = MutableDecimalTest.number(rand, MAX_COMPACT_DIGITS);
            assertEquals(value, MutableDecimalParser.parse(value).toString());
        }
    }

    @Test
    void parse() {
        assertThrows(NullPointerException.class, () -> MutableDecimalParser.parse(null, MutableDecimal.empty(), 0, 3));
        assertThrows(NullPointerException.class, () -> MutableDecimalParser.parse("123", null, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> MutableDecimalParser.parse("12 3", MutableDecimal.empty(), 0, 4));
        assertThrows(IllegalArgumentException.class, () -> MutableDecimalParser.parse("12a3", MutableDecimal.empty(), 0, 4));
        assertThrows(IllegalArgumentException.class, () -> MutableDecimalParser.parse("123", MutableDecimal.empty(), -1, 3));
        assertThrows(IllegalArgumentException.class, () -> MutableDecimalParser.parse("123", MutableDecimal.empty(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> MutableDecimalParser.parse("123", MutableDecimal.empty(), 0, -1));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("1.2.3", MutableDecimal.empty(), 0, 5));
        assertThrows(NumberFormatException.class, () -> MutableDecimalParser.parse("2..3", MutableDecimal.empty(), 0, 4));
        assertThrows(StringIndexOutOfBoundsException.class, () -> MutableDecimalParser.parse("", MutableDecimal.empty(), 0, 1));

        MutableDecimal priceBuffer = MutableDecimal.empty();
        MutableDecimalParser.parse("1", priceBuffer, 0, 1);
        assertEquals(ONE, priceBuffer);
        MutableDecimalParser.parse("123", priceBuffer, 0, 1);
        assertEquals(ONE, priceBuffer);
        MutableDecimalParser.parse("212", priceBuffer, 1, 1);
        assertEquals(ONE, priceBuffer);

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10000;
            var value = MutableDecimalTest.number(rand, MAX_COMPACT_DIGITS);
            value = "t" + value;
            MutableDecimalParser.parse(value, priceBuffer, 1, value.length() - 3);
            assertEquals(value.substring(1, value.length() - 2), priceBuffer.toString());
        }
    }
}