package org.sbot.utils;

import org.junit.jupiter.api.Test;
import org.sbot.utils.MutableDecimal.ImmutableDecimal;

import java.math.BigDecimal;

import static java.math.MathContext.DECIMAL128;
import static java.math.MathContext.DECIMAL64;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.MutableDecimal.LONG_TEN_POWERS_TABLE;
import static org.sbot.utils.MutableDecimal.THRESHOLDS_TABLE;
import static org.sbot.utils.MutableDecimalParser.MAX_COMPACT_DIGITS;

public class MutableDecimalTest {


    public static final MutableDecimal ONE = new ImmutableDecimal(1L, (byte) 0);
    public static final MutableDecimal TWO = new ImmutableDecimal(2L, (byte) 0);
    public static final MutableDecimal TEN = new ImmutableDecimal(10L, (byte) 0);

    static final int TEST_LOOP = 300000;

    @Test
    void TEN_POWERS_TABLES_SIZE() {
        assertEquals(LONG_TEN_POWERS_TABLE.length, THRESHOLDS_TABLE.length);
    }

    @Test
    void empty() {
        assertNotNull(MutableDecimal.empty());
    }

    @Test
    void of() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.value());
        assertEquals((byte) 3, decimal.scale());
    }

    static String number(double rand, int maxLength) {
        if(rand < 0) {
            maxLength++;
        }
        var value = new BigDecimal(rand).toPlainString();
        if(value.length() > maxLength) {
            value = value.substring(0, maxLength);
            if(value.endsWith(".")) {
                value = value.substring(0, value.length() - 1);
            }
        }
        return value;
    }

    @Test
    void bigDecimal() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        var bd = decimal.bigDecimal();
        assertEquals("1.234", bd.toPlainString());
        decimal = MutableDecimal.of(1234000000L, (byte) 3);
        bd = decimal.bigDecimal();
        assertEquals("1234000.000", bd.toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10000000;
            var value = number(rand, MAX_COMPACT_DIGITS);
            bd = MutableDecimalParser.parse(value).bigDecimal();
            assertEquals(value, bd.toPlainString());
        }
    }

    @Test
    void value() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.value());
        assertEquals((byte) 3, decimal.scale());
        decimal.value(431L);
        assertEquals(431L, decimal.value());
        assertEquals((byte) 3, decimal.scale());
    }

    @Test
    void scale() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.value());
        assertEquals((byte) 3, decimal.scale());
        decimal.scale((byte) 7);
        assertEquals(1234L, decimal.value());
        assertEquals((byte) 7, decimal.scale());
    }

    @Test
    void set() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.value());
        assertEquals((byte) 3, decimal.scale());
        decimal.set(431L, (byte) 7);
        assertEquals(431L, decimal.value());
        assertEquals((byte) 7, decimal.scale());
    }

    @Test
    void max() {
        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 10000;
            var rand2 = (Math.random() - 0.5d) * 10 * i;
            var value1 = number(rand1, MAX_COMPACT_DIGITS);
            var value2 = number(rand2, MAX_COMPACT_DIGITS);
            var decimal1 = MutableDecimalParser.parse(value1);
            var decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            decimal1.max(decimal2);
            assertEquals(decimal2, MutableDecimalParser.parse(value2));
            if(new BigDecimal(value1).compareTo(new BigDecimal(value2)) >= 0) {
                assertEquals(decimal1, MutableDecimalParser.parse(value1));
            } else {
                assertEquals(decimal1, MutableDecimalParser.parse(value2));
                assertEquals(decimal1, decimal2);
            }
        }
    }

    @Test
    void compareTo() {
        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 100000;
            var rand2 = (Math.random() - 0.5d) * 10 * i;
            var value1 = number(rand1, MAX_COMPACT_DIGITS);
            var value2 = number(rand2, MAX_COMPACT_DIGITS);
            var decimal1 = MutableDecimalParser.parse(value1);
            var decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            var result = decimal1.compareTo(decimal2);
            assertEquals(result, new BigDecimal(value1).compareTo(new BigDecimal(value2)));
        }
    }

    @Test
    void longDigitLength() {
        assertEquals(1, MutableDecimal.longDigitLength(-0L));
        assertEquals(1, MutableDecimal.longDigitLength(0L));
        assertEquals(1, MutableDecimal.longDigitLength(-1L));
        assertEquals(1, MutableDecimal.longDigitLength(1L));
        assertEquals(1, MutableDecimal.longDigitLength(-9L));
        assertEquals(1, MutableDecimal.longDigitLength(9L));
        assertEquals(2, MutableDecimal.longDigitLength(-10L));
        assertEquals(2, MutableDecimal.longDigitLength(10L));
        assertEquals(2, MutableDecimal.longDigitLength(-99L));
        assertEquals(2, MutableDecimal.longDigitLength(99L));
        assertEquals(3, MutableDecimal.longDigitLength(-100L));
        assertEquals(3, MutableDecimal.longDigitLength(100L));
        assertEquals(3, MutableDecimal.longDigitLength(-999L));
        assertEquals(3, MutableDecimal.longDigitLength(999L));
        assertEquals(4, MutableDecimal.longDigitLength(-1000L));
        assertEquals(4, MutableDecimal.longDigitLength(1000L));
        assertEquals(19, MutableDecimal.longDigitLength(Long.MIN_VALUE));
        assertEquals(19, MutableDecimal.longDigitLength(Long.MIN_VALUE + 1));
        assertEquals(19, MutableDecimal.longDigitLength(Long.MAX_VALUE));
        for(int i = 1; i < LONG_TEN_POWERS_TABLE.length; i++) {
            assertEquals(i + 1, MutableDecimal.longDigitLength(-LONG_TEN_POWERS_TABLE[i]));
            assertEquals(i, MutableDecimal.longDigitLength(-LONG_TEN_POWERS_TABLE[i] + 1));
            assertEquals(i + 1, MutableDecimal.longDigitLength(LONG_TEN_POWERS_TABLE[i]));
            assertEquals(i, MutableDecimal.longDigitLength(LONG_TEN_POWERS_TABLE[i] - 1));
        }
    }

    @Test
    void subtract() {
        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 10 * i;
            var rand2 = (Math.random() - 0.5d) * 10000;
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 5);
            var value2 = number(rand2, MAX_COMPACT_DIGITS - 5);
            var decimal1 = MutableDecimalParser.parse(value1);
            var decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            decimal1.subtract(decimal2);
            assertEquals(decimal2, MutableDecimalParser.parse(value2));
            var resultString = new BigDecimal(value1).subtract(new BigDecimal(value2), DECIMAL128).toPlainString();
            assertTrue(decimal1.toString().startsWith(resultString) || resultString.startsWith(decimal1.toString()));
            if(resultString.length() <= MAX_COMPACT_DIGITS) {
                var result = MutableDecimalParser.parse(resultString);
                assertEquals(decimal1, result);
                while (resultString.contains(".") && resultString.endsWith("0")) {
                    resultString = resultString.substring(0, resultString.length() - 1);
                }
                assertEquals(resultString, decimal1.toString());
            }
        }
    }

    @Test
    void add() {
        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 10000;
            var rand2 = (Math.random() - 0.5d) * 1 * i;
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 5);
            var value2 = number(rand2, MAX_COMPACT_DIGITS - 5);
            var decimal1 = MutableDecimalParser.parse(value1);
            var decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            decimal1.add(decimal2);
            assertEquals(decimal2, MutableDecimalParser.parse(value2));
            var resultString = new BigDecimal(value1).add(new BigDecimal(value2), DECIMAL128).toPlainString();
            assertTrue(decimal1.toString().startsWith(resultString) || resultString.startsWith(decimal1.toString()));
            if(resultString.length() <= MAX_COMPACT_DIGITS) {
                var result = MutableDecimalParser.parse(resultString);
                assertEquals(decimal1, result);
                while (resultString.contains(".") && resultString.endsWith("0")) {
                    resultString = resultString.substring(0, resultString.length() - 1);
                }
                assertEquals(resultString, decimal1.toString());
            }
        }
    }

    @Test
    void multiplyCaped() {
        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 100000;
            var rand2 = (long) ((Math.random() - 0.5d) * 10d * i);
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 9);
            var value2 = number(rand2, MAX_COMPACT_DIGITS - 5);
            var decimal1 = MutableDecimalParser.parse(value1);
            var decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            decimal1.multiplyCaped(rand2);
            var resultString = new BigDecimal(value1).multiply(BigDecimal.valueOf(rand2), DECIMAL64).toPlainString();
            resultString = resultString.substring(0, Math.min(resultString.length(), MAX_COMPACT_DIGITS + (resultString.startsWith("-") ? 1 : 0)));
            var result = MutableDecimalParser.parse(resultString);
            assertEquals(decimal1.scale, result.scale);
            assertTrue(Math.abs(decimal1.value - result.value) <= 1);
            assertEquals(resultString, decimal1.toString());
        }
    }

    @Test
    void divideCaped() {
        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 111110.333000123d;
            var rand2 = (long) ((Math.random() - 0.5d) * 10d * i);
            if(0L == rand2) {
                rand2 = Math.min(31, (long) ((Math.random() - 0.5d) * 10d * i));
            }
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 9);
            var decimal1 = MutableDecimalParser.parse(value1);
            if(0L == rand2) {
                rand2 = Math.max(31, (long) ((Math.random() - 0.5d) * 10d * i));
            }
            decimal1.divideCaped(rand2);
            String s1 = decimal1.toString();
            String s2 = new BigDecimal(value1).divide(BigDecimal.valueOf(rand2), DECIMAL128).toPlainString();

            if(s2.length() > s1.length()) {
                s2 = s2.substring(0, s1.length() - 1);
                s1 = s1.substring(0, s1.length() - 1);
            } else if(s1.length() > s2.length()) {
                fail();
            }
            assertEquals(s1, s2);
        }
    }

    @Test
    void setOverflow() {
        var decimal = MutableDecimal.empty();
        decimal.setOverflow(true, (byte) 0);
        assertEquals(Long.MAX_VALUE, decimal.value);
        assertEquals(Byte.MIN_VALUE, decimal.scale);
        decimal.setOverflow(false, (byte) 0);
        assertEquals(Long.MIN_VALUE + 1, decimal.value);
        assertEquals(Byte.MIN_VALUE, decimal.scale);
        decimal.setOverflow(true, Byte.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, decimal.value);
        assertEquals(Byte.MIN_VALUE, decimal.scale);
        decimal.setOverflow(false, Byte.MAX_VALUE - 1);
        assertEquals(Long.MIN_VALUE + 1, decimal.value);
        assertEquals(Byte.MIN_VALUE, decimal.scale);
        decimal.setOverflow(true, Byte.MAX_VALUE);
        assertEquals(0L, decimal.value);
        assertEquals((byte) 0, decimal.scale);
        decimal.setOverflow(false, Byte.MAX_VALUE);
        assertEquals(0L, decimal.value);
        assertEquals((byte) 0, decimal.scale);
        decimal.setOverflow(true, Byte.MAX_VALUE + 1);
        assertEquals(0L, decimal.value);
        assertEquals((byte) 0, decimal.scale);
        decimal.setOverflow(false, Byte.MAX_VALUE + 3);
        assertEquals(0L, decimal.value);
        assertEquals((byte) 0, decimal.scale);
    }

    @Test
    void toStringTest() {
        var value = "1.2345678912345";
        assertEquals(value, MutableDecimalParser.parse(value).toString());

        value = "-12345678912345";
        assertEquals(value, MutableDecimalParser.parse(value).toString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10 * i;
            value = number(rand, MAX_COMPACT_DIGITS);
            assertEquals(value, MutableDecimalParser.parse(value).toString());
        }
    }

    @Test
    void toPlainString() {
        StringBuilder sb = new StringBuilder();
        var value = "1.2345678912345";
        MutableDecimalParser.parse(value).toPlainString(sb);
        assertEquals(value, sb.toString());
        sb.setLength(0);

        value = "-12345678912345";
        MutableDecimalParser.parse(value).toPlainString(sb);
        assertEquals(value, sb.toString());
        sb.setLength(0);

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 100 * i;
            value = number(rand, MAX_COMPACT_DIGITS);
            MutableDecimalParser.parse(value).toPlainString(sb);
            assertEquals(value, sb.toString());
            sb.setLength(0);
        }
    }

    @Test
    void equals() {
        assertEquals(MutableDecimal.of(10L, (byte) 1), MutableDecimal.of(1L, (byte) 0));
        assertEquals(MutableDecimal.of(10L, (byte) 0), MutableDecimal.of(1L, (byte) -1));
        assertEquals(MutableDecimal.of(1000L, (byte) 0), MutableDecimal.of(1L, (byte) -3));
        assertEquals(MutableDecimal.of(10L, (byte) 1), MutableDecimal.of(100L, (byte) 2));
        assertEquals(MutableDecimal.of(12L, (byte) 1), MutableDecimal.of(120L, (byte) 2));
        assertEquals(MutableDecimal.of(10L, (byte) 1), MutableDecimal.of(10000L, (byte) 4));
        assertEquals(MutableDecimal.of(0L, (byte) 1), MutableDecimal.of(0L, (byte) -14));
        assertEquals(MutableDecimal.of(0L, (byte) 0), MutableDecimal.of(0L, (byte) 14));
        assertEquals(MutableDecimal.of(0L, (byte) 0).hashCode(), MutableDecimal.of(0L, (byte) 14).hashCode());
        assertEquals(MutableDecimal.of(12L, (byte) 0).hashCode(), MutableDecimal.of(12L, (byte) 0).hashCode());
        assertNotEquals(MutableDecimal.of(12L, (byte) 0).hashCode(), MutableDecimal.of(21L, (byte) 0).hashCode());
        assertNotEquals(MutableDecimal.of(12L, (byte) 0).hashCode(), MutableDecimal.of(12L, (byte) 1).hashCode());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10d * i;
            if(0L == rand) {
                rand = Math.max(31, (long) ((Math.random() - 0.5d) * 10d * i));
            }
            var value = number(rand, MAX_COMPACT_DIGITS);
            var decimal = MutableDecimalParser.parse(value);
            var same = MutableDecimal.of(decimal.value(), decimal.scale());
            assertTrue(same.equals(decimal));
            assertEquals(decimal.hashCode(), same.hashCode());
            same.multiplyCaped(10L);
            assertNotEquals(decimal, same);
            same.divideCaped(10L);
            assertTrue(decimal.equals(same));
            assertTrue(same.equals(decimal));
            assertEquals(decimal.hashCode(), same.hashCode());
            same.add(TWO);
            assertNotEquals(decimal, same);
            assertNotEquals(decimal.hashCode(), same.hashCode());
            same.subtract(TWO);
            assertEquals(decimal, same);
            assertEquals(decimal.hashCode(), same.hashCode());
        }
    }
}