package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.math.BigDecimal;

public final class RangeAlert extends Alert {

    private final BigDecimal low;
    private final BigDecimal high;


    public RangeAlert(long userId, long serverId,
                      @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message) {
        this(IdGenerator.newId(), userId, serverId, exchange, ticker1, ticker2, low, high, message,
                DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS, DEFAULT_THRESHOLD);
    }
    private RangeAlert(long id, long userId, long serverId,
                      @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message,
                      short occurrence, short delay, short threshold) {
        super(id, userId, serverId, exchange, ticker1, ticker2, message, occurrence, delay, threshold);
        if(low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low price is higher than high price");
        }
        this.low = low.stripTrailingZeros();
        this.high = high.stripTrailingZeros();
    }

    @Override
    public RangeAlert withRepeat(short repeat) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, repeat, repeatDelay, threshold);
    }

    @Override
    public RangeAlert withRepeatDelay(short delay) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, repeat, delay, threshold);
    }

    @Override
    public RangeAlert withThreshold(short threshold) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, repeat, repeatDelay, threshold);
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
    public String triggerMessage() {
        return "Range Alert set by <@" + userId + "> with id " + id + ", exchange " + exchange +
                ", pair " + getSlashPair() + ", price reached box from " + low + " to " + high +
                ".\nLast candlestick : " + lastCandlestick +
                "\n" + message;
    }
   @NotNull
    @Override
    public String descriptionMessage(@NotNull String userName) {
        return "Range Alert set by @" + userName + " on " + exchange +" [" + getSlashPair() +
                "]\n* id :\t" + id + "\n* low :\t" + low + "\n* high :\t" + high +
                "\n\nLast close : " + lastCandlestick.close();
    }
}
