package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;

import static java.math.RoundingMode.FLOOR;
import static org.sbot.chart.Symbol.getSymbol;
import static org.sbot.utils.ArgumentValidator.requirePositive;
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
                DEFAULT_REPEAT, DEFAULT_REPEAT_DELAY_HOURS, DEFAULT_MARGIN);
    }

    private TrendAlert(long id, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2,
                      @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                      @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate,
                      @NotNull String message,
                      short repeat, short repeatDelay, short margin) {
        super(id, userId, serverId, exchange, ticker1, ticker2, message, repeat, repeatDelay, margin);
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
    public TrendAlert withRepeat(short repeat) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, repeat, repeatDelay, margin);
    }

    @Override
    @NotNull
    public TrendAlert withRepeatDelay(short delay) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, repeat, delay, margin);
    }

    @Override
    @NotNull
    public TrendAlert withMargin(short margin) {
        return new TrendAlert(id, userId, serverId, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message, repeat, repeatDelay, margin);
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

    @Override
    public boolean inMargin(@NotNull Candlestick candlestick) {
        return true;
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

    @Override
    @NotNull
    protected String asMessage(boolean triggered) {
        return header(triggered) +
                "\n\n* id :\t" + id +
                "\n* from price :\t" + fromPrice.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* from date :\t" + fromDate.format(DATE_TIME_FORMATTER) +
                "\n* to price :\t" + toPrice.toPlainString() + ' ' + getSymbol(ticker2) +
                "\n* to date :\t" + toDate.format(DATE_TIME_FORMATTER) +
                footer();
    }
}
