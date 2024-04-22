package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;


public class MutableDecimal {

    public static final class ImmutableDecimal extends MutableDecimal {

        public static final MutableDecimal ZERO = new ImmutableDecimal(0L, (byte) 0);

        public ImmutableDecimal(long value, byte scale) {
            this.value = value;
            this.scale = scale;
        }

        @Override
        public void value(long value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void scale(byte scale) {
            throw new UnsupportedOperationException();
        }
    }


    static final long[] LONG_TEN_POWERS_TABLE = {
            1,                     // 0 / 10^0
            10,                    // 1 / 10^1
            100,                   // 2 / 10^2
            1000,                  // 3 / 10^3
            10000,                 // 4 / 10^4
            100000,                // 5 / 10^5
            1000000,               // 6 / 10^6
            10000000,              // 7 / 10^7
            100000000,             // 8 / 10^8
            1000000000,            // 9 / 10^9
            10000000000L,          // 10 / 10^10
            100000000000L,         // 11 / 10^11
            1000000000000L,        // 12 / 10^12
            10000000000000L,       // 13 / 10^13
            100000000000000L,      // 14 / 10^14
            1000000000000000L,     // 15 / 10^15
            10000000000000000L,    // 16 / 10^16
            100000000000000000L,   // 17 / 10^17
            1000000000000000000L   // 18 / 10^18
    };

    static final long[] THRESHOLDS_TABLE = {
            Long.MAX_VALUE,                     // 0
            Long.MAX_VALUE/10L,                 // 1
            Long.MAX_VALUE/100L,                // 2
            Long.MAX_VALUE/1000L,               // 3
            Long.MAX_VALUE/10000L,              // 4
            Long.MAX_VALUE/100000L,             // 5
            Long.MAX_VALUE/1000000L,            // 6
            Long.MAX_VALUE/10000000L,           // 7
            Long.MAX_VALUE/100000000L,          // 8
            Long.MAX_VALUE/1000000000L,         // 9
            Long.MAX_VALUE/10000000000L,        // 10
            Long.MAX_VALUE/100000000000L,       // 11
            Long.MAX_VALUE/1000000000000L,      // 12
            Long.MAX_VALUE/10000000000000L,     // 13
            Long.MAX_VALUE/100000000000000L,    // 14
            Long.MAX_VALUE/1000000000000000L,   // 15
            Long.MAX_VALUE/10000000000000000L,  // 16
            Long.MAX_VALUE/100000000000000000L, // 17
            Long.MAX_VALUE/1000000000000000000L // 18
    };

    private static final long LONG_MASK = 0xffffffffL;
    private static final long DIV_NUM_BASE = (1L<<32); // Number base (32 bits).

    protected long value; // mantissa
    protected byte scale; // exponent

    @NotNull
    public static MutableDecimal empty() {
        return new MutableDecimal();
    }

    @NotNull
    public static MutableDecimal of(long unscaledVal, byte scale) {
        return empty().set(unscaledVal, scale);
    }

    @NotNull
    public final BigDecimal bigDecimal() {
        return BigDecimal.valueOf(value, scale);
    }

    public final long value() {
        return value;
    }

    public void value(long value) {
        this.value = value;
    }

    public final byte scale() {
        return scale;
    }

    public void scale(byte scale) {
        this.scale = scale;
    }

    @NotNull
    public final MutableDecimal set(long unscaledVal, byte scale) {
        value(unscaledVal);
        scale(scale);
        return this;
    }

    public final void max(@NotNull MutableDecimal mutableDecimal) {
        max(mutableDecimal.value, mutableDecimal.scale);
    }

    public final void max(long unscaledVal, byte scale) {
        if (compareTo(unscaledVal, scale) < 0) {
            set(unscaledVal, scale);
        }
    }

    public final int compareTo(@NotNull MutableDecimal mutableDecimal) {
        return compareTo(mutableDecimal.value, mutableDecimal.scale);
    }

    public final int compareTo(long unscaledVal, byte scale) {
        if (this.scale == scale) {
            return this.value != unscaledVal ? ((this.value > unscaledVal) ? 1 : -1) : 0;
        }
        int xsign = Long.signum(this.value);
        int ysign = Long.signum(unscaledVal);
        if (xsign != ysign) {
            return (xsign > ysign) ? 1 : -1;
        }
        if (xsign == 0) {
            return 0;
        }
        int cmp = compareMagnitude(unscaledVal, scale);
        return xsign > 0 ? cmp : -cmp;
    }

