package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;


public class MutableDecimal {

    public static final class ImmutableDecimal extends MutableDecimal {

        public static final MutableDecimal ZERO = new ImmutableDecimal(0L, (byte) 0);
        public static final MutableDecimal ONE = new ImmutableDecimal(1L, (byte) 0);
        public static final MutableDecimal TWO = new ImmutableDecimal(2L, (byte) 0);

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
    //TODO dans l'autre sens...
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
        // todo shceck scale round up
        this.scale = scale;
    }

    @NotNull
    public final MutableDecimal set(long unscaledVal, byte scale) {
        value(unscaledVal);
        scale(scale);
        return this;
    }

    @NotNull
    public final MutableDecimal copy() {
        return empty().set(value, scale);
    }

    public final void shrunkScale() {
        // shrunk scale keeping same precision
        while (scale > Byte.MIN_VALUE && 0L != value && 0L == value % 10L) {
            scale(checkScale(scale - 1));
            value(value / 10L);
        }
    }

    public final void max(@NotNull MutableDecimal mutableDecimal) {
        max(mutableDecimal.value, mutableDecimal.scale);
    }

    public final void max(long unscaledVal, byte scale) {
        if (compareTo(unscaledVal, scale) < 0) {
            set(unscaledVal, scale);
        }
    }

    public final int compareAbsTo(@NotNull MutableDecimal mutableDecimal) {
        return compareAbsTo(value, this.scale, mutableDecimal.value, mutableDecimal.scale);
    }

    public final int compareTo(@NotNull MutableDecimal mutableDecimal) {
        return compareTo(mutableDecimal.value, mutableDecimal.scale);
    }

    public final int compareTo(long unscaledVal, byte scale) {
        return compareTo(value, this.scale, unscaledVal, scale);
    }

    private static int compareAbsTo(long value, byte s, long unscaledVal, byte scale) {
        return compareTo(absCaped(value), s, absCaped(unscaledVal), scale);
    }

    private static int compareTo(long value, byte s, long unscaledVal, byte scale) {
        if (s == scale) {
            return value != unscaledVal ? ((value > unscaledVal) ? 1 : -1) : 0;
        }
        int xsign = Long.signum(value);
        int ysign = Long.signum(unscaledVal);
        if (xsign != ysign) {
            return (xsign > ysign) ? 1 : -1;
        }
        if (xsign == 0) {
            return 0;
        }
        int cmp = compareMagnitude(value, s, unscaledVal, scale);
        return xsign > 0 ? cmp : -cmp;
    }

    private static int compareMagnitude(long value, byte s, long unscaledVal, byte scale) {
        if (value == 0) {
            return (unscaledVal == 0) ? 0 : -1;
        }
        if (unscaledVal == 0) {
            return 1;
        }

        int sdiff = s;
        sdiff -= scale;
        if (0 != sdiff) {
            // Avoid matching scales if the (adjusted) exponents differ
            long xae = (long) longDigitLength(value) - s;      // [-1]
            long yae = (long) longDigitLength(unscaledVal) - scale;  // [-1]
            if (xae < yae) {
                return -1;
            } else if (xae > yae) {
                return 1;
            }
            if (sdiff < 0) {
                value = longMultiplyPowerTen(value, -sdiff);
            } else {
                unscaledVal = longMultiplyPowerTen(unscaledVal, sdiff);
            }
        }
        return Long.compare(absCaped(value), absCaped(unscaledVal));
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
        boolean overflow = Long.MIN_VALUE == unscaledVal;
        add(overflow ? Long.MAX_VALUE : -unscaledVal, scale, overflow);
    }

    public final void add(@NotNull MutableDecimal mutableDecimal) {
        add(mutableDecimal.value, mutableDecimal.scale, false);
    }

    public final void add(long unscaledVal, byte scale) {
        add(unscaledVal, scale, false);
    }

