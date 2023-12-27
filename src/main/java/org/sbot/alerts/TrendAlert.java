package org.sbot.alerts;

import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import static java.math.RoundingMode.FLOOR;

public final class TrendAlert extends Alert {

    private final BigDecimal price1;
    private final ZonedDateTime date1;
    private final BigDecimal price2;
    private final ZonedDateTime date2;

    public TrendAlert(String exchange, String ticker1, String ticker2, BigDecimal price1, ZonedDateTime date1, BigDecimal price2, ZonedDateTime date2, String message, String owner) {
        super(exchange, ticker1, ticker2, message, owner);
        this.price1 = price1;
        this.date1 = date1;
        this.price2 = price2;
        this.date2 = date2;
    }

    @Override
    public boolean match(Candlestick candlestick) {
        try {
            BigDecimal currentTrendPrice = currentTrendPrice();
            return isNewerCandleStick(candlestick) &&
                    (priceOnTrend(candlestick, currentTrendPrice) || priceCrossedTrend(candlestick, currentTrendPrice));
        } finally {
            lastCandlestick = candlestick;
        }
    }

    private boolean priceOnTrend(Candlestick candlestick, BigDecimal currentTrendPrice) {
        return candlestick.low().compareTo(currentTrendPrice) <= 0 &&
                candlestick.high().compareTo(currentTrendPrice) >= 0;
    }

    private boolean priceCrossedTrend(Candlestick candlestick, BigDecimal currentTrendPrice) {
        return null != lastCandlestick &&
                lastCandlestick.low().min(candlestick.low()).compareTo(currentTrendPrice) <= 0 &&
                lastCandlestick.high().max(candlestick.high()).compareTo(currentTrendPrice) >= 0;
    }

    private BigDecimal currentTrendPrice() {
        BigDecimal delta = priceDeltaByHour().multiply(hoursSinceDate1());
        return price1.add(delta);
    }

    private BigDecimal hoursSinceDate1() {
        BigDecimal durationSec = new BigDecimal(Duration.between(date1, Instant.now()).abs().toSeconds());
        return durationSec.divide(new BigDecimal(3600), FLOOR);
    }

    private BigDecimal priceDeltaByHour() {
        BigDecimal durationSec = new BigDecimal(Duration.between(date1, date2).abs().toSeconds());
        return price1.min(price2).divide(durationSec.divide(new BigDecimal(3600), FLOOR), FLOOR);
    }

    @Override
    public String notification() {
        return "Trend Alert set by " + owner + " with id " + id + ", exchange " + exchange +
                ", pair " + getReadablePair() + ", price crossed trend from " + price1 + " at " + date1 +
                " to " + price2 + " at " + date2 + ".\nLast candlestick : " + lastCandlestick +
                "\n" + message;
    }
}
