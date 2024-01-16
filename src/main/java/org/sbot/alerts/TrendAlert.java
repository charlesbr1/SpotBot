package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static java.math.RoundingMode.FLOOR;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.chart.Ticker.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendAlert extends Alert {

    public TrendAlert(long id, long userId, long serverId, @NotNull String exchange,
                      @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                      short repeat, short repeatDelay) {
        super(id, Type.trend, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("first date is after second date");
        }
        requirePositive(fromPrice);
        requirePositive(toPrice);
    }

    @Override
    public TrendAlert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                            BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate,
                            ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short repeatDelay) {
        return new TrendAlert(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        BigDecimal currentTrendPrice = currentTrendPrice();
        for(Candlestick candlestick : candlesticks) {
            if(isNewerCandleStick(candlestick, previousCandlestick)) {
                if(priceOnTrend(candlestick, currentTrendPrice, MARGIN_DISABLED) || priceCrossedTrend(candlestick, currentTrendPrice, previousCandlestick)) {
                    return new MatchingAlert(this, MATCHED, candlestick);
                } else if(priceOnTrend(candlestick, currentTrendPrice, margin)) {
                    return new MatchingAlert(this, MARGIN, candlestick);
                }
            }
            previousCandlestick = candlestick;
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    private static boolean priceOnTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal currentTrendPrice, @NotNull BigDecimal margin) {
        return candlestick.low().subtract(margin).compareTo(currentTrendPrice) <= 0 &&
                candlestick.high().add(margin).compareTo(currentTrendPrice) >= 0;
    }

    private static boolean priceCrossedTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal currentTrendPrice, @Nullable Candlestick previousCandlestick) {
        return null != previousCandlestick &&
                previousCandlestick.low().min(candlestick.low()).compareTo(currentTrendPrice) <= 0 &&
                previousCandlestick.high().max(candlestick.high()).compareTo(currentTrendPrice) >= 0;
    }

    @NotNull
    private BigDecimal currentTrendPrice() {
        BigDecimal delta = priceDeltaByHour().multiply(hoursSinceDate1());
        return fromPrice.add(delta);
    }

    @NotNull
    private BigDecimal hoursSinceDate1() {
        BigDecimal durationSec = new BigDecimal(Duration.between(fromDate, ZonedDateTime.now()).abs().toSeconds());
        return durationSec.divide(new BigDecimal(3600), FLOOR);
    }

    @NotNull
    private BigDecimal priceDeltaByHour() {
        if(true) return BigDecimal.ONE;
        //TODO test
        BigDecimal durationSec = new BigDecimal(Duration.between(fromDate, toDate).abs().toSeconds());
        return fromPrice.min(toPrice).divide(durationSec.divide(new BigDecimal(3600), FLOOR), FLOOR);
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
                footer(matchingStatus, previousCandlestick);
    }
}