    private void add(long unscaledVal, byte scale, boolean carry) {
        try {
            if (0L == value) {
                set(unscaledVal, scale);
                return;
            } else if (0L == unscaledVal) {
                return;
            }
            // TODO reduce scale
            int sdiff = this.scale - scale;
            if (Math.abs(sdiff) >= LONG_TEN_POWERS_TABLE.length) {
                // precision overflow, keep the biggest absolute result
                if (compareTo(absCaped(value), this.scale, absCaped(unscaledVal), scale) < 0) {
                    set(unscaledVal, scale);
                }
                return;
            }
            if (0 == sdiff ||
                    sdiff > 0 && absCaped(unscaledVal) <= THRESHOLDS_TABLE[sdiff] ||
                    sdiff < 0 && absCaped(this.value) <= THRESHOLDS_TABLE[-sdiff]) {
                // scaling can not overflow but the adding can
                boolean noOverflow;
                if (sdiff == 0) {
                    noOverflow = add(this.value, unscaledVal, this.scale, carry);
                } else if (sdiff < 0) {
                    long scaled = longMultiplyPowerTen(this.value, -sdiff);
                    noOverflow = add(scaled, unscaledVal, scale, carry);
                } else {
                    long scaled = longMultiplyPowerTen(unscaledVal, sdiff);
                    noOverflow = add(this.value, scaled, this.scale, carry);
                }
                if(noOverflow) {
                    return;
                }
            }
            // computing result will overflow, use 128 bits then reduce precision to fit result into a long
            if (sdiff <= 0) {
                scale(scale); // is updated by multiplyAdd128AndSet
                multiplyAdd128AndSet(this.value, LONG_TEN_POWERS_TABLE[-sdiff], unscaledVal, carry);
            } else {
                multiplyAdd128AndSet(unscaledVal, LONG_TEN_POWERS_TABLE[sdiff], this.value, carry);
            }
        } finally {
            shrunkScale();
        }
    }

    private boolean add(long x, long y, byte scale, boolean carry) {
        long sum = x + y;
        if (((sum ^ x) & (sum ^ y)) < 0L && (!carry || (Long.MIN_VALUE != x || Long.MIN_VALUE != y))) {
                return false;
        }
        if(carry) { // reversed Long.MIN_VALUE value adjustment
            long adjustedSum = sum + 1;
            if (((adjustedSum ^ sum) & (adjustedSum ^ 1)) < 0L) {
                return false;
            }
            sum = adjustedSum;
        }
        set(sum, scale);
        return true;
    }

    private static byte checkScale(int scale) {
        if((byte) scale != scale) {
            throw overflowException(true);
        }
        return (byte) scale;
    }

    private static long absCaped(long value) {
        return Long.MIN_VALUE == value ? Long.MAX_VALUE : (value < 0L ? -value : value);
    }

    private static ArithmeticException overflowException(boolean exponent) {
        return new ArithmeticException(exponent ? "Exponent overflow" : "Overflow");
    }

    private static long longMultiplyPowerTen(long value, int power) {
        if (0L == value || power <= 0) {
            return value;
        }
        long tenpower = LONG_TEN_POWERS_TABLE[power];
        if (1L == value) {
            return tenpower;
        }
        if (absCaped(value) <= THRESHOLDS_TABLE[power]) {
            return value * tenpower;
        }
        // overflow, should not happen here (compareTo)
        return Long.MAX_VALUE;
    }

    // divide and cap max / min result to MutableDecimal capacities
    public final void divide(long divisor) {
        divide(divisor, false);
    }

    public final void divideCaped(long divisor) {
        divide(divisor, true);
    }

