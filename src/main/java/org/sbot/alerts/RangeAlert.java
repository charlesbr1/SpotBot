package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.math.BigDecimal;
import java.util.Optional;

import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class RangeAlert extends Alert {

    private final BigDecimal low;
    private final BigDecimal high;


    public RangeAlert(long userId, long serverId,
                      @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message) {
        this(IdGenerator.newId(), userId, serverId, exchange, ticker1, ticker2, low, high, message,
                DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS, DEFAULT_MARGIN);
    }
    private RangeAlert(long id, long userId, long serverId,
                      @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message,
                      short repeat, short repeatDelay, short margin) {
        super(id, userId, serverId, exchange, ticker1, ticker2, message, repeat, repeatDelay, margin);
        if(low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low price is higher than high price");
        }
        this.low = requirePositive(low).stripTrailingZeros();
        this.high = requirePositive(high).stripTrailingZeros();
    }

    @NotNull
    @Override
    public String name() {
        return "Range";
    }

    @Override
    @NotNull
    public RangeAlert withRepeat(short repeat) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, repeat, repeatDelay, margin);
    }

    @Override
    @NotNull
    public RangeAlert withRepeatDelay(short delay) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, repeat, delay, margin);
    }

    @Override
    @NotNull
    public RangeAlert withMargin(short margin) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, repeat, repeatDelay, margin);
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

    @Override
    public boolean inMargin(@NotNull Candlestick candlestick) {
        return true;
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


    @Override
    @NotNull
     protected String asMessage(boolean triggered) {
        return header(triggered) +
                "\n\n* id :\t" + id +
                "\n* low :\t" + low.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* high :\t" + high.toPlainString() + ' ' + getSymbol(ticker2) +
                footer();
    }
}
