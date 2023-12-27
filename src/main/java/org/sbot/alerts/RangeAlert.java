package org.sbot.alerts;

import org.sbot.chart.Candlestick;

import java.math.BigDecimal;

public final class RangeAlert extends Alert {

    private final BigDecimal low;
    private final BigDecimal high;


    public RangeAlert(String exchange, String ticker1, String ticker2, BigDecimal low, BigDecimal high, String message, String owner) {
        super(exchange, ticker1, ticker2, message, owner);
        this.low = low;
        this.high = high;
    }

    @Override
    public boolean match(Candlestick candlestick) {
        try {
            return isNewerCandleStick(candlestick) &&
                    (priceInRange(candlestick) || priceCrossedRange(candlestick));
        } finally {
            lastCandlestick = candlestick;
        }
    }

    private boolean priceInRange(Candlestick candlestick) {
        return candlestick.low().compareTo(high) <= 0 &&
                candlestick.high().compareTo(low) >= 0;
    }

    private boolean priceCrossedRange(Candlestick candlestick) {
        return null != lastCandlestick &&
                lastCandlestick.low().min(candlestick.low()).compareTo(high) <= 0 &&
                lastCandlestick.high().max(candlestick.high()).compareTo(low) >= 0;
    }

    @Override
    public String notification() {
        return "Range Alert set by " + owner + " with id " + id + ", exchange " + exchange +
                ", pair " + getReadablePair() + ", price reached box from " + low + " to " + high +
                ".\nLast candlestick : " + lastCandlestick +
                "\n" + message;
    }
}
