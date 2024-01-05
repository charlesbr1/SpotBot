package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.chart.Symbol.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class RangeAlert extends Alert {

    private final BigDecimal low;
    private final BigDecimal high;


    public RangeAlert(long userId, long serverId,
                      @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message) {
        this(IdGenerator.newId(), userId, serverId, exchange, ticker1, ticker2, low, high, message,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS);
    }
    private RangeAlert(long id, long userId, long serverId,
                       @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                       @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message,
                       @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                       short repeat, short repeatDelay) {
        super(id, userId, serverId, exchange, ticker1, ticker2, message, lastTrigger, margin, repeat, repeatDelay);
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
    public RangeAlert withMessage(@NotNull String message) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withMargin(@NotNull BigDecimal margin) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withRepeat(short repeat) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withRepeatDelay(short repeatDelay) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withLastTriggerMarginRepeat(@NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, low, high, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        for(Candlestick candlestick : candlesticks) {
            if(isNewerCandleStick(candlestick, previousCandlestick)) {
                if(priceInRange(candlestick, high, low, MARGIN_DISABLED) || priceCrossedRange(candlestick, high, low, previousCandlestick)) {
                    return new MatchingAlert(this.withLastTriggerMarginRepeat(ZonedDateTime.now(), MARGIN_DISABLED,
                            ((short) Math.max(0, repeat - 1))),
                            MATCHED, candlestick);
                } else if(priceInRange(candlestick, high,low, margin)) {
                    return new MatchingAlert(this.withMargin(MARGIN_DISABLED), MARGIN, candlestick);
                }
            }
            previousCandlestick = candlestick;
        }
        return new MatchingAlert(this, NO_TRIGGER, null);
    }

    private static boolean priceInRange(@NotNull Candlestick candlestick, @NotNull BigDecimal high, @NotNull BigDecimal low, @NotNull BigDecimal margin) {
        return candlestick.low().compareTo(high.add(margin)) <= 0 &&
                candlestick.high().compareTo(low.subtract(margin)) >= 0;
    }

    private static boolean priceCrossedRange(@NotNull Candlestick candlestick, @NotNull BigDecimal high, @NotNull BigDecimal low, @Nullable Candlestick previousCandlestick) {
        return null != previousCandlestick &&
                previousCandlestick.low().min(candlestick.low()).compareTo(high) <= 0 &&
                previousCandlestick.high().max(candlestick.high()).compareTo(low) >= 0;
    }


    @Override
    @NotNull
     protected String asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return header(matchingStatus) +
                "\n\n* id :\t" + id +
                "\n* low :\t" + low.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* high :\t" + high.toPlainString() + ' ' + getSymbol(ticker2) +
                footer(matchingStatus, previousCandlestick);
    }
}
