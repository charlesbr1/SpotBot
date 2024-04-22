package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;
import static org.sbot.utils.MutableDecimal.empty;

public interface MutableDecimalParser {

    int MAX_COMPACT_DIGITS = 18;

    @NotNull
    static MutableDecimal parse(@NotNull CharSequence value) {
        MutableDecimal priceBuffer = empty();
        parse(value, priceBuffer);
        return priceBuffer;
    }

    private static void parse(@NotNull CharSequence value, @NotNull MutableDecimal priceBuffer) {
        parse(value, priceBuffer, 0, value.length());
    }

    // code adapted from JDK BigDecimal::new(char[] in, int offset, int len, MathContext mc)
    // if fills priceBuffer with not inflated value instead of creating a new BigDecimal
    // or throws an exception if the value overflow compact format
    static void parse(@NotNull CharSequence value, @NotNull MutableDecimal priceBuffer, int offset, int length) {
        requireNonNull(priceBuffer);
        requirePositive(offset);
        requireStrictlyPositive(length);
        // handle the sign
        char firstChar = value.charAt(offset);
        boolean isneg = firstChar == '-';
        int len = length;
        if (isneg || firstChar == '+') {
            offset++;
            len--;
        }

        int startingOffset = startingOffset(value, offset);
        len -= (startingOffset - offset);
        offset = startingOffset;
        if(length - offset > MAX_COMPACT_DIGITS) {
            throw new NumberFormatException ("Provided value '" + value + "'is too long : length " + value.length() + ", max : " + MAX_COMPACT_DIGITS);
        }

        long scale = 0;
        long intCompact = 0;

        try {
            int prec = 0;
            boolean dot = false;
            for (; len > 0; offset++, len--) {
                char c = value.charAt(offset);
                if ((c == '0')) { // have zero
                    if (prec == 0) {
                        prec = 1;
                    } else if (intCompact != 0) {
                        intCompact *= 10;
                        ++prec;
                    } // else digit is a redundant leading zero
                    if (dot)
                        ++scale;
                } else if ((c >= '1' && c <= '9')) { // have digit
                    int digit = c - '0';
                    if (prec != 1 || intCompact != 0)
                        ++prec; // prec unchanged if preceded by 0s
                    intCompact = intCompact * 10 + digit;
                    if (dot)
                        ++scale;
                } else if (c == '.') {   // have dot
                    // have dot
                    if (dot) // two dots
                        throw new NumberFormatException("Provided value contains more than one decimal point");
                    dot = true;
                } else if (Character.isDigit(c)) { // slow path
                    int digit = Character.digit(c, 10);
                    if (digit == 0) {
                        if (prec == 0) {
                            prec = 1;
                        } else if (intCompact != 0) {
                            intCompact *= 10;
                            ++prec;
                        } // else digit is a redundant leading zero
                    } else {
                        if (prec != 1 || intCompact != 0)
                            ++prec; // prec unchanged if preceded by 0s
                        intCompact = intCompact * 10 + digit;
                    }
                    if (dot)
                        ++scale;
                } else {
                    throw new NumberFormatException("Character " + c + " is not a decimal digit number or a decimal point");
                }
            }
            if (prec == 0) { // no digits found
                throw new NumberFormatException("No digits found");
            }
            intCompact = isneg ? -intCompact : intCompact;

        } catch (IndexOutOfBoundsException e) {
            NumberFormatException nfe = new NumberFormatException();
            nfe.initCause(e);
            throw nfe;
        }
        priceBuffer.value(intCompact);
        priceBuffer.scale((byte) scale); // scale can not overflow here
    }

    // skip any heading zero, this could help the number to fit into compact format
    private static int startingOffset(@NotNull CharSequence value, int offset) {
        if(value.length() > offset + 1) {
            var c = value.charAt(offset);
            while (value.length() > offset + 1 && c == '0') {
                c = value.charAt(offset + 1);
                if (!Character.isDigit(c)) {
                    break;
                }
                offset++;
            }
        }
        return offset;
    }
}