    public final void divide(long divisor, boolean caped) {
        if (0L == divisor) {   // x/0
            throw new ArithmeticException(0L == this.value ? "Division undefined" : "Division by zero");
        } else if (0L == this.value) {
            return;
        } else if((this.value & 1) == 0 && isPow2(divisor)) {
            var exponent = Long.numberOfTrailingZeros(value);
            var exponent2 = Long.numberOfTrailingZeros(divisor);
            var delta = exponent >= exponent2 ? 0 : exponent2 - exponent;
            value(this.value >> (exponent2 - delta));
            if(0 == delta) {
                value(divisor < 0 ? -this.value : this.value);
                return;
            }
            divisor >>= (exponent2 - delta);
        } else {
            int pow10index = pow10Scale(divisor);
            if (pow10index >= 0) { // divisor is a pow of ten, do quick scale update
                if (Byte.MAX_VALUE >= scale + pow10index && (Long.MIN_VALUE != this.value || divisor >= 0)) {
                    scale((byte) (scale + pow10index));
                    value(divisor < 0 ? -this.value : this.value);
                    return;
                }
                pow10index = Byte.MAX_VALUE - scale;
                value /= LONG_TEN_POWERS_TABLE[pow10index];
                scale((byte) (scale + pow10index));
            }
        }

        long newValue;
        try {
            var quotien = newValue = Math.divideExact(value, divisor);
            long remainder = value % divisor;
            if(Math.abs(remainder) > 0) {
                // reduce the quotien size if possible to let more room for the remainder (figures after the comma)
                while (0L != quotien && 0L == remainder % 10L && 0L == quotien % 10L) {
                    try {
                        scale(checkScale(scale - 1));
                    } catch (ArithmeticException e) { // scale overflow, process with current figures
                        break;
                    }
                    quotien /= 10;
                    remainder /= 10;
                }
                // find the biggest scale for remainder values (decimals after the comma)
                int scaleDelta = Math.min(Byte.MAX_VALUE - scale, LONG_TEN_POWERS_TABLE.length - 1);
                for (var absQuotien = Math.abs(quotien); absQuotien > THRESHOLDS_TABLE[scaleDelta]; scaleDelta--);
                // optimistic division strategy, reducing precision until it fit into long format with supported scale
                for(;;) {
                    try {
                        long decimals = multiplyDivide128(remainder, LONG_TEN_POWERS_TABLE[scaleDelta], divisor);
                        long scaled = quotien * LONG_TEN_POWERS_TABLE[scaleDelta];
                        newValue = Math.addExact(scaled, decimals);
                        try {
                            scale(checkScale(scale + scaleDelta));
                        } catch (ArithmeticException e) {
                            // if scale overflow above byte limits,
                            // the number is either close to 0, either close to max/min mutable decimal values, depending on scale
                            // round or cape to max / min, and return
                            if(!caped) {
                                throw e;
                            }
                            setOverflow(value >= 0L == divisor >= 0L, scale + scaleDelta);
                            return;
                        }
                        break;
                    } catch (ArithmeticException e) { // overflow, reduce precision
                        scaleDelta--;
                    }
                }
            }
        } catch (ArithmeticException e) { // divideExact special error case, positive overflow
            if(!caped) {
                throw e;
            }
            setOverflow(true, scale);
            return;
        }
        value(newValue);
        shrunkScale();
    }

    private boolean isPow2(long value) {
        if(Long.MIN_VALUE == value) {
            return false;
        } // TODO check 0
        value = value & LONG_MASK;
        return (value & (value - 1)) == 0L;
    }

    private static int pow10Scale(long value) {
        value = Math.abs(value);
        for(int i = LONG_TEN_POWERS_TABLE.length; i-- != 0;) {
            if(LONG_TEN_POWERS_TABLE[i] == value) {
                return i;
            }
        }
        return -1;
    }

    // divide and cap max / min result to MutableDecimal capacities
    public final void multiplyCaped(long value) {
        if (0L == this.value || 0L == value) {
            value(0L);
            return;
        }
        if(isPow2(value)) {
            var exponent = Long.numberOfLeadingZeros(this.value);
            if(exponent > 0) {
                var exponent2 = Long.numberOfTrailingZeros(value);
                var delta = exponent > exponent2 ? 0 : (exponent2 - exponent + 1);
                value(this.value << (exponent2 - delta));
                if(0 == delta) {
                    value(value < 0 ? -this.value : this.value);
                    return;
                }
                value >>= (exponent2 - delta);
            }
        }
        int pow10index = pow10Scale(value);
        if(pow10index >= 0) { // value is a pow of ten, do quick scale update
            if(Byte.MIN_VALUE <= scale - pow10index && (Long.MIN_VALUE != this.value || value >= 0)) {
                scale((byte) (scale - pow10index));
                value(value < 0 ? - this.value : this.value);
                return;
            }
            pow10index = Math.abs(Byte.MIN_VALUE - scale);
            value /= LONG_TEN_POWERS_TABLE[pow10index];
            scale((byte) (scale - pow10index));
        }
        try {
            value(Math.multiplyExact(this.value, value));
            shrunkScale();
        } catch (ArithmeticException e) { // overflow, try to reduce precision
            multiplyAdd128AndSet(this.value, value, 0L, false);
        }
    }

