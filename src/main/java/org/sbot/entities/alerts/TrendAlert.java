package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import static java.math.RoundingMode.HALF_UP;
import static org.sbot.entities.chart.Ticker.formatPrice;
import static org.sbot.entities.chart.Ticker.getSymbol;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendAlert extends Alert {

    public TrendAlert(long id, long userId, long serverId, @NotNull Locale locale,
                      @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                      @NotNull String exchange, @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                      short repeat, short snooze) {
        super(id, Type.trend, userId, serverId, locale, creationDate, listeningDate, exchange, pair, message, requirePositive(fromPrice), requirePositive(toPrice),
                fromDate, toDate, lastTrigger, margin, repeat, snooze);
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from_date is after to_date");
        } else if(fromDate.compareTo(toDate) == 0) {
            throw new IllegalArgumentException("from_date and to_date can not be the same");
        }
    }

    @Override
    @NotNull
    public TrendAlert build(long id, long userId, long serverId, @NotNull Locale locale,
                            @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                            @NotNull String exchange, @NotNull String pair, @NotNull String message,
                            @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                            @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate,
                            @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        return new TrendAlert(id, userId, serverId, locale, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public MatchingAlert match(@NotNull ZonedDateTime actualTime, @NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        BigDecimal currentTrendPrice = currentTrendPrice(actualTime, fromPrice, toPrice, fromDate, toDate);
        for (Candlestick candlestick : candlesticks) {
            if (isListenableCandleStick(candlestick) && isNewerCandleStick(candlestick, previousCandlestick)) {
                if (priceOnTrend(candlestick, currentTrendPrice, MARGIN_DISABLED) ||
                        priceCrossedTrend(candlestick, currentTrendPrice, filterListenableCandleStick(previousCandlestick))) {
                    return new MatchingAlert(this, MATCHED, candlestick);
                } else if (priceOnTrend(candlestick, currentTrendPrice, margin)) {
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
    static BigDecimal currentTrendPrice(@NotNull ZonedDateTime actualTime, @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate) {
        return fromPrice.add(priceDelta(actualTime, fromPrice, toPrice, fromDate, toDate)).max(BigDecimal.ZERO);
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
    protected EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick, @NotNull ZonedDateTime now) {
        String description = header(matchingStatus, previousCandlestick, now) +
                "\n\n* id :\t" + id +
                footer(matchingStatus);
        var embed = new EmbedBuilder().setDescription(description);
        embed.addField("created", formatUTC(creationDate), true);
        embed.addField("from price", fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("from date", formatUTC(fromDate), true);
        embed.addField("current trend price", formatPrice(currentTrendPrice(now, fromPrice, toPrice, fromDate, toDate), getTicker2()), true);
        embed.addField("to price", toPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("to date", formatUTC(toDate), true);
        return embed;
    }
}