    private int compareMagnitude(long unscaledVal, byte scale) {
        var thisValue = value;
        if (thisValue == 0) {
            return (unscaledVal == 0) ? 0 : -1;
        }
        if (unscaledVal == 0) {
            return 1;
        }

        int sdiff = this.scale;
        sdiff -= scale;
        if (0 != sdiff) {
            // Avoid matching scales if the (adjusted) exponents differ
            long xae = (long) longDigitLength(thisValue) - this.scale;      // [-1]
            long yae = (long) longDigitLength(unscaledVal) - scale;  // [-1]
            if (xae < yae) {
                return -1;
            } else if (xae > yae) {
                return 1;
            }
            if (sdiff < 0) {
                thisValue = longMultiplyPowerTen(thisValue, -sdiff);
            } else {
                unscaledVal = longMultiplyPowerTen(unscaledVal, sdiff);
            }
        }
        return Long.compare(Math.abs(thisValue), Math.abs(unscaledVal));
    }

    public static int longDigitLength(long value) {
        if(Long.MIN_VALUE == value) {
            return 19;
        } else if (value < 0) {
            value = -value;
        }
        if (value < 10) { // must screen for 0, might as well 10
            return 1;
        }
        int r = ((64 - Long.numberOfLeadingZeros(value) + 1) * 1233) >>> 12;
        // if r >= length, must have max possible digits for long
        return r >= LONG_TEN_POWERS_TABLE.length || value < LONG_TEN_POWERS_TABLE[r] ? r : r + 1;
    }

    public final void subtract(@NotNull MutableDecimal mutableDecimal) {
        subtract(mutableDecimal.value, mutableDecimal.scale);
    }

    public final void subtract(long unscaledVal, byte scale) {
        add(-unscaledVal, scale);
    }

    public final void add(@NotNull MutableDecimal mutableDecimal) {
        add(mutableDecimal.value, mutableDecimal.scale);
    }

    public final void add(long unscaledVal, byte scale) {
        int sdiff = this.scale - scale;
        if (sdiff == 0) {
            add(this.value, unscaledVal, this.scale);
        } else if (sdiff < 0) {
            long scaledX = longMultiplyPowerTen(this.value, -sdiff);
            add(scaledX, unscaledVal, scale);
        } else {
            long scaledY = longMultiplyPowerTen(unscaledVal, sdiff);
            add(this.value, scaledY, this.scale);
        }
    }

    private void add(long x, long y, byte scale) {
        long sum = x + y;
        if (((sum ^ x) & (sum ^ y)) < 0L) {
            throw overflowException(false);
        }
        // shrunk size keeping same precision
        byte scaleInc = scale < 0 ? (byte) 1 : (byte) -1;
        while (0L != sum && 0L == sum % 10L) {
            scale = checkScale(scale + scaleInc); // this can not overflow
            sum /= 10L;
        }
        set(sum, scale);
    }

    private static byte checkScale(int scale) {
        if((byte) scale != scale) {
            throw overflowException(true);
        }
        return (byte) scale;
    }

    private static ArithmeticException overflowException(boolean exponent) {
        return new ArithmeticException(exponent ? "Exponent overflow" : "Overflow");
    }

    private static long longMultiplyPowerTen(long value, int power) {
        if (0L == value || power <= 0) {
            return value;
        }
        if (power < LONG_TEN_POWERS_TABLE.length) {
            long tenpower = LONG_TEN_POWERS_TABLE[power];
            if (1L == value) {
                return tenpower;
            }
            if (Math.abs(value) <= THRESHOLDS_TABLE[power]) {
                return value * tenpower;
            }
        }
        throw overflowException(false);
    }

