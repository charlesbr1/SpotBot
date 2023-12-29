package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;

public final class RangeAlert extends Alert {

    private final BigDecimal low;
    private final BigDecimal high;


    public RangeAlert(@NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message, @NotNull String owner) {
        super(exchange, ticker1, ticker2, message, owner);
        if(low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low price is higher than high price");
        }
        this.low = low;
        this.high = high;
    }

    @Override
    public boolean match(@NotNull Candlestick candlestick) {
        try {
            return isNewerCandleStick(candlestick) &&
                    (priceInRange(candlestick) || priceCrossedRange(candlestick));
        } finally {
            lastCandlestick = candlestick;
        }
    }

    private boolean priceInRange(@NotNull Candlestick candlestick) {
        return candlestick.low().compareTo(high) <= 0 &&
                candlestick.high().compareTo(low) >= 0;
    }

    private boolean priceCrossedRange(@NotNull Candlestick candlestick) {
        return null != lastCandlestick &&
                lastCandlestick.low().min(candlestick.low()).compareTo(high) <= 0 &&
                lastCandlestick.high().max(candlestick.high()).compareTo(low) >= 0;
    }

    @NotNull
    @Override
    public String notification() {
        return "Range Alert set by " + owner + " with id " + id + ", exchange " + exchange +
                ", pair " + getReadablePair() + ", price reached box from " + low + " to " + high +
                ".\nLast candlestick : " + lastCandlestick +
                "\n" + message;
    }
}
