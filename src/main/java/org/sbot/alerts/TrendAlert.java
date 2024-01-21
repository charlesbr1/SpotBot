package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static java.math.RoundingMode.HALF_UP;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.chart.Ticker.formatPrice;
import static org.sbot.chart.Ticker.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendAlert extends Alert {

    public TrendAlert(long id, long userId, long serverId, @NotNull String exchange,
                      @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                      short repeat, short snooze) {
        super(id, Type.trend, userId, serverId, exchange, pair, message, requirePositive(fromPrice), requirePositive(toPrice),
                fromDate, toDate, lastTrigger, margin, repeat, snooze);
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from_date is after to_date");
        } else if(fromDate.compareTo(toDate) == 0) {
            throw new IllegalArgumentException("from_date and to_date can not be the same");
        }
    }

    @Override
    @NotNull
    public TrendAlert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                            BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate,
                            ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short snooze) {
        return new TrendAlert(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @Override
    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        BigDecimal currentTrendPrice = currentTrendPrice(fromPrice, toPrice, fromDate, toDate);
        for(Candlestick candlestick : candlesticks) {
            if(isNewerCandleStick(candlestick, previousCandlestick)) {
                if(priceOnTrend(candlestick, currentTrendPrice, MARGIN_DISABLED) || priceCrossedTrend(candlestick, currentTrendPrice, previousCandlestick)) {
                    return new MatchingAlert(this, MATCHED, candlestick);
                } else if(priceOnTrend(candlestick, currentTrendPrice, margin)) {
                    return new MatchingAlert(this, MARGIN, candlestick);
                }
                previousCandlestick = candlestick;
            }
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    private static boolean priceOnTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal currentTrendPrice, @NotNull BigDecimal margin) {
        return RangeAlert.priceInRange(candlestick, currentTrendPrice, currentTrendPrice, margin);
    }

    private static boolean priceCrossedTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal currentTrendPrice, @Nullable Candlestick previousCandlestick) {
        return RangeAlert.priceCrossedRange(candlestick, currentTrendPrice, currentTrendPrice, previousCandlestick);
    }

    @NotNull
    static BigDecimal currentTrendPrice(@NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate) {
        return fromPrice.add(priceDelta(ZonedDateTime.now(), fromPrice, toPrice, fromDate, toDate)).max(BigDecimal.ZERO);
    }

    static BigDecimal priceDelta(@NotNull ZonedDateTime atDate, @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate) {
        BigDecimal delta = toPrice.subtract(fromPrice).multiply(secondsBetween(fromDate, atDate));
        BigDecimal deltaSeconds = secondsBetween(fromDate, toDate);
        return BigDecimal.ZERO.equals(deltaSeconds) ? delta : delta.divide(deltaSeconds, 16, HALF_UP);
    }

    @NotNull
    static BigDecimal secondsBetween(@NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime date) {
        long durationSec = Duration.between(fromDate, date).toSeconds();
        return durationSec != 0 ? new BigDecimal(durationSec) : BigDecimal.ZERO;
    }

    @Override
    @NotNull
    protected String asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return header(matchingStatus) +
                "\n\n* id :\t" + id +
                "\n* from price :\t" + fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()) +
                "\n* from date :\t" + formatUTC(fromDate) +
                "\n* to price :\t" + toPrice.toPlainString() + ' ' + getSymbol(getTicker2()) +
                "\n* to date :\t" + formatUTC(toDate) +
                "\n* current trend price :\t" +
                formatPrice(currentTrendPrice(fromPrice, toPrice, fromDate, toDate), getTicker2()) +
                footer(matchingStatus, previousCandlestick);
    }
}
