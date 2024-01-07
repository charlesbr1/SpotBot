package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;

import java.beans.ConstructorProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static java.math.RoundingMode.FLOOR;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.chart.Symbol.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendAlert extends Alert {

    private final BigDecimal fromPrice;
    private final ZonedDateTime fromDate;
    private final BigDecimal toPrice;
    private final ZonedDateTime toDate;

    public TrendAlert(long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                      @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate,
                      @NotNull String message) {
        this(0, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS);
    }

    @ConstructorProperties({"id", "user_id", "server_id", "exchange", "ticker1", "ticker2",
            "fromPrice", "fromDate", "toPrice", "toDate", "message", "last_trigger", "margin"})
    public TrendAlert(long id, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                      @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate,
                      @NotNull String message, @Nullable ZonedDateTime lastTrigger,
                      @NotNull BigDecimal margin, short repeat, short repeatDelay) {
        super(id, userId, serverId, exchange, ticker1, ticker2, message, lastTrigger, margin, repeat, repeatDelay);
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("first date is after second date");
        }
        this.fromPrice = requirePositive(fromPrice).stripTrailingZeros();
        this.fromDate = fromDate;
        this.toPrice = requirePositive(toPrice).stripTrailingZeros();
        this.toDate = toDate;
    }

    @NotNull
    @Override
    public String name() {
        return "Trend";
    }

    @Override
    @NotNull
    public TrendAlert withId(long id) {
        if(0 != this.id) {
            throw new IllegalArgumentException("Can't update the id of an already stored alert");
        }
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public TrendAlert withMessage(@NotNull String message) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public TrendAlert withMargin(@NotNull BigDecimal margin) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public TrendAlert withRepeat(short repeat) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public TrendAlert withRepeatDelay(short repeatDelay) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public TrendAlert withLastTriggerMarginRepeat(@NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, lastTrigger, margin, repeat, repeatDelay);
    }

    @Override
    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        BigDecimal currentTrendPrice = currentTrendPrice();
        for(Candlestick candlestick : candlesticks) {
            if(isNewerCandleStick(candlestick, previousCandlestick)) {
                if(priceOnTrend(candlestick, currentTrendPrice, MARGIN_DISABLED) || priceCrossedTrend(candlestick, currentTrendPrice, previousCandlestick)) {
                    return new MatchingAlert(this.withLastTriggerMarginRepeat(ZonedDateTime.now(), MARGIN_DISABLED,
                            ((short) Math.max(0, repeat - 1))),
                            MATCHED, candlestick);
                } else if(priceOnTrend(candlestick, currentTrendPrice, margin)) {
                    return new MatchingAlert(this.withMargin(MARGIN_DISABLED), MARGIN, candlestick);
                }
            }
            previousCandlestick = candlestick;
        }
        return new MatchingAlert(this, NO_TRIGGER, null);
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
                "\n* from price :\t" + fromPrice.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* from date :\t" + formatUTC(fromDate) +
                "\n* to price :\t" + toPrice.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* to date :\t" + formatUTC(toDate) +
                footer(matchingStatus, previousCandlestick);
    }
}
