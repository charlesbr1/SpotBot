package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.chart.Ticker.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class RangeAlert extends Alert {

    public RangeAlert(long id, long userId, long serverId, @NotNull String exchange,
                      @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                      short repeat, short repeatDelay) {
        super(id, Type.range, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
        if(fromPrice.compareTo(toPrice) > 0) {
            throw new IllegalArgumentException("from_price is higher than to_price");
        }
        requirePositive(fromPrice);
        requirePositive(toPrice);
    }

    @Override
    public RangeAlert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                            BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate,
                            ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short repeatDelay) {
        return new RangeAlert(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
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
        return new MatchingAlert(this, NOT_MATCHING, null);
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
                "\n* low :\t" + fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()) +
                "\n* high :\t" + toPrice.toPlainString() + ' ' + getSymbol(getTicker2()) +
                footer(matchingStatus, previousCandlestick);
    }
}
