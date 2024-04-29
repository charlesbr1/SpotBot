package org.sbot.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;
import static org.sbot.utils.MutableDecimal.Maths.LONG_TEN_POWERS_TABLE;
import static org.sbot.utils.MutableDecimal.Maths.*;


public class MutableDecimal {

    public static final class ImmutableDecimal extends MutableDecimal {

        public static final MutableDecimal ZERO = new ImmutableDecimal(0L, (byte) 0);
        public static final MutableDecimal ONE = new ImmutableDecimal(1L, (byte) 0);
        public static final MutableDecimal TWO = new ImmutableDecimal(2L, (byte) 0);

        public ImmutableDecimal(long mantissa, byte exp) {
            this.mantissa = mantissa;
            this.exp = exp;
        }

        @Override
        public void mantissa(long mantissa) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void exp(byte exp) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class OverflowException extends ArithmeticException {
        public OverflowException(boolean exponent) {
            super(exponent ? "Exponent overflow" : "Overflow");
        }
        @Override
        public Throwable fillInStackTrace() {//TODO test check
            return this;
        }
    }

    interface Maths {
        long[] LONG_TEN_POWERS_TABLE = {
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

        long[] THRESHOLDS_TABLE = {
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

        long LONG_MASK = 0xffffffffL;
        long DIV_NUM_BASE = 1L << 32; // Number base (32 bits)


        static int digitLength(long value) {
            if(Long.MIN_VALUE == value) {
                return 19;
            } else if (value < 0) {
                value = -value;
            }
            if (value < 10) {
                return 1;
            }
            int r = ((64 - Long.numberOfLeadingZeros(value) + 1) * 1233) >>> 12;
            return r >= LONG_TEN_POWERS_TABLE.length || value < LONG_TEN_POWERS_TABLE[r] ? r : r + 1;
        }

        static long multiplyPowerTen(long value, int power) {
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

        static boolean isPow2(long value) {
            if(Long.MIN_VALUE == value) {
                return false;
            } // TODO check 0
            value = value & LONG_MASK;
            return (value & (value - 1)) == 0L;
        }

        static int pow10ExactExp(long value) {
            value = Math.abs(value);
            for(int i = LONG_TEN_POWERS_TABLE.length; i-- != 0;) {
                if(LONG_TEN_POWERS_TABLE[i] == value) {
                    return i;
                }
            }
            return -1;
        }
    }

    protected long mantissa; // mantissa

    protected byte exp; // exponent

    @NotNull
    public static MutableDecimal empty() {
        return new MutableDecimal();
    }

    @NotNull
    public static MutableDecimal of(long value, byte scale) {
        return empty().set(value, scale);
    }

    @NotNull
    public final BigDecimal bigDecimal() {
        return BigDecimal.valueOf(mantissa, -exp);
    }

    public final long mantissa() {
        return mantissa;
    }

    public void mantissa(long mantissa) {
        this.mantissa = mantissa;
    }

    public final byte exp() {
        return exp;
    }

    public void exp(byte exp) {
        this.exp = exp;
    }

    @NotNull
    public final MutableDecimal set(long mantissa, byte exp) {
        mantissa(mantissa);
        exp(exp);
        return this;
    }

    public final void setOverflow(boolean positive, int scale) {
        boolean zero = scale <= Byte.MIN_VALUE;
        mantissa(zero ? 0L : positive ? Long.MAX_VALUE : -Long.MAX_VALUE);
        exp(zero ? (byte) 0 : Byte.MAX_VALUE);
    }

    @NotNull
    public final MutableDecimal copy() {
        return empty().set(mantissa, exp);
    }

    public final void shrinkMantissa() {
        // shrunk scale keeping same precision
        while (exp < Byte.MAX_VALUE && 0L != mantissa && 0L == mantissa % 10L) {
            exp((byte) (exp + 1));
            mantissa(mantissa / 10L);
        }
    }

    private static boolean exponentOverflow(int exp) {
        return (byte) exp != exp;
    }

    private static byte checkExponent(int exp) {
        if(exponentOverflow(exp)) {
            throw new OverflowException(true);
        }
        return (byte) exp;
    }

    // drop provided number of digits from significant number (reduce precision)
    // ex : 1.2345.roundHalfUp(2) = 1.23; 1.2355.roundHalfUp(2) = 1.24
    // @throws OverflowException if digits > 19 or digits + exp will overflow
    public void roundHalfUp(int digits) {
        if(requireStrictlyPositive(digits) + exp > Byte.MAX_VALUE || digits >= LONG_TEN_POWERS_TABLE.length) {
            throw new OverflowException(true);
        }
        var tenPower = LONG_TEN_POWERS_TABLE[digits];
        long remainder = mantissa % tenPower;
        remainder = Math.abs(remainder) >= (tenPower >> 1) ? remainder >= 0 ? 1 : -1 : 0;
        set(remainder + (mantissa / tenPower), (byte) (this.exp + digits));
    }

    public final void max(@NotNull MutableDecimal mutableDecimal) {
        max(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void max(long value) {
        max(value, (byte) 0);
    }

    public final void max(long mantissa, byte exp) {
        if (compareTo(mantissa, exp) < 0) {
            set(mantissa, exp);
        }
    }

    public final int compareAbsTo(@NotNull MutableDecimal mutableDecimal) {
        return compareTo(absCaped(mantissa), exp, absCaped(mutableDecimal.mantissa), mutableDecimal.exp);
    }

    public final int compareAbsTo(long value) {
        return compareTo(absCaped(value), exp, absCaped(value), (byte) 0);
    }

    private static long absCaped(long value) {
        return Long.MIN_VALUE == value ? Long.MAX_VALUE : (value < 0L ? -value : value);
    }

    public final int compareTo(@NotNull MutableDecimal mutableDecimal) {
        return compareTo(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final int compareTo(long value) {
        return compareTo(value, (byte) 0);
    }

    public final int compareTo(long mantissa, byte exp) {
        return compareTo(this.mantissa, this.exp, mantissa, exp);
    }

    private static int compareTo(long mantissa1, byte exp1, long mantissa2, byte exp2) {
        if (exp1 == exp2) {
            return mantissa1 != mantissa2 ? ((mantissa1 > mantissa2) ? 1 : -1) : 0;
        }
        int xsign = Long.signum(mantissa1);
        int ysign = Long.signum(mantissa2);
        if (xsign != ysign) {
            return (xsign > ysign) ? 1 : -1;
        }
        if (xsign == 0) {
            return 0;
        }
        int cmp = compareMagnitude(mantissa1, exp1, mantissa2, exp2);
        return xsign > 0 ? cmp : -cmp;
    }

    private static int compareMagnitude(long mantissa1, byte exp1, long mantissa2, byte exp2) {
        if (mantissa1 == 0) {
            return (mantissa2 == 0) ? 0 : -1;
        }
        if (mantissa2 == 0) {
            return 1;
        }
        int sdiff = exp1 - exp2;
        if (0 != sdiff) {
            // Avoid matching scales if the (adjusted) exponents differ
            long xae = (long) digitLength(mantissa1) + exp1;
            long yae = (long) digitLength(mantissa2) + exp2;
            if (xae < yae) {
                return -1;
            } else if (xae > yae) {
                return 1;
            }
            if (sdiff < 0) {
                mantissa2 = multiplyPowerTen(mantissa2, -sdiff);
            } else {
                mantissa1 = multiplyPowerTen(mantissa1, sdiff);
            }
        }
        return Long.compare(absCaped(mantissa1), absCaped(mantissa2));
    }

    public final void subtractExact(@NotNull MutableDecimal mutableDecimal) {
        subtractExact(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void subtractExact(long value) {
        subtractExact(value, (byte) 0);
    }

    public final void subtractExact(long mantissa, byte exp) {
        boolean overflow = Long.MIN_VALUE == mantissa;
        add(overflow ? Long.MAX_VALUE : -mantissa, exp, overflow, true);
    }

    public final void subtractCaped(@NotNull MutableDecimal mutableDecimal) {
        subtractCaped(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void subtractCaped(long value) {
        subtractCaped(value, (byte) 0);
    }

    public final void subtractCaped(long mantissa, byte exp) {
        boolean overflow = Long.MIN_VALUE == mantissa;
        add(overflow ? Long.MAX_VALUE : -mantissa, exp, overflow, false);
    }

    public final void addExact(@NotNull MutableDecimal mutableDecimal) {
        addExact(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void addExact(long value) {
        addExact(value, (byte) 0);
    }

    public final void addExact(long mantissa, byte exp) {
        add(mantissa, exp, false, true);
    }

    public final void addCaped(@NotNull MutableDecimal mutableDecimal) {
        addCaped(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void addCaped(long value) {
        addCaped(value, (byte) 0);
    }

    public final void addCaped(long mantissa, byte exp) {
        add(mantissa, exp, false, false);
    }

    private void add(long mantissa2, byte exp2, boolean carry, boolean exact) {
        try {
            int sdiff = addTrivial(mantissa2, exp2, exact);
            boolean ok = false;
            if(Byte.MAX_VALUE == sdiff) {
                return;
            } else if(0 == sdiff) {
                ok = add(this.mantissa, mantissa2, this.exp, carry);
            } else if (sdiff > 0 && absCaped(mantissa2) <= THRESHOLDS_TABLE[sdiff]) {
                long scaled = multiplyPowerTen(mantissa2, sdiff);
                ok = add(this.mantissa, scaled, this.exp, carry);
            } else if(sdiff < 0 && absCaped(this.mantissa) <= THRESHOLDS_TABLE[-sdiff]) {
                long scaled = multiplyPowerTen(this.mantissa, -sdiff);
                ok = add(scaled, mantissa2, exp2, carry);
            }
            if(ok) {
                return;
            } else if(exact) {
                throw new OverflowException(true);
            }
            // computing result will overflow, use 128 bits then reduce precision to fit result into a long
            if (sdiff <= 0) {
                exp(exp2); // is updated by multiplyAdd128AndSet
                multiplyAdd128AndSet(this.mantissa, LONG_TEN_POWERS_TABLE[-sdiff], mantissa2, carry, false);
            } else {
                multiplyAdd128AndSet(mantissa2, LONG_TEN_POWERS_TABLE[sdiff], this.mantissa, carry, false);
            }
        } finally {
            shrinkMantissa();
        }
    }

    private int addTrivial(long mantissa2, byte exp2, boolean exact) {
        if (0L == this.mantissa) {
            set(mantissa2, exp2);
            return Byte.MAX_VALUE; // returns
        } else if (0L == mantissa2) {
            return Byte.MAX_VALUE;
        }
        int sdiff = exp2 - this.exp;
        if (Math.abs(sdiff) >= LONG_TEN_POWERS_TABLE.length) {
            if (exact) {
                throw new OverflowException(true);
            }
            // precision overflow, keep the biggest absolute result
            if (compareTo(absCaped(this.mantissa), this.exp, absCaped(mantissa2), exp2) < 0) {
                set(mantissa2, exp2);
            }
            return Byte.MAX_VALUE;
        }
        return sdiff;
    }

    private boolean add(long x, long y, byte scale, boolean carry) {
        long sum = x + y;
        if (((sum ^ x) & (sum ^ y)) < 0L && (!carry || (Long.MIN_VALUE != x || Long.MIN_VALUE != y))) {
                return false;
        }
        if(carry) { // reversed Long.MIN_VALUE value adjustment
            long adjustedSum = sum + 1;
            if (((adjustedSum ^ sum) & (adjustedSum ^ 1)) < 0L) {
                return false; // carry created overflow
            }
            sum = adjustedSum;
        }
        set(sum, scale);
        return true;
    }

    public final void multiplyExact(@NotNull MutableDecimal mutableDecimal) {
        multiplyExact(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void multiplyExact(long value) {
        multiplyExact(value, (byte) 0);
    }

    public final void multiplyExact(long mantissa, byte exp) {
        // update scale
        exp(checkExponent(this.exp + exp));
        multiply(mantissa, true);
    }

    public final void multiplyCaped(@NotNull MutableDecimal mutableDecimal) {
        multiplyCaped(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void multiplyCaped(long value) {
        multiplyCaped(value, (byte) 0);
    }

    public final void multiplyCaped(long mantissa, byte exp) {
        // update scale
        if(exponentOverflow(this.exp + exp)) {
            if(0L == this.mantissa || 0L == mantissa) {
                mantissa(0L);
            } else {
                setOverflow(this.mantissa >= 0L == mantissa >= 0L, this.exp + exp);
            }
            return;
        }
        exp((byte) (this.exp + exp));
        multiply(mantissa, false);
    }

    // divide and overflow or reduce result precision to 64bits capacities
    private void multiply(long value, boolean exact) {
        if(value == Long.MAX_VALUE || (value = multiplyTrivial(value)) != Long.MAX_VALUE) {
            try {
                mantissa(Math.multiplyExact(this.mantissa, value));
                shrinkMantissa();
            } catch (ArithmeticException e) { // overflow, try to reduce precision
                if (exact) {
                    throw e;
                }
                multiplyAdd128AndSet(this.mantissa, value, 0L, false, false);
            }
        }
    }

    private long multiplyTrivial(long value) {
        if (0L == this.mantissa || 0L == value) {
            mantissa(0L);
            return Long.MAX_VALUE;
        } else if (1L == value) { // -1 can overflow (-Long.MIN_VALUE)
            return Long.MAX_VALUE;
        }
        if (isPow2(value)) { // TODO test bench or drop
            var exponent = Long.numberOfLeadingZeros(this.mantissa);
            if (exponent > 0) { // perform pow 2 division up to no precision loss
                var exponent2 = Long.numberOfTrailingZeros(value);
                var delta = exponent > exponent2 ? 0 : (exponent2 - exponent + 1);
                mantissa(this.mantissa << (exponent2 - delta));
                if (0 == delta) {
                    mantissa(value < 0 ? -this.mantissa : this.mantissa);
                    return Long.MAX_VALUE;
                }
                value >>= (exponent2 - delta);
            }
        }
        int pow10index = pow10ExactExp(value);
        if (pow10index >= 0) { // value is a pow of ten, do quick scale update without precision loss
            if (Byte.MAX_VALUE >= exp + pow10index && (Long.MIN_VALUE != this.mantissa || value >= 0)) {
                exp((byte) (exp + pow10index));
                mantissa(value < 0 ? -this.mantissa : this.mantissa);
                return Long.MAX_VALUE;
            }
            pow10index = Byte.MAX_VALUE - exp;
            if (pow10index > 0) {
                value /= LONG_TEN_POWERS_TABLE[pow10index];
                exp((byte) (exp + pow10index));
            }
        }
        return value;
    }

    public final void divideExact(@NotNull MutableDecimal mutableDecimal) {
        divideExact(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void divideExact(long value) {
        divideExact(value, (byte) 0);
    }

    public final void divideExact(long mantissa, byte exp) {
        // update scale
        exp(checkExponent(this.exp - exp));
        divide(mantissa, exp, true);
    }

    public final void divideCaped(@NotNull MutableDecimal mutableDecimal) {
        divideCaped(mutableDecimal.mantissa, mutableDecimal.exp);
    }

    public final void divideCaped(long value) {
        divideCaped(value, (byte) 0);
    }


    // divide and cap max / min result to MutableDecimal capacities
    public final void divideCaped(long mantissa, byte exp) {
        divide(mantissa, exp, false);
    }

    private void divide(long mantissa1, byte exp1, boolean exact) {
        if(mantissa1 != Long.MAX_VALUE && (mantissa1 = divideTrivial(mantissa1)) == Long.MAX_VALUE) {
            return;
        }

        long quotien, result;
        try {
            quotien = result = Math.divideExact(mantissa, mantissa1);
        } catch (ArithmeticException e) { // divideExact special error case, positive overflow
            if(exact) {
                throw e;
            }
            setOverflow(true, exp);
            return;
        }

        long remainder = mantissa % mantissa1;
        if(Math.abs(remainder) > 0) { // compute fractional part
            // reduce the quotient size if possible to let more room for the remainder (figures after the comma)
            while (0L != quotien && 0L == remainder % 10L && 0L == quotien % 10L) {
                try {
                    exp(checkExponent(exp + 1));
                } catch (ArithmeticException e) { // scale overflow, process with current figures
                    break;
                }
                quotien /= 10;
                remainder /= 10;
            }
            // find the biggest scale for remainder values (decimals after the comma)
            int scaleDelta = Math.min(exp - Byte.MIN_VALUE, LONG_TEN_POWERS_TABLE.length - 1);
            for (var absQuotient = Math.abs(quotien); absQuotient > THRESHOLDS_TABLE[Math.min(scaleDelta, THRESHOLDS_TABLE.length - 1)]; scaleDelta--);
            scaleDelta -= scaleDelta + digitLength(remainder) - digitLength(mantissa1) > 17 ? 1 : 0;
            // optimistic division strategy, reducing precision until it fit into long format with supported scale
            boolean check = false;
            for(;;) {
                try {
                    var pow10 = LONG_TEN_POWERS_TABLE[scaleDelta];
                    long decimals = multiplyDivide128(remainder, pow10, mantissa1);
                    long scaled = quotien * pow10;
                    result = Math.addExact(scaled, decimals);
                    try {
                        exp(checkExponent(exp - scaleDelta));
                    } catch (ArithmeticException e) {
                        // if scale overflow above byte limits,
                        // the number is either close to 0, either close to max/min mutable decimal values, depending on scale
                        // round or cape to max / min, and return
                        if(exact) {
                            throw e;
                        }
                        setOverflow(mantissa >= 0L == mantissa1 >= 0L, exp - scaleDelta);
                        return;
                    }
                    if(!check)
                        ok++;
                    break;
                } catch (ArithmeticException e) { // overflow, reduce fractional precision
                    // this slow path (one more div) happens in 0.21% of tests
                    // optimization would be to find a more accurate ten power to use first (scaleDelta)
                    if(!check)
                        failing++;
                    check = true;
                    scaleDelta--;
                }
            }
        }
        mantissa(result);
        shrinkMantissa();
    }

public static long ok = 0L; // TODO remove
public static long failing = 0L;

    private long divideTrivial(long mantissa1) {
        if (0L == mantissa1) {   // x/0
            throw new ArithmeticException(0L == this.mantissa ? "Division undefined" : "Division by zero");
        } else if (0L == this.mantissa || 1L == mantissa1) { // -1 can overflow (-Long.MIN_VALUE)
            return Long.MAX_VALUE;
        } else if ((this.mantissa & 1) == 0 && isPow2(mantissa1)) {
            var exponent = Long.numberOfTrailingZeros(this.mantissa);
            var exponent2 = Long.numberOfTrailingZeros(mantissa1);
            var delta = exponent >= exponent2 ? 0 : exponent2 - exponent;
            mantissa(this.mantissa >> (exponent2 - delta));
            if (0 == delta) {
                mantissa(mantissa1 < 0 ? -this.mantissa : this.mantissa);
                return Long.MAX_VALUE;
            }
            mantissa1 >>= (exponent2 - delta);
        } else {
            int pow10index = pow10ExactExp(mantissa1);
            if (pow10index >= 0) { // divisor is a pow of ten, do quick scale update
                if (Byte.MIN_VALUE <= this.exp - pow10index && (Long.MIN_VALUE != this.mantissa || mantissa1 >= 0)) {
                    exp((byte) (this.exp - pow10index));
                    mantissa(mantissa1 < 0 ? -this.mantissa : this.mantissa);
                    return Long.MAX_VALUE;
                }
                pow10index = this.exp - Byte.MIN_VALUE;
                mantissa1 /= LONG_TEN_POWERS_TABLE[pow10index];
                exp((byte) (this.exp - pow10index));
            }
        }
        return mantissa1;
    }

    // border effect on value & scale fields, they are updated by this function
    private void multiplyAdd128AndSet(long x, long y, long value, boolean carry, boolean exact) {
        int sign = Long.signum(x) * Long.signum(y);
        if(0 == sign) { // trivial case not expected
            throw new IllegalArgumentException();
        }
        int sOverflow = Math.max(digitLength(value), digitLength(x) + digitLength(y)) - LONG_TEN_POWERS_TABLE.length;
        x = Math.abs(x);
        y = Math.abs(y); // TODO check caped ? (for multiplication
        long lo0;
        if(1L == x || 1L == y) {
            y = 1L == x ? y : x;
            x = 0L;
        } else {
            lo0 = y & LONG_MASK;
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
        }

        // add the value
        value = sign > 0 ? value : -value;
        lo0 = y + value;
        if ((((y ^ lo0) & (value ^ lo0)) < 0) && (y < 0L || value < 0L)) { // TODO check, use add binary
            // handle overflow only for underflow, one signed addition do not overflow one unsigned 64 bits
            x++;
        }
        lo0 += carry ? 1 : 0;//TODO check overflow ?
        y = lo0;

        if (x == 0L && y >= 0L) {
            mantissa(sign >= 0 ? y : -y);
            return;
        }
        if(exact) {
            throw new OverflowException(false);
        }
        if(x == 0L) { // simple overflow, reduce precision by 1 digit
            set(divideOrRound128(x, y, 10, sign, null), checkExponent(this.exp + 1));
        } else {
            // extended overflow
            scaleDownAndRound128(x, y, sOverflow, sign);
        }
    }

    private void scaleDownAndRound128(long x, long y, int sOverflow, int sign) {
        // divide the 128 bits value by a power of 10 to have it fit into a long and increase scale accordingly
        // the result is rounded using the remainder of the division
        int delta = Math.min(sOverflow, LONG_TEN_POWERS_TABLE.length) - 1;
        exp(checkExponent(this.exp + delta));

        long save = this.mantissa; // save the value, the instance is used as a buffer to collect the remainder
        long remainder;
        try {
            long div = LONG_TEN_POWERS_TABLE[delta];
            if (Long.compareUnsigned(x, div) < 0) {
                y = divideOrRound128(x, y, div, 0, this);
                x = 0L;
            } else {
                var rHi = Long.remainderUnsigned(x, div);
                x = Long.divideUnsigned(x, div);
                y = divideOrRound128(rHi, y, div, 0, this);
            }
        } finally {
            // the remainder was set into this.value
            // it's more straightforward this way than relying on escape analysis to avoid a buffer allocation
            remainder = this.mantissa;
            this.mantissa = save; // restore the value
        }

        boolean minLongValue = x == 0L && sign < 0 && y == Long.MIN_VALUE;
        if(!minLongValue && (x != 0L || y < 0L)) {
            // slow path, one more div, it happens in 2.6% of tests, optimization would be to find to a more accurate ten power first (delta)
            set(divideOrRound128(x, y, 10, sign, null), checkExponent(this.exp + 1));
        } else {
            // remainder round
            remainder = Math.abs(remainder) >= (LONG_TEN_POWERS_TABLE[delta] >> 1) ? 1 : 0;
            mantissa(sign >= 0 ? y + remainder : -(y + remainder));
        }
    }

    private static long multiplyDivide128(long x, long y, long divisor) {
        // y always > 0
        int sign = Long.signum(x) * Long.signum(divisor);
        if(0 == sign) { // x can be 0, fast path
            return 0L;
        }
        x = Math.abs(x);
        long hi0, lo0;
        if(1L == requirePositive(y) || 1L == x) {
            hi0 = 0L;
            lo0 = 1L == x ? y : x;
        } else {
            lo0 = y & LONG_MASK;
            hi0 = x & LONG_MASK;
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
            hi0 = ((product >>> 32) + hi0) & LONG_MASK;
            hi0 = hi0 << 32 | (product & LONG_MASK);
            lo0 = hi1 << 32 | lo1;
        }

        if(Long.MIN_VALUE == divisor) { // this value can't be abs(), divide it directly
            long remainder = Long.MAX_VALUE & lo0;
            lo0 = shift128RightLowBits(hi0, lo0, 63);
            remainder = remainder >= -(divisor >> 1) ? 1 : 0;
            return sign * (lo0 + remainder);
        }
        return divideOrRound128(hi0, lo0, Math.abs(divisor), sign, null);
    }

    static long shift128RightLowBits(long nHi, long nLo, int bits) {
        bits = bits & 127;
        if(bits != 0) {
            if (bits < 64) {
                return (nLo >>> bits) | (nHi << (64 - bits));
            } else if (bits > 64) {
                return nHi >>> (bits - 64);
            } else {
                return nHi;
            }
        } else {
            return nLo;
        }
    }

    // divide 128-bit value by long divisor, knuth
    // if a remainder buffer is provided, return the unsigned quotient and the remainder filled (sign is ignored)
    // otherwise return a rounded signed quotient (half up rounding, this may overflow)
    public static long divideOrRound128(long dividendHi, long dividendLo, long divisor, int sign, @Nullable MutableDecimal remainder) {
        if (null == remainder && dividendHi >= divisor) { // null == remainder is rounding mode
            throw new OverflowException(false);
        }

        int shift = Long.numberOfLeadingZeros(divisor);
        long tmp = divisor << shift;
        long v1 = tmp >>> 32;
        long v0 = tmp & LONG_MASK;
        tmp = (dividendHi << shift) | (dividendLo >>> 64 - shift);
        long qHi = dividendLo << shift;
        dividendHi = qHi >>> 32;
        dividendLo = qHi & LONG_MASK;
        qHi = tmp & LONG_MASK;

        // loop 2 times, first time computing qHi then qLo into q
        for(boolean q0Turn = false; ; q0Turn = true) {
            long q;
            if (v1 == 1) {
                q = tmp;
                tmp = 0;
            } else if (tmp >= 0) {
                q = tmp / v1;
                tmp = tmp - q * v1;
            } else { // tmp, q = divRemNegativeLong(tmp, v1)
                q = (tmp >>> 1) / (v1 >>> 1);
                tmp = tmp - q * v1;
                // Correct the approximation
                while (tmp < 0) {
                    tmp += v1;
                    q--;
                }
                while (tmp >= v1) {
                    tmp -= v1;
                    q++;
                }
            }
            while(q >= DIV_NUM_BASE || ((q * v0 + Long.MIN_VALUE) > (((tmp << 32) | (q0Turn ? dividendLo : dividendHi)) + Long.MIN_VALUE))) {
                q--;
                tmp += v1;
                if (tmp >= DIV_NUM_BASE)
                    break;
            }

            if(q0Turn) {
                // rem = mulsub(dividendHi, dividendLo, v1, v0, q) >>> shift
                tmp = dividendLo - (q * v0);
                tmp = ((dividendHi + (tmp >>> 32) - q * v1 << 32) | (tmp & LONG_MASK)) >>> shift;
                q = qHi << 32 | q;
                if(null != remainder) {
                    remainder.mantissa = tmp;
                } else { // do rounding
                    q *= sign;
                    if (tmp != 0) { // HALF_UP rounding
                        tmp = tmp >= ((divisor >> 1) + (divisor & 1)) ? sign : 0;
                        q += tmp; // can not overflow
                    }
                }
                return q;
            }
            if(null == remainder && (int) q < 0) { // rounding
                // result (which is positive and unsigned here) can't fit into signed long
                throw new OverflowException(false);
            }
            // tmp = mulsub(qHi, dividendHi, v1, v0, q)
            tmp = dividendHi - (q * v0);
            tmp = (qHi + (tmp >>> 32) - q * v1 << 32) | (tmp & LONG_MASK);
            qHi = q;
            dividendHi = tmp & LONG_MASK;
        }
    }

    @Override
    public final String toString() {
        var sb = new StringBuilder(20);
        toPlainString(sb);
        return sb.toString();
    }

    public final void toPlainString(@NotNull StringBuilder stringBuilder) {
        if(0L == mantissa || 0 == exp) {
            stringBuilder.append(mantissa);
            return;
        }
        int signum = Long.signum(mantissa);
        if(exp > 0) { // No decimal point
            if(signum == 0) {
                stringBuilder.append("0");
                return;
            }
            int trailingZeros = exp;
            stringBuilder.append(mantissa);
            for (int i = trailingZeros; i-- != 0;) {
                stringBuilder.append('0');
            }
            return;
        }
        stringBuilder.append(Math.abs(mantissa));
        if(Long.MIN_VALUE == mantissa) { // can't be positive
            stringBuilder.deleteCharAt(0);
        }
        fillValueString(stringBuilder, signum, exp);
    }

    private static void fillValueString(@NotNull StringBuilder intString, int signum, int scale) {
        /* Insert decimal point */
        int insertionPoint = intString.length() + scale;
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