    public final void setOverflow(boolean positive, int scale) {
        boolean zero = scale >= Byte.MAX_VALUE;
        value(zero ? 0L : positive ? Long.MAX_VALUE : Long.MIN_VALUE + 1L);
        scale(zero ? (byte) 0 : Byte.MIN_VALUE);
    }

    // border effect on value & scale fields, they are updated by this function
    private void multiplyAdd128AndSet(long x, long y, long value, boolean carry) {
        int sign = Long.signum(x) * Long.signum(y);
        if(0 == sign) { // trivial case not expected
            throw new IllegalArgumentException();
        }
        int sOverflow = Math.max(longDigitLength(value), longDigitLength(x) + longDigitLength(y)) - LONG_TEN_POWERS_TABLE.length;
        x = Math.abs(x);
        y = Math.abs(y);

        long lo0 = y & LONG_MASK;
        long hi0 = x & LONG_MASK;
        long product = hi0 * lo0;
        long hi1 = product >>> 32;
        long lo1 = product & LONG_MASK;
        x >>>= 32;
        product = x * lo0 + hi1;
        hi1 = product & LONG_MASK;
        lo0 = product >>> 32;
        y >>>= 32;
        product = hi0 * y + hi1;
        hi1 = product & LONG_MASK;
        lo0 += product >>> 32;
        hi0 = lo0 >>> 32;
        lo0 &= LONG_MASK;
        product = x * y + lo0;
        lo0 = product & LONG_MASK;
        hi0 = ((product >>> 32) + hi0) & LONG_MASK;
        x = hi0 << 32 | lo0;
        y = hi1 << 32 | lo1;

        // add the value
        value = sign > 0 ? value : -value;
        lo0 = y + value;
        if ((((y ^ lo0) & (value ^ lo0)) < 0) && (y < 0L || value < 0L)) { // TODO check, use add binary
            // handle overflow only for underflow, one signed addition do not overflow one unsigned 64 bits
            x++;
        }
        lo0 += carry ? 1 : 0;//TODO check overflow
        y = lo0;

        if (x == 0L && y >= 0L) {
            value(sign >= 0 ? y : -y);
            return;
        } else if (sOverflow == 1) { // simple overflow
            set(divide128(x, y, 10, sign), checkScale(this.scale - 1));
            return;
        }
        try { // extended overflow
            // TODO check scale overflow at start
            var delta = Math.min(sOverflow, LONG_TEN_POWERS_TABLE.length) - 1;
            scale(checkScale(this.scale - delta));
            var tmp = new Uint128(x, y);
            var div = new Uint128(0L, LONG_TEN_POWERS_TABLE[delta]);
            var qu = new Uint128(0L, 0L);
            var rem = new Uint128(0L, 0L);
            Bits128.divmod128(tmp, div, qu, rem);
            x = qu.high;
            y = qu.low;
            if(x == 0L && sign < 0 && y == Long.MIN_VALUE) {
                value(y);
            } else if(x != 0L || y < 0L) {
                set(divide128(x, y, 10, sign), checkScale(this.scale - 1));
            } else {
                value(sign >= 0 ? y : -y);
            }
        } catch (ArithmeticException e) {
            throw e;
        }
    }

