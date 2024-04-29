package org.sbot.utils;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.sbot.utils.MutableDecimal.ImmutableDecimal;

import java.math.BigDecimal;
import java.math.MathContext;

import static java.math.MathContext.DECIMAL128;
import static java.math.MathContext.DECIMAL64;
import static java.math.RoundingMode.HALF_UP;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.MutableDecimal.Maths.LONG_TEN_POWERS_TABLE;
import static org.sbot.utils.MutableDecimal.Maths.THRESHOLDS_TABLE;
import static org.sbot.utils.MutableDecimalParser.MAX_COMPACT_DIGITS;

public class MutableDecimalTest {


    public static final MutableDecimal ONE = new ImmutableDecimal(1L, (byte) 0);
    public static final MutableDecimal TWO = new ImmutableDecimal(2L, (byte) 0);
    public static final MutableDecimal TEN = new ImmutableDecimal(10L, (byte) 0);

    static final int TEST_LOOP = 10000000;

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
        assertEquals(1234L, decimal.mantissa());
        assertEquals((byte) 3, decimal.exp());
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
        var decimal = MutableDecimal.of(1234L, (byte) -3);
        var bd = decimal.bigDecimal();
        assertEquals("1.234", bd.toPlainString());
        decimal = MutableDecimal.of(1234000000L, (byte) -3);
        bd = decimal.bigDecimal();
        assertEquals("1234000.000", bd.toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10 * i;
            var value = number(rand, MAX_COMPACT_DIGITS);
            bd = MutableDecimalParser.parse(value).bigDecimal();
            assertEquals(value, bd.toPlainString());
        }
    }

    @Test
    void value() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.mantissa());
        assertEquals((byte) 3, decimal.exp());
        decimal.mantissa(431L);
        assertEquals(431L, decimal.mantissa());
        assertEquals((byte) 3, decimal.exp());
    }

    @Test
    void scale() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.mantissa());
        assertEquals((byte) 3, decimal.exp());
        decimal.exp((byte) 7);
        assertEquals(1234L, decimal.mantissa());
        assertEquals((byte) 7, decimal.exp());
    }

    @Test
    void set() {
        var decimal = MutableDecimal.of(1234L, (byte) 3);
        assertEquals(1234L, decimal.mantissa());
        assertEquals((byte) 3, decimal.exp());
        decimal.set(431L, (byte) 7);
        assertEquals(431L, decimal.mantissa());
        assertEquals((byte) 7, decimal.exp());
    }

    @Test
    void shrunkScale() {
        //TODO
    }

    @Test
    void max() {

        var decimal1 = MutableDecimal.empty();
        var decimal2 = MutableDecimal.empty();
        decimal1.mantissa(Long.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        var bd = decimal1.bigDecimal();
        var bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());
        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal2.mantissa(1000000L);
        decimal1.exp((byte) 45);
        decimal2.exp((byte) - 7);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.max(decimal2);
        assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 10000;
            var rand2 = (Math.random() - 0.5d) * 10 * i;
            var value1 = number(rand1, MAX_COMPACT_DIGITS);
            var value2 = number(rand2, MAX_COMPACT_DIGITS);
            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            bd = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            assertNotEquals(decimal1, decimal2);
            decimal1.max(decimal2);
            assertEquals(decimal2, MutableDecimalParser.parse(value2));
            assertEquals(decimal1.toString(), bd.max(bd2).toPlainString());
            if(new BigDecimal(value1).compareTo(new BigDecimal(value2)) >= 0) {
                assertEquals(decimal1, MutableDecimalParser.parse(value1));
            } else {
                assertEquals(decimal1, MutableDecimalParser.parse(value2));
                assertEquals(decimal1, decimal2);
            }

            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            rand1 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            rand2 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            decimal1.exp((byte) rand1);
            decimal2.exp((byte) rand2);
            bd = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            assertNotEquals(decimal1, decimal2);
            decimal1.max(decimal2);
            decimal1.shrinkMantissa();
            assertEquals(decimal1.toString(), bd.max(bd2).stripTrailingZeros().toPlainString());
        }
    }

    @Test
    void compareTo() {
        var decimal1 = MutableDecimal.empty();
        var decimal2 = MutableDecimal.empty();
        decimal1.mantissa(Long.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        var bd = decimal1.bigDecimal();
        var bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.mantissa(Long.MIN_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.mantissa(Long.MIN_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        decimal1.mantissa(Long.MIN_VALUE);
        decimal2.mantissa(1000000L);
        decimal1.exp((byte) 45);
        decimal2.exp((byte) - 7);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 100000;
            var rand2 = (Math.random() - 0.5d) * 10 * i;
            var value1 = number(rand1, MAX_COMPACT_DIGITS);
            var value2 = number(rand2, MAX_COMPACT_DIGITS);
            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            var result = decimal1.compareTo(decimal2);
            assertEquals(result, new BigDecimal(value1).compareTo(new BigDecimal(value2)));

            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            rand1 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            rand2 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            decimal1.exp((byte) rand1);
            decimal2.exp((byte) rand2);
            bd = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            assertNotEquals(decimal1, decimal2);
            if(decimal1.compareTo(decimal2) != bd.compareTo(bd2)) {
                System.err.println("v " + value1 + "\nv2 " + value2 + "\nr1 " + rand1 + "\nr2 " + rand2 + "\nd1 " + decimal1 + "\nd2 " + decimal2 + "\nbd1 " + bd + "\nbd2 " + bd2);
            }
            assertEquals(decimal1.compareTo(decimal2), bd.compareTo(bd2));
        }
    }

    @Test
    void digitLength() {
        assertEquals(1, MutableDecimal.Maths.digitLength(-0L));
        assertEquals(1, MutableDecimal.Maths.digitLength(0L));
        assertEquals(1, MutableDecimal.Maths.digitLength(-1L));
        assertEquals(1, MutableDecimal.Maths.digitLength(1L));
        assertEquals(1, MutableDecimal.Maths.digitLength(-9L));
        assertEquals(1, MutableDecimal.Maths.digitLength(9L));
        assertEquals(2, MutableDecimal.Maths.digitLength(-10L));
        assertEquals(2, MutableDecimal.Maths.digitLength(10L));
        assertEquals(2, MutableDecimal.Maths.digitLength(-99L));
        assertEquals(2, MutableDecimal.Maths.digitLength(99L));
        assertEquals(3, MutableDecimal.Maths.digitLength(-100L));
        assertEquals(3, MutableDecimal.Maths.digitLength(100L));
        assertEquals(3, MutableDecimal.Maths.digitLength(-999L));
        assertEquals(3, MutableDecimal.Maths.digitLength(999L));
        assertEquals(4, MutableDecimal.Maths.digitLength(-1000L));
        assertEquals(4, MutableDecimal.Maths.digitLength(1000L));
        assertEquals(19, MutableDecimal.Maths.digitLength(Long.MIN_VALUE));
        assertEquals(19, MutableDecimal.Maths.digitLength(Long.MIN_VALUE + 1));
        assertEquals(19, MutableDecimal.Maths.digitLength(Long.MAX_VALUE));
        for(int i = 1; i < LONG_TEN_POWERS_TABLE.length; i++) {
            assertEquals(i + 1, MutableDecimal.Maths.digitLength(-LONG_TEN_POWERS_TABLE[i]));
            assertEquals(i, MutableDecimal.Maths.digitLength(-LONG_TEN_POWERS_TABLE[i] + 1));
            assertEquals(i + 1, MutableDecimal.Maths.digitLength(LONG_TEN_POWERS_TABLE[i]));
            assertEquals(i, MutableDecimal.Maths.digitLength(LONG_TEN_POWERS_TABLE[i] - 1));
        }
    }

    @Test
    void subtractCaped() {
        var decimal1 = MutableDecimalParser.parse("-398865106.256");
        var decimal2 = MutableDecimalParser.parse("-1.09381767178");
        var bd = decimal1.bigDecimal();
        var bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        var resultString = bd.subtract(bd2, DECIMAL128).setScale(10, HALF_UP).toPlainString();
        assertEquals(decimal1.toString(), resultString);

        decimal1 = MutableDecimalParser.parse("125657240.880");
        decimal2 = MutableDecimalParser.parse("-9.80988499554");
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2); // -398865105.1561823283, bd : -398865105.16218232822
        resultString = bd.subtract(bd2, DECIMAL128).setScale(10, HALF_UP).toPlainString();
        assertEquals(decimal1.toString(), resultString);

        decimal1.set(-813899096289L, (byte) -11);
        decimal2.set(444340702592L, (byte) -20);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        // -8.138990967333407026
        // -8.13899096733340702592
        assertEquals(decimal1.toString(), bd.subtract(bd2).setScale(18, HALF_UP).toPlainString());

        decimal1 = MutableDecimal.empty();
        decimal2 = MutableDecimal.empty();
        decimal1.mantissa(Long.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2).toPlainString());
        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2).stripTrailingZeros().toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2).stripTrailingZeros().toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2).stripTrailingZeros().toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString().substring(0, "922337203685477580".length()), bd.subtract(bd2, DECIMAL128).toPlainString().substring(0, "922337203685477580".length()));

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2).stripTrailingZeros().toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        var fdec1 = MutableDecimal.of(decimal1.mantissa, decimal1.exp);
        var fdec2 = MutableDecimal.of(decimal2.mantissa, decimal2.exp);
        // exponent overflow ( scale > Byte.MAX_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.subtractCaped(fdec2));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).stripTrailingZeros().toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString().substring(0, "922337203685477580".length()), bd.subtract(bd2, DECIMAL128).toPlainString().substring(0, "922337203685477580".length()));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).setScale(127, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).setScale(127, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        fdec1.set(decimal1.mantissa, decimal1.exp);
        fdec2.set(decimal2.mantissa, decimal2.exp);
        // exponent overflow ( scale > Byte.MAX_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.subtractCaped(fdec2));

        decimal1.mantissa(Long.MIN_VALUE);
        decimal2.mantissa(1000000L);
        decimal1.exp((byte) 45);
        decimal2.exp((byte) - 7);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.subtractCaped(decimal2);
        assertEquals(decimal1.toString(), bd.subtract(bd2, DECIMAL128).stripTrailingZeros().toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 10 * i;
            var rand2 = (Math.random() - 0.5d) * 10000;
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 5);
            var value2 = number(rand2, MAX_COMPACT_DIGITS - 5);
            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal1, decimal2);
            decimal1.subtractCaped(decimal2);
            assertEquals(decimal2, MutableDecimalParser.parse(value2));
            resultString = new BigDecimal(value1).subtract(new BigDecimal(value2), DECIMAL128).setScale(-decimal1.exp, HALF_UP).stripTrailingZeros().toPlainString();
            assertEquals(decimal1.toString(), resultString);

            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            rand1 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            rand2 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            decimal1.exp((byte) rand1);
            decimal2.exp((byte) rand2);
            var tmp = MutableDecimal.empty().set(decimal1.mantissa, decimal1.exp);
            bd = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            assertNotEquals(decimal1, decimal2);
            decimal1.subtractCaped(decimal2);
            var size = decimal1.toString().length();
            var decimalResult = decimal1.toString().substring(0, Math.min(7, size - 4));
            resultString = bd.subtract(bd2, DECIMAL128).stripTrailingZeros().toPlainString().substring(0, Math.min(7, size - 4));
            if(!decimalResult.equals(resultString)) {
                assertTrue(decimal1.exp > (byte) -16);
            }
        }
    }

    @Test
    void addCaped() {
        var decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MAX_VALUE);
        var decimal2 = MutableDecimal.empty();
        decimal2.mantissa(Long.MAX_VALUE);
        var bd = decimal.bigDecimal();
        var bd2 = decimal2.bigDecimal();
        decimal.addCaped(decimal2);
        var decimalResult = decimal.toString();
        var bdResult = bd.add(bd2, DECIMAL128).toPlainString();
        assertEquals(decimalResult.length(), bdResult.length());
        assertEquals(decimalResult.substring(0, "1844674407370955161".length()), bdResult.substring(0, "1844674407370955161".length()));

        decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MIN_VALUE);
        decimal2 = MutableDecimal.empty();
        decimal2.mantissa(Long.MAX_VALUE);
        bd = decimal.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal.addCaped(decimal2);
        assertEquals("-1", decimal.toString());
        assertEquals(decimal.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MAX_VALUE);
        decimal2 = MutableDecimal.empty();
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal.addCaped(decimal2);
        assertEquals("-1", decimal.toString());
        assertEquals(decimal.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MIN_VALUE);
        decimal2 = MutableDecimal.empty();
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal.addCaped(decimal2);
        decimalResult = decimal.toString();
        bdResult = bd.add(bd2, DECIMAL128).toPlainString();
        assertEquals(decimalResult.length(), bdResult.length());
        assertEquals(decimalResult.substring(0, "-184467440737095516".length()), bdResult.substring(0, "-184467440737095516".length()));

        decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MIN_VALUE);
        decimal.exp((byte) 60);
        decimal2 = MutableDecimal.empty();
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal.addCaped(decimal2);
        decimalResult = decimal.toString();
        bdResult = bd.add(bd2, DECIMAL128).toPlainString();
        assertEquals(decimalResult, bdResult);

        decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MIN_VALUE);
        decimal.exp((byte) -80);
        decimal2 = MutableDecimal.empty();
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal.addCaped(decimal2);
        decimalResult = decimal.toString();
        bdResult = bd.add(bd2, DECIMAL128).stripTrailingZeros().toPlainString();
        assertEquals(decimalResult, bdResult);

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp((byte) -18); // value = -0.100902777777777777
        bd = decimal.bigDecimal();
        decimal.addCaped(TEN);
        assertEquals("9.89909722222222222", decimal.toString());
        assertEquals(decimal.toString(), bd.add(BigDecimal.TEN, DECIMAL128).setScale(17, HALF_UP).toPlainString());

        // test extrem values
        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MIN_VALUE); // value = -0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100902777777777777
        bd = decimal.bigDecimal();
        decimal.addCaped(TEN);
        assertEquals("10", decimal.toString());
        assertEquals(decimal.toString(), bd.add(BigDecimal.TEN, DECIMAL64).stripTrailingZeros().toPlainString());
        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MIN_VALUE); // value = -0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100902777777777777
        var ten = MutableDecimalParser.parse("10");
        ten.addCaped(decimal);
        assertEquals("10", ten.toString());

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MAX_VALUE); // value = -1009027777777777770000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
        bd = decimal.bigDecimal();
        decimal.addCaped(TEN);
        assertEquals(decimal.toString().substring(0, 16), bd.add(BigDecimal.TEN, DECIMAL64).toPlainString().substring(0, 16));
        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MAX_VALUE); // value = -1.00902777777777777E-109
        ten = MutableDecimalParser.parse("10");
        ten.addCaped(decimal);
        assertEquals(decimal.toString(), ten.toString());

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MIN_VALUE); // value = -10090277777777777700000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
        var other = MutableDecimalParser.parse("900902777777777777");
        other.exp(Byte.MIN_VALUE);
        bd = decimal.bigDecimal();
        var otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertTrue(bd.add(otherBd).toPlainString().startsWith(decimal.toString()));

        decimal = MutableDecimalParser.parse("400902777777777777");
        decimal.exp(Byte.MIN_VALUE);
        other = MutableDecimalParser.parse("700902777777777777");
        other.exp(Byte.MIN_VALUE);
        bd = decimal.bigDecimal();
        otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertEquals(decimal.toString(), bd.add(otherBd).toPlainString());

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MIN_VALUE); // value = -1.00902777777777777E-110
        other = MutableDecimalParser.parse("-900902777777777777");
        other.exp(Byte.MAX_VALUE);
        bd = decimal.bigDecimal();
        otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertEquals(decimal.toString(), bd.add(otherBd).toPlainString().substring(0, bd.add(otherBd).toPlainString().indexOf(".")));

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp(Byte.MIN_VALUE); // value = -1.00902777777777777E-110
        other = MutableDecimalParser.parse("900902777777777777");
        other.exp(Byte.MAX_VALUE);
        bd = decimal.bigDecimal();
        otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertEquals(decimal.toString(), bd.add(otherBd, DECIMAL128).toPlainString());

        decimal = MutableDecimalParser.parse("700902777777777777");
        decimal.exp(Byte.MAX_VALUE);
        other = MutableDecimalParser.parse("900902777777777777");
        other.exp(Byte.MAX_VALUE);
        bd = decimal.bigDecimal();
        otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertEquals(decimal.toString(), bd.add(otherBd).toPlainString());

        decimal = MutableDecimalParser.parse("-300902777777777777");
        decimal.exp(Byte.MAX_VALUE);
        other = MutableDecimalParser.parse("900902777777777777");
        other.exp(Byte.MAX_VALUE);
        bd = decimal.bigDecimal();
        otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertEquals(decimal.toString(), bd.add(otherBd).toPlainString());

        decimal = MutableDecimalParser.parse("-900902777777777777");
        decimal.exp(Byte.MAX_VALUE);
        other = MutableDecimalParser.parse("-900902777777777777");
        other.exp(Byte.MAX_VALUE);
        bd = decimal.bigDecimal();
        otherBd = other.bigDecimal();
        decimal.addCaped(other);
        assertEquals(decimal.toString(), bd.add(otherBd).toPlainString());

        // more tests

        var decimal1 = MutableDecimal.empty();
        decimal2 = MutableDecimal.empty();
        decimal1.mantissa(Long.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(minusLastChar(decimal1.toString()), minusLastChar(bd.add(bd2).toPlainString()));
        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        var fdec1 = MutableDecimal.of(decimal1.mantissa, decimal1.exp);
        var fdec2 = MutableDecimal.of(decimal2.mantissa, decimal2.exp);
        // exponent overflow ( scale > Byte.MAX_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.addCaped(fdec2));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2).toPlainString().substring(0, bd.add(bd2).toPlainString().indexOf(".")));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2).toPlainString().substring(0, bd.add(bd2).toPlainString().indexOf(".")));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2).setScale(127, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2).setScale(127, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        fdec1.set(decimal1.mantissa, decimal1.exp);
        fdec2.set(decimal2.mantissa, decimal2.exp);
        // exponent overflow( scale > Byte.MAX_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.addCaped(fdec2));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, MathContext.DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MIN_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.exp(Byte.MIN_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        decimal2.mantissa(Long.MAX_VALUE);
        decimal2.exp(Byte.MAX_VALUE);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal2.mantissa(1000000L);
        decimal1.exp((byte) 45);
        decimal2.exp((byte) - 7);
        bd = decimal1.bigDecimal();
        bd2 = decimal2.bigDecimal();
        decimal1.addCaped(decimal2);
        assertEquals(decimal1.toString(), bd.add(bd2, DECIMAL128).stripTrailingZeros().toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 10000;
            var rand2 = (Math.random() - 0.5d) * 1 * i;
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 5);
            var value2 = number(rand2, MAX_COMPACT_DIGITS - 5);
            decimal = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            assertNotEquals(decimal, decimal2);
            decimal.addCaped(decimal2);
            assertEquals(decimal2, MutableDecimalParser.parse(value2));
            var resultString = new BigDecimal(value1).add(new BigDecimal(value2), DECIMAL128).stripTrailingZeros().toPlainString();
            assertEquals(decimal.toString(), resultString);

            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            rand1 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            rand2 = 2 * (Math.random() - 0.5d) * (Byte.MAX_VALUE - 1);
            decimal1.exp((byte) rand1);
            decimal2.exp((byte) rand2);
            bd = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            assertNotEquals(decimal1, decimal2);
            decimal1.addCaped(decimal2);
            var size = decimal1.toString().length();
            decimalResult = decimal1.toString().substring(0, Math.min(7, size - 4));
            resultString = bd.add(bd2, DECIMAL128).stripTrailingZeros().toPlainString().substring(0, Math.min(7, size - 4));
            if(!decimalResult.equals(resultString)) {
                assertTrue(decimal1.exp > (byte) -16);
            }
        }
    }

    private static String minusLastChar(@NotNull String value) {
        return value.substring(0, value.length() - 1);
    }

    @Test
    void multiply() {

        for (int i = TEST_LOOP; i-- != 0; ) {
            var rand1 = (Math.random() - 0.5d) * 10000000;
            double rand2 = (long) ((Math.random() - 0.5d) * 10d * i);
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 7);
            var value2 = number(rand2, MAX_COMPACT_DIGITS - 9);
            var decimal1 = MutableDecimalParser.parse(value1);
            var decimal2 = MutableDecimalParser.parse(value2);
            var bd1 = decimal1.bigDecimal();
            var bd2 = decimal2.bigDecimal();
            decimal1.multiplyExact(decimal2);
            decimal1.shrinkMantissa();
            assertEquals(decimal1.toString(), bd1.multiply(bd2, DECIMAL128).setScale(-decimal1.exp, HALF_UP).stripTrailingZeros().toPlainString());

            rand2 = ((10d * Math.random()) - 5d) * i;
            value2 = number(rand2, MAX_COMPACT_DIGITS - 9);
            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            bd1 = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            decimal1.multiplyExact(decimal2);
            decimal1.shrinkMantissa();
            assertEquals(decimal1.toString(), bd1.multiply(bd2, DECIMAL128).setScale(-decimal1.exp, HALF_UP).stripTrailingZeros().toPlainString());

            decimal1 = MutableDecimalParser.parse(value1);
            decimal2 = MutableDecimalParser.parse(value2);
            byte randScale = (byte) (2 * ((Math.random() - 0.5d) * (Byte.MAX_VALUE - 1)));
            decimal1.exp(randScale);
            randScale = (byte) (2 * ((Math.random() - 0.5d) * (Byte.MAX_VALUE - 1)));
            decimal2.exp(randScale);
            bd1 = decimal1.bigDecimal();
            bd2 = decimal2.bigDecimal();
            try {
                decimal1.multiplyExact(decimal2);
                decimal1.shrinkMantissa();
                assertEquals(decimal1.toString(), bd1.multiply(bd2, DECIMAL128).setScale(-decimal1.exp, HALF_UP).stripTrailingZeros().toPlainString());
            } catch (ArithmeticException e) {
                // overflow // TODO
            }
        }
    }

    @Test
    void multiplyCaped() {
        var decimal = MutableDecimalParser.parse("15");
        decimal.multiplyCaped(2L);
        assertEquals("30", decimal.toString());
        decimal = MutableDecimalParser.parse("15");
        decimal.multiplyCaped(4L);
        assertEquals("60", decimal.toString());
        decimal = MutableDecimalParser.parse("15");
        decimal.multiplyCaped(8L);
        assertEquals("120", decimal.toString());
        decimal = MutableDecimalParser.parse("15");
        decimal.multiplyCaped(16L);
        assertEquals("240", decimal.toString());

        for(long v = 1, i = 1; i < 63; i++) {
            decimal = MutableDecimalParser.parse("15");
            decimal.multiplyCaped(v *= 2);
            assertEquals(BigDecimal.valueOf(15L).multiply(BigDecimal.valueOf(v)).toPlainString(), decimal.toString());
        }

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp((byte) -18); // value = -0.100902777777777777
        var bd = decimal.bigDecimal();
        decimal.multiplyCaped(10L);
        assertEquals("-1.00902777777777777", decimal.toString());
        assertEquals(decimal.toString(), bd.multiply(BigDecimal.TEN, MathContext.DECIMAL128).stripTrailingZeros().toPlainString());

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp((byte) 18); // value = -100902777777777777000000000000000000
        bd = decimal.bigDecimal();
        decimal.multiplyCaped(10L);
        var bdResult = bd.multiply(BigDecimal.TEN, DECIMAL128).toPlainString();
        assertEquals(bdResult, decimal.toString());
        assertEquals(decimal.toString(), bdResult);

        var decimal1 = MutableDecimal.empty();
        long value;
        decimal1.mantissa(Long.MAX_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        var bd2 = BigDecimal.valueOf(value);
        decimal1.multiplyCaped(value);
        assertEquals(decimal1.toString().length(), bd.multiply(bd2).toPlainString().length());
        assertTrue(bd.multiply(bd2).toPlainString().startsWith(decimal1.toString().replace("50000000000000000000", "")));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MAX_VALUE;
        var fdec1 = MutableDecimal.of(decimal1.mantissa, decimal1.exp);
        var fvalue = value;
        // exponent overflow ( scale > Byte.MIN_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.multiplyCaped(fvalue));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.multiplyCaped(value);
        assertEquals(decimal1.toString(), bd.multiply(bd2).setScale(127 - 18, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        fdec1.set(decimal1.mantissa, decimal1.exp);
        decimal1.multiplyCaped(value);
        assertEquals(decimal1.toString(), bd.multiply(bd2).setScale(127 - 18, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MAX_VALUE;
        fdec1.set(decimal1.mantissa, decimal1.exp);
        // exponent overflow ( scale > Byte.MIN_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.multiplyCaped(Long.MAX_VALUE));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MIN_VALUE;
        fdec1.set(decimal1.mantissa, decimal1.exp);
        // exponent overflow ( scale > Byte.MIN_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.multiplyCaped(Long.MIN_VALUE));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.multiplyCaped(value);
        assertEquals(decimal1.toString(), bd.multiply(bd2).setScale(127 - 18, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MIN_VALUE;
        fdec1.set(decimal1.mantissa, decimal1.exp);
        // exponent overflow ( scale > Byte.MIN_VALUE )
        assertThrows(ArithmeticException.class, () -> fdec1.multiplyCaped(Long.MIN_VALUE));

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.multiplyCaped(value);
        assertEquals(decimal1.toString(), bd.multiply(bd2).setScale(127 - 18, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        value = 1000000L;
        decimal1.exp((byte) 45);
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.multiplyCaped(value);
        assertEquals(decimal1.toString(), bd.multiply(bd2, DECIMAL128).stripTrailingZeros().toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 100000;
            var rand2 = (long) ((Math.random() - 0.5d) * 10d * i);
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 9);
            decimal1 = MutableDecimalParser.parse(value1);
            decimal1.multiplyCaped(rand2);
            decimal1.shrinkMantissa();
            assertEquals(decimal1.toString(), new BigDecimal(value1).multiply(BigDecimal.valueOf(rand2), DECIMAL128).setScale(-decimal1.exp, HALF_UP).stripTrailingZeros().toPlainString());

            decimal1 = MutableDecimalParser.parse(value1);
            byte randScale = (byte) (2 * ((Math.random() - 0.5d) * (Byte.MAX_VALUE - 1)));
            decimal1.exp(randScale);
            bd = decimal1.bigDecimal();
            decimal1.multiplyCaped(rand2);
            var decimalResult = decimal1.toString();
            var resultString = bd.multiply(BigDecimal.valueOf(rand2), DECIMAL128).toPlainString();
            if(!decimalResult.equals(resultString)) {
                var size = Math.min(decimalResult.length(), resultString.length()) - 1;
                decimalResult = decimalResult.substring(0, size);
                resultString = resultString.substring(0, size);
                assertEquals(decimalResult, resultString);
            }
        }
    }

    @Test
    void divideCaped() {
        var decimal = MutableDecimalParser.parse("30");
        decimal.divideCaped(2L);
        assertEquals("15", decimal.toString());
        decimal = MutableDecimalParser.parse("30");
        decimal.divideCaped(-2L);
        assertEquals("-15", decimal.toString());
        decimal = MutableDecimalParser.parse("60");
        decimal.divideCaped(4L);
        assertEquals("15", decimal.toString());
        decimal = MutableDecimalParser.parse("120");
        decimal.divideCaped(8L);
        assertEquals("15", decimal.toString());
        decimal = MutableDecimalParser.parse("240");
        decimal.divideCaped(16L);
        assertEquals("15", decimal.toString());

        for(long v = 1, i = 1; i < 63; i++) {
            v *= 2;
            boolean overflow = false;
            var r = 0L;
            try {
                r = Math.multiplyExact(15L, v);
            } catch (ArithmeticException e) {
            overflow = true;
            }
            if(overflow) {
                decimal = MutableDecimal.empty();
                decimal.mantissa(v);
                decimal.divideCaped(v);
                assertEquals("1", decimal.toString());
            } else {
                decimal = MutableDecimal.empty();
                decimal.mantissa(r);
                decimal.divideCaped(v);
                assertEquals("15", decimal.toString());
            }
        }

        decimal = MutableDecimalParser.parse("1636.9238");
        var bd = decimal.bigDecimal();
        decimal.divideCaped(8192L);
        assertEquals(decimal.toString(), bd.divide(BigDecimal.valueOf(8192L), DECIMAL64).toPlainString());

        decimal = MutableDecimalParser.parse("39937.162");
        bd = decimal.bigDecimal();
        decimal.divideCaped(3L);
        assertEquals(decimal.toString(), bd.divide(BigDecimal.valueOf(3L), DECIMAL128).setScale(-decimal.exp, HALF_UP).toPlainString());

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp((byte) -18); // value = -0.100902777777777777
        bd = decimal.bigDecimal();
        decimal.divideCaped(10L);
        assertEquals("-0.0100902777777777777", decimal.toString());
        assertEquals(decimal.toString(), bd.divide(BigDecimal.TEN, DECIMAL128).toPlainString());

        decimal = MutableDecimalParser.parse("-100902777777777777");
        decimal.exp((byte) 18); // value = -100902777777777777000000000000000000
        bd = decimal.bigDecimal();
        decimal.divideCaped(10L);
        assertEquals("-10090277777777777700000000000000000", decimal.toString());
        assertEquals(decimal.toString(), bd.divide(BigDecimal.TEN, DECIMAL128).toPlainString());

        var decimal1 = MutableDecimal.empty();
        long value;
        decimal1.mantissa(Long.MAX_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        var bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals("1", decimal1.toString());
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).setScale(128, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MAX_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString().substring(0, 19), bd.divide(bd2, DECIMAL128).toPlainString().substring(0, 19));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        // -999999999999999999 - 1 (rounding)
        // -9999999999999999998915797827514496000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
        assertTrue(decimal1.toString().startsWith(String.valueOf(-1L - 999999999999999999L)));
        assertTrue(bd.divide(bd2, DECIMAL128).toPlainString().startsWith("-9999999999999999998"));
        assertEquals(decimal1.toString().length(), 1 + bd.divide(bd2, DECIMAL128).toPlainString().length());


        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        // 999999999999999999 + 1 (rounding)
        // 9999999999999999998915797827514496000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
        assertTrue(decimal1.toString().startsWith(String.valueOf(-1L - 999999999999999999L)));
        assertTrue(bd.divide(bd2, DECIMAL128).toPlainString().startsWith("-9999999999999999998"));
        assertEquals(decimal1.toString().length(), 1 + bd.divide(bd2, DECIMAL128).toPlainString().length());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MIN_VALUE + 2;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString().length(), bd.divide(bd2, DECIMAL128).toPlainString().length());
        assertEquals(decimal1.toString().substring(0, 19), bd.divide(bd2, DECIMAL128).toPlainString().substring(0, 19));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp((byte) -90);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        // precision overflow
        // 0.0........099999999999999999989157978275144960000...
        // rounded : .1000000000000000000
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).setScale(90, HALF_UP).toPlainString());
        assertTrue(bd.divide(bd2, DECIMAL128).setScale(90 + 18, HALF_UP).toPlainString().startsWith(decimal1.toString()));
        assertFalse(bd.divide(bd2, DECIMAL128).setScale(90 + 19, HALF_UP).toPlainString().startsWith(decimal1.toString()));

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp((byte) -90);
        value = Long.MIN_VALUE / 100000L;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        // precision overflow
        // 0.0........099999999999999999989157978275144960000...
        // rounded : .1000000000000000000
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).setScale(-decimal1.exp, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MAX_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        // precision overflow
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).setScale(128, HALF_UP).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MAX_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        decimal1.exp(Byte.MIN_VALUE);
        value = Long.MIN_VALUE;
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).toPlainString());

        decimal1.mantissa(Long.MIN_VALUE);
        value = 1000000L;
        decimal1.exp((byte) 45);
        bd = decimal1.bigDecimal();
        bd2 = BigDecimal.valueOf(value);
        decimal1.divideCaped(value);
        assertEquals(decimal1.toString(), bd.divide(bd2, DECIMAL128).toPlainString());

        for(int i = TEST_LOOP; i-- != 0;) {
            var rand1 = (Math.random() - 0.5d) * 111110.333000123d;
            var rand2 = (long) ((Math.random() - 0.5d) * 10d * i);
            if(0L == rand2) {
                rand2 = Math.min(31, (long) ((Math.random() - 0.5d) * 10d * i));
            }
            var value1 = number(rand1, MAX_COMPACT_DIGITS - 9);
            decimal1 = MutableDecimalParser.parse(value1);
            if(0L == rand2) {
                rand2 = Math.max(31, (long) ((Math.random() - 0.5d) * 10d * i));
            }
            decimal1.divideCaped(rand2);
            decimal1.shrinkMantissa();
            String s1 = decimal1.toString();
            String s2 = new BigDecimal(value1).divide(BigDecimal.valueOf(rand2), DECIMAL128).setScale(-decimal1.exp, HALF_UP).stripTrailingZeros().toPlainString();

            if(s2.length() > s1.length()) {
                s2 = s2.substring(0, s1.length() - 1);
                s1 = s1.substring(0, s1.length() - 1);
            } else if(s1.length() > s2.length()) {
                s1 = s1.substring(0, s2.length() - 1);
                s2 = s2.substring(0, s2.length() - 1);
            }
            if(!s1.equals(s2)) {
                System.out.println(value1 + " - " + rand2);
                System.out.println(decimal1 + " - " + new BigDecimal(value1).divide(BigDecimal.valueOf(rand2), DECIMAL128).toPlainString());
            }

            assertEquals(s1, s2);

            decimal1 = MutableDecimalParser.parse(value1);
            byte randScale = (byte) (2 * ((Math.random() - 0.5d) * (Byte.MAX_VALUE - 1)));
            decimal1.exp(randScale);
            bd = decimal1.bigDecimal();
            decimal1.divideCaped(rand2);
            s1 = decimal1.toString();
            s2 = bd.divide(BigDecimal.valueOf(rand2), DECIMAL128).toPlainString();

            if(s2.length() > s1.length()) {
                s2 = s2.substring(0, s1.length() - 1);
                s1 = s1.substring(0, s1.length() - 1);
            } else if(s1.length() > s2.length()) {
                s1 = s1.substring(0, s2.length() - 1);
                s2 = s2.substring(0, s2.length() - 1);
            }
            if(!s1.equals(s2)) {
                while(!"0".equals(s1) && s1.endsWith("0")) {
                    s1 = minusLastChar(s1);
                    s2 = minusLastChar(s2);
                }
                if(!"0".equals(s1)) {
                    s1 = minusLastChar(s1);
                    s2 = minusLastChar(s2);
                }
            }
            assertEquals(s1, s2);
        }
        System.out.println("ok : " + MutableDecimal.ok);
        System.out.println("failed : " + MutableDecimal.failing);
        System.out.println("% : " + (100d * (double) MutableDecimal.failing) / (double) MutableDecimal.ok);
    }

    @Test
    void setOverflow() {
        var decimal = MutableDecimal.empty();
        decimal.setOverflow(true, (byte) 0);
        assertEquals(Long.MAX_VALUE, decimal.mantissa);
        assertEquals(Byte.MAX_VALUE, decimal.exp);
        decimal.setOverflow(false, (byte) 0);
        assertEquals(Long.MIN_VALUE + 1, decimal.mantissa);
        assertEquals(Byte.MAX_VALUE, decimal.exp);
        decimal.setOverflow(true, Byte.MIN_VALUE + 1);
        assertEquals(Long.MAX_VALUE, decimal.mantissa);
        assertEquals(Byte.MAX_VALUE, decimal.exp);
        decimal.setOverflow(false, Byte.MIN_VALUE + 1);
        assertEquals(Long.MIN_VALUE + 1, decimal.mantissa);
        assertEquals(Byte.MAX_VALUE, decimal.exp);
        decimal.setOverflow(true, Byte.MIN_VALUE);
        assertEquals(0L, decimal.mantissa);
        assertEquals((byte) 0, decimal.exp);
        decimal.setOverflow(false, Byte.MIN_VALUE);
        assertEquals(0L, decimal.mantissa);
        assertEquals((byte) 0, decimal.exp);
        decimal.setOverflow(true, Byte.MIN_VALUE - 1);
        assertEquals(0L, decimal.mantissa);
        assertEquals((byte) 0, decimal.exp);
        decimal.setOverflow(false, Byte.MIN_VALUE - 3);
        assertEquals(0L, decimal.mantissa);
        assertEquals((byte) 0, decimal.exp);
    }

    @Test
    void toStringTest() {
        var decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MIN_VALUE);
        decimal.exp((byte) -2);
        assertEquals("-92233720368547758.08", decimal.toString());

        decimal = MutableDecimal.empty();
        decimal.mantissa(Long.MAX_VALUE);
        decimal.exp((byte) -2);
        assertEquals("92233720368547758.07", decimal.toString());

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

        for(int i = TEST_LOOP / 2; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 100 * i;
            value = number(rand, MAX_COMPACT_DIGITS);
            MutableDecimalParser.parse(value).toPlainString(sb);
            assertEquals(value, sb.toString());
            sb.setLength(0);
        }
    }

    @Test
    void equals() {
        assertThrows(UnsupportedOperationException.class, () -> MutableDecimal.empty().hashCode());
        assertThrows(UnsupportedOperationException.class, ONE::hashCode);
        assertEquals(MutableDecimal.of(10L, (byte) -1), MutableDecimal.of(1L, (byte) 0));
        assertEquals(MutableDecimal.of(10L, (byte) 0), MutableDecimal.of(1L, (byte) 1));
        assertEquals(MutableDecimal.of(1000L, (byte) 0), MutableDecimal.of(1L, (byte) 3));
        assertEquals(MutableDecimal.of(10L, (byte) -1), MutableDecimal.of(100L, (byte) -2));
        assertEquals(MutableDecimal.of(12L, (byte) -1), MutableDecimal.of(120L, (byte) -2));
        assertEquals(MutableDecimal.of(10L, (byte) -1), MutableDecimal.of(10000L, (byte) -4));
        assertEquals(MutableDecimal.of(0L, (byte) -1), MutableDecimal.of(0L, (byte) 14));
        assertEquals(MutableDecimal.of(0L, (byte) 0), MutableDecimal.of(0L, (byte) -14));

        for(int i = TEST_LOOP / 2; i-- != 0;) {
            var rand = (Math.random() - 0.5d) * 10d * i;
            if(0L == rand) {
                rand = Math.max(31, (long) ((Math.random() - 0.5d) * 10d * i));
            }
            var value = number(rand, MAX_COMPACT_DIGITS);
            var decimal = MutableDecimalParser.parse(value);
            var same = MutableDecimal.of(decimal.mantissa(), decimal.exp());
            assertTrue(same.equals(decimal));
            same.multiplyCaped(10L);
            assertNotEquals(decimal, same);
            same.divideCaped(10L);
            assertTrue(decimal.equals(same));
            assertTrue(same.equals(decimal));
            same.addCaped(TWO);
            assertNotEquals(decimal, same);
            same.subtractCaped(TWO);
            assertEquals(decimal, same);
        }
    }
}