    // divide and cap max / min result to MutableDecimal capacities
    public final void divideCaped(long divisor) {
        if (0L == divisor) {   // x/0
            throw new ArithmeticException(0L == this.value ? "Division undefined" : "Division by zero");
        } else if (0L == this.value) {
            return;
        }
        boolean incScale = scale >= 0;
        byte scaleInc = incScale ?  (byte) 1 : (byte) -1;

        long newValue;
        try {
            var quotien = newValue = Math.divideExact(value, divisor);
            long remainder = value % divisor;
            if(Math.abs(remainder) > 0) {
                // reduce the quotien size if possible to let more room for the remainder (figures after the comma)
                while (0L != quotien && 0L == remainder % 10L && 0L == quotien % 10L) {
                    try {
                        scale(checkScale(scale - scaleInc));
                    } catch (ArithmeticException e) { // scale overflow, process with current figures
                        break;
                    }
                    quotien /= 10;
                    remainder /= 10;
                }
                // find the biggest scale for remainder values (decimals after the comma)
                int scaleDelta = LONG_TEN_POWERS_TABLE.length - 1;
                for (var absQuotien = Math.abs(quotien); absQuotien > THRESHOLDS_TABLE[scaleDelta]; scaleDelta--);
                // optimistic division strategy, reducing precision until it fit into long format with supported scale
                for(;;) {
                    try {
                        long decimals = multiplyDivide128(remainder, LONG_TEN_POWERS_TABLE[scaleDelta], divisor);
                        long scaled = quotien * LONG_TEN_POWERS_TABLE[scaleDelta];
                        newValue = Math.addExact(scaled, decimals);
                        try {
                            scale(checkScale(scale + (incScale ? scaleDelta : -scaleDelta)));
                        } catch (ArithmeticException e) { // scale overflow
                            // if scale overflow above byte limits,
                            // the number is either close to 0, either close to max/min mutable decimal values, depending on scale
                            // round or cape to max / min, and return
                            setOverflow(value >= 0L == divisor >= 0L, scale + (incScale ? scaleDelta : -scaleDelta));
                            return;
                        }
                        break;
                    } catch (ArithmeticException e) { // overflow, reduce precision
                        scaleDelta--;
                    }
                }
            }
            // clean up trailing zeros, scale can not overflow here
            while (0L != newValue && 0L == newValue % 10L) {
                newValue /= 10L;
                scale(checkScale(scale - scaleInc));
            }
        } catch (ArithmeticException e) { // divideExact special error case, positive overflow
            setOverflow(true, scale);
            return;
        }
        value(newValue);
    }

    // divide and cap max / min result to MutableDecimal capacities
    public final void multiplyCaped(long value) {
        byte scaleInc = this.value < 0 ? (byte) 1 : (byte) -1;
        for(;;) {
            try {
                value(Math.multiplyExact(this.value, value));
                return;
            } catch (ArithmeticException e) { // overflow, try to reduce precision
                if(Math.abs(this.value) > Math.abs(value)) {
                    value(this.value / 10L);
                } else {
                    value /= 10L;
                }
                try {
                    scale(checkScale(scale + scaleInc));
                } catch (ArithmeticException e2) { // scale overflow, set to limits
                    setOverflow(this.value >= 0L == value >= 0L, scale + scaleInc);
                    return;
                }
            }
        }
    }

    public final void setOverflow(boolean positive, int scale) {
        boolean zero = scale >= Byte.MAX_VALUE;
        value(zero ? 0L : positive ? Long.MAX_VALUE : Long.MIN_VALUE + 1L);
        scale(zero ? (byte) 0 : Byte.MIN_VALUE);
    }

    private static long multiplyDivide128(long x, long y, long divisor) {
        int sign = Long.signum(x) * Long.signum(y) * Long.signum(divisor);
        if(0 == sign) { // x can be 0, fast path
            return 0L;
        }
        x = Math.abs(x);
        y = Math.abs(y);
        divisor = Math.abs(divisor);

        long word = y & LONG_MASK;
        long word2 = x & LONG_MASK;
        long product = word2 * word;
        long d1 = product >>> 32;
        long d0 = product & LONG_MASK;
        long hi0 = x >>> 32;
        product = hi0 * word + d1;
        d1 = product & LONG_MASK;
        word = product >>> 32;
        long hi1 = y >>> 32;
        product = word2 * hi1 + d1;
        d1 = product & LONG_MASK;
        word += product >>> 32;
        word2 = word >>> 32;
        word &= LONG_MASK;
        product = hi0 * hi1 + word;
        word = product & LONG_MASK;
        word2 = ((product >>> 32) + word2) & LONG_MASK;
        return divide128(make64(word2, word), make64(d1, d0), divisor, sign);
    }