    private static long multiplyDivide128(long x, long y, long divisor) {
        int sign = Long.signum(x) * Long.signum(y) * Long.signum(divisor);
        if(0 == sign) { // x can be 0, fast path
            return 0L;
        } // else if x ou y = 1 simply fy
        x = Math.abs(x);
        y = Math.abs(y);
        divisor = Math.abs(divisor);

        long lo0 = y & LONG_MASK;
        long hi0 = x & LONG_MASK;
        long product = hi0 * lo0;
        long hi1 = product >>> 32;
        long lo1 = product & LONG_MASK;
        x >>= 32;
        product = x * lo0 + hi1;
        hi1 = product & LONG_MASK;
        lo0 = product >>> 32;
        y >>>= 32;
        product = hi0 * y + hi1;
        hi1 = product & LONG_MASK;
        lo0 += product >>> 32;
        hi0 = lo0 >>> 32;
        lo0 &= LONG_MASK;
        product = x * y + lo0;
        lo0 = product & LONG_MASK;
        hi0 = ((product >>> 32) + hi0) & LONG_MASK;
        if(Long.MIN_VALUE == divisor) {
            Uint128 tmp = new Uint128(hi0 << 32 | lo0, hi1 << 32 | lo1);
            Uint128 div = new Uint128(0L, Long.MIN_VALUE);
            Uint128 qu = new Uint128(0L, 0L);
            Uint128 rem = new Uint128(0L, 0L);
            Bits128.divmod128(tmp, div, qu, rem);
            return sign * qu.low;
        }
        return divide128(hi0 << 32 | lo0, hi1 << 32 | lo1, divisor, sign);
    }

    // divide 128-bit value by long divisor
    public static long divide128(long dividendHi, long dividendLo, long divisor, int sign) {
        if (dividendHi >= divisor) {
            throw overflowException(false);
        }

        int shift = Long.numberOfLeadingZeros(divisor);
        long tmp = dividendLo << shift;
        long lo = tmp & LONG_MASK;
        long hi = tmp >>> 32;
        tmp = (dividendHi << shift) | (dividendLo >>> 64 - shift);

        divisor <<= shift;
        dividendHi = divisor >>> 32;
        dividendLo = divisor & LONG_MASK;

        for(boolean loTurn = false ;; loTurn = true) {
            long q;
            long remainder;
            if (dividendHi == 1) {
                q = tmp;
                remainder = 0L;
            } else if (tmp >= 0L) {
                q = tmp / dividendHi;
                remainder = tmp - q * dividendHi;
            } else {
                // Approximate the quotient and remainder
                q = (tmp >>> 1) / (dividendHi >>> 1);
                remainder = tmp - q * dividendHi;
                // Correct the approximation
                while (remainder < 0) {
                    remainder += dividendHi;
                    q--;
                }
                while (remainder >= dividendHi) {
                    remainder -= dividendHi;
                    q++;
                }
            }
            while(q >= DIV_NUM_BASE || (((q * dividendLo) + Long.MIN_VALUE) > ((remainder << 32 | (loTurn ? lo : hi)) + Long.MIN_VALUE))) {
                q--;
                remainder += dividendHi;
                if (remainder >= DIV_NUM_BASE)
                    break;
            }
            if(loTurn) { // 2 turns, compute high word then low (into q)
                divisor |= q;
                return sign >= 0 && divisor >= 0L ? divisor : -divisor;
                // return remainder
            } else { // fist turn, compute high word
                if((int) q < 0) { // negative unsigned max value
                    throw overflowException(false);
                }
                divisor = tmp & LONG_MASK;
                tmp = hi - q * dividendLo;
                tmp = (divisor + (tmp >>> 32) - q * dividendHi) << 32 | (tmp & LONG_MASK);
                divisor = q << 32;
            }
        }
    }

    @Override
    public final String toString() {
        var sb = new StringBuilder(20);
        toPlainString(sb);
        return sb.toString();
    }

    public final void toPlainString(@NotNull StringBuilder stringBuilder) {
        if(0L == value || 0 == scale) {
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
        if(Long.MIN_VALUE == value) { // can't be positive
            stringBuilder.deleteCharAt(0);
        }
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
        } else { /* insert zeros between point and significant numbers */
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
