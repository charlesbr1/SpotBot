package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.chart.Symbol.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class RangeAlert extends Alert {

    public RangeAlert(long userId, long serverId, @NotNull String exchange,
                      @NotNull String ticker1, @NotNull String ticker2, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate) {
        this(0, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS);
    }

    public RangeAlert(long id, long userId, long serverId, @NotNull String exchange,
                      @NotNull String ticker1, @NotNull String ticker2, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                      short repeat, short repeatDelay) {
        super(id, Type.range, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
        if(fromPrice.compareTo(toPrice) > 0) {
            throw new IllegalArgumentException("from_price is higher than to_price");
        }
        requirePositive(fromPrice);
        requirePositive(toPrice);
    }

    @Override
    @NotNull
    public RangeAlert withId(@NotNull Supplier<Long> idGenerator) {
        if(0 != this.id) {
            throw new IllegalArgumentException("Can't update the id of an already stored alert");
        }
        return new RangeAlert(idGenerator.get(), userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withMessage(@NotNull String message) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withMargin(@NotNull BigDecimal margin) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withRepeat(short repeat) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withRepeatDelay(short repeatDelay) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public RangeAlert withLastTriggerMarginRepeat(@NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        return new RangeAlert(id, userId, serverId, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        for(Candlestick candlestick : candlesticks) {
            if(isNewerCandleStick(candlestick, previousCandlestick)) {
                if(priceInRange(candlestick, fromPrice, toPrice, MARGIN_DISABLED) || priceCrossedRange(candlestick, fromPrice, toPrice, previousCandlestick)) {
                    return new MatchingAlert(this, MATCHED, candlestick);
                } else if(priceInRange(candlestick, fromPrice,toPrice, margin)) {
                    return new MatchingAlert(this, MARGIN, candlestick);
                }
            }
            previousCandlestick = candlestick;
        }
        return new MatchingAlert(this, NO_TRIGGER, null);
    }

    private static boolean priceInRange(@NotNull Candlestick candlestick, @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @NotNull BigDecimal margin) {
        return candlestick.low().compareTo(toPrice.add(margin)) <= 0 &&
                candlestick.high().compareTo(fromPrice.subtract(margin)) >= 0;
    }

    private static boolean priceCrossedRange(@NotNull Candlestick candlestick, @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @Nullable Candlestick previousCandlestick) {
        return null != previousCandlestick &&
                previousCandlestick.low().min(candlestick.low()).compareTo(toPrice) <= 0 &&
                previousCandlestick.high().max(candlestick.high()).compareTo(fromPrice) >= 0;
    }


    @Override
    @NotNull
     protected String asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return header(matchingStatus) +
                "\n\n* id :\t" + id +
                "\n* low :\t" + fromPrice.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* high :\t" + toPrice.toPlainString() + ' ' + getSymbol(ticker2) +
                footer(matchingStatus, previousCandlestick);
    }
}
