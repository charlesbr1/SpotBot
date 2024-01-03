package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.math.RoundingMode.FLOOR;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;

public final class TrendAlert extends Alert {

    private final BigDecimal fromPrice;
    private final ZonedDateTime fromDate;
    private final BigDecimal toPrice;
    private final ZonedDateTime toDate;

    public TrendAlert(long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                      @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate,
                      @NotNull String message) {
        this(IdGenerator.newId(), userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message,
                DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS, DEFAULT_THRESHOLD);
    }

    private TrendAlert(long id, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                      @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate,
                      @NotNull String message,
                      short repeat, short repeatDelay, short threshold) {
        super(id, userId, serverId, exchange, ticker1, ticker2, message, repeat, repeatDelay, threshold);
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("first date is after second date");
        }
        this.fromPrice = fromPrice.stripTrailingZeros();
        this.fromDate = fromDate;
        this.toPrice = toPrice.stripTrailingZeros();
        this.toDate = toDate;
    }

    @Override
    public TrendAlert withRepeat(short repeat) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, repeat, repeatDelay, threshold);
    }

    @Override
    public TrendAlert withRepeatDelay(short delay) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, repeat, delay, threshold);
    }

    @Override
    public TrendAlert withThreshold(short threshold) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, repeat, repeatDelay, threshold);
    }

    @Override
    public boolean match(@NotNull Candlestick candlestick) {
        try {
            BigDecimal currentTrendPrice = currentTrendPrice();
            return isNewerCandleStick(candlestick) &&
                    (priceOnTrend(candlestick, currentTrendPrice) || priceCrossedTrend(candlestick, currentTrendPrice));
        } finally {
            lastCandlestick = candlestick;
        }
    }

    private boolean priceOnTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal currentTrendPrice) {
        return candlestick.low().compareTo(currentTrendPrice) <= 0 &&
                candlestick.high().compareTo(currentTrendPrice) >= 0;
    }

    private boolean priceCrossedTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal currentTrendPrice) {
        return null != lastCandlestick &&
                lastCandlestick.low().min(candlestick.low()).compareTo(currentTrendPrice) <= 0 &&
                lastCandlestick.high().max(candlestick.high()).compareTo(currentTrendPrice) >= 0;
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

    @NotNull
    @Override
    public String triggerMessage() {
        return "Trend Alert set by <@" + userId + "> with id " + id + " on " + exchange + " [" + getSlashPair() +
                "] fired !\n\nPrice crossed trend\n\nfrom : " +
                fromPrice.toPlainString() + ' ' + ticker2 + " at " + fromDate.format(DATE_TIME_FORMATTER) +
                "\nto : " + toPrice.toPlainString() + ' ' + ticker2 + " at " + toDate.format(DATE_TIME_FORMATTER) +
                Optional.ofNullable(lastCandlestick).map(Candlestick::close)
                        .map(BigDecimal::toPlainString)
                        .map("\n\nLast close : "::concat).orElse("");
    }

    @NotNull
    @Override
    public String descriptionMessage() {
        return "Trend Alert set by <@" + userId + "> on " + exchange +" [" + getSlashPair() +
                "]\n* id :\t" + id +
                "\n* from price :\t" + fromPrice.toPlainString() + ' ' + ticker2 +
                "\n* from date :\t" + fromDate.format(DATE_TIME_FORMATTER) +
                "\n* to price :\t" + toPrice.toPlainString() + ' ' + ticker2 +
                "\n* to date :\t" + toDate.format(DATE_TIME_FORMATTER) +
                Optional.ofNullable(lastCandlestick).map(Candlestick::close)
                        .map(BigDecimal::toPlainString)
                        .map("\n\nLast close : "::concat).orElse("");
    }
}
