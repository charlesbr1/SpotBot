package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import static org.sbot.utils.MutableDecimal.ImmutableDecimal.ZERO;

public interface Tickers {

    @NotNull
    static String getSymbol(@NotNull String ticker) {
        return ticker.length() <= 4 && ticker.contains("USD") ? "$" :
                (ticker.equals("EUR") ? "€" :
                (ticker.equals("YEN") ? "¥" :
                (ticker.equals("GBP") ? "£" :
                (ticker.equals("BTC") ? "₿" : ticker.toLowerCase()))));
    }

    static String formatPrice(@NotNull MutableDecimal price, @NotNull String ticker) {
        if (price.compareTo(ZERO) == 0) {
            return "0 " + getSymbol(ticker);
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat decimalFormat = new DecimalFormat();
        decimalFormat.setGroupingUsed(false);
        decimalFormat.setDecimalFormatSymbols(symbols);
        decimalFormat.setMinimumFractionDigits(0);
        var priceBigDecimal = price.bigDecimal();
        BigDecimal absPrice = priceBigDecimal.abs();
        if (absPrice.compareTo(BigDecimal.TWO) > 0) { // 2 digits after the comma
            decimalFormat.setMaximumFractionDigits(2);
        } else if (absPrice.compareTo(BigDecimal.ONE) >= 0) { // 4 digits after the comma
            decimalFormat.setMaximumFractionDigits(4);
        } else { // 5 digits after the zeros after the comma
            BigDecimal fractionalPart = absPrice.remainder(BigDecimal.ONE);
            int nbZerosPlusOne = 0;
            while (fractionalPart.compareTo(BigDecimal.ONE) < 0) {
                fractionalPart = fractionalPart.multiply(BigDecimal.TEN);
                nbZerosPlusOne++;
            }
            decimalFormat.setMaximumFractionDigits(Math.min(16, nbZerosPlusOne + 4));
        }
        return decimalFormat.format(priceBigDecimal) + ' ' + getSymbol(ticker);
    }
}