    /*
     * divideAndRound 128-bit value by long divisor.
     * Specialized version of Knuth's division
     */
    private static long divide128(final long dividendHi, final long dividendLo, long divisor, int sign) {
        if (dividendHi >= divisor) {
            throw overflowException(false);
        }

        int shift = Long.numberOfLeadingZeros(divisor);
        divisor <<= shift;

        long v1 = divisor >>> 32;
        long v0 = divisor & LONG_MASK;

        long tmp = dividendLo << shift;
        long u1 = tmp >>> 32;
        long u0 = tmp & LONG_MASK;

        tmp = (dividendHi << shift) | (dividendLo >>> 64 - shift);
        long u2 = tmp & LONG_MASK;
        long q0 = 0L, q1 = 0L, r_tmp;

        for(int i = 2; i-- != 0;) {
            long q;
            if (v1 == 1) {
                q = tmp;
                r_tmp = 0L;
            } else if (tmp >= 0L) {
                q = tmp / v1;
                r_tmp = tmp - q * v1;
            } else {
                // Approximate the quotient and remainder
                q = (tmp >>> 1) / (v1 >>> 1);
                r_tmp = tmp - q * v1;
                // Correct the approximation
                while (r_tmp < 0) {
                    r_tmp += v1;
                    q--;
                }
                while (r_tmp >= v1) {
                    r_tmp -= v1;
                    q++;
                }
            }
            boolean q0Loop = i == 0; // 2 turns, compute q1 then q0 (into q)
            while(q >= DIV_NUM_BASE || unsignedLongCompare(q * v0, make64(r_tmp, q0Loop ? u0 : u1))) {
                q--;
                r_tmp += v1;
                if (r_tmp >= DIV_NUM_BASE)
                    break;
            }
            if(q0Loop) {
                q0 = q;
            } else {
                q1 = q;
                tmp = mulsub(u2, u1, v1, v0, q1);
            }
        }

        if((int) q1 < 0) {
            // negative unsigned max value
            throw overflowException(false);
        }
        long quotien = make64(q1, q0);
        return sign >= 0 && quotien >= 0L ? quotien : -quotien;
    }

    private static long mulsub(long u1, long u0, final long v1, final long v0, long q0) {
        long tmp = u0 - q0 * v0;
        return make64(u1 + (tmp >>> 32) - q0 * v1,tmp & LONG_MASK);
    }

    private static boolean unsignedLongCompare(long one, long two) {
        return (one + Long.MIN_VALUE) > (two + Long.MIN_VALUE);
    }

    private static long make64(long hi, long lo) {
        return hi<<32 | lo;
    }

    @Override
    public final String toString() {
        var sb = new StringBuilder(20);
        toPlainString(sb);
        return sb.toString();
    }

    public final void toPlainString(@NotNull StringBuilder stringBuilder) {
        if(scale == 0) {
            stringBuilder.append(value);
            return;
        }
        int signum = Long.signum(value);
        if(scale < 0) { // No decimal point
            if(signum == 0) {
                stringBuilder.append("0");
                return;
            }
            int trailingZeros = -scale;
            stringBuilder.append(value);
            for (int i = trailingZeros; i-- != 0;) {
                stringBuilder.append('0');
            }
            return;
        }
        stringBuilder.append(Math.abs(value));
        fillValueString(stringBuilder, signum, scale);
    }

    private static void fillValueString(@NotNull StringBuilder intString, int signum, int scale) {
        /* Insert decimal point */
        int insertionPoint = intString.length() - scale;
        if (insertionPoint == 0) {  /* Point goes right before intVal */
            intString.insert(0, signum < 0 ? "-0." : "0.");
        } else if (insertionPoint > 0) { /* Point goes inside intVal */
            intString.insert(insertionPoint, '.');
            if (signum < 0) {
                intString.insert(0, '-');
            }
        } else { /* We must insert zeros between point and intVal */
            for (int i = 0; i < -insertionPoint; i++) {
                intString.insert(0, '0');
            }
            intString.insert(0, signum < 0 ? "-0." : "0.");
        }
    }

    @Override
    public final boolean equals(Object o) {
        return this == o || o instanceof MutableDecimal other && compareTo(other) == 0;
    }

    @Override
    public final int hashCode() {
        // probably a bad idea to use a mutable value as a key in a map
        throw new UnsupportedOperationException();
    }
}
