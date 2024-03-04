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

import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static org.sbot.entities.chart.Ticker.formatPrice;
import static org.sbot.entities.chart.Ticker.getSymbol;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.Dates.formatDiscord;
import static org.sbot.utils.Dates.formatDiscordRelative;

public final class TrendAlert extends Alert {

    public TrendAlert(long id, long userId, long serverId,
                      @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                      @NotNull String exchange, @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                      short repeat, short snooze) {
        super(id, Type.trend, userId, serverId, creationDate, listeningDate, exchange, pair, message, requireNonNull(fromPrice), requireNonNull(toPrice),
                fromDate, toDate, lastTrigger, margin, repeat, snooze);
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from_date is after to_date");
        } else if(fromDate.isEqual(toDate)) {
            throw new IllegalArgumentException("from_date and to_date can not be the same");
        }
    }

    @Override
    @NotNull
    public TrendAlert build(long id, long userId, long serverId,
                            @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                            @NotNull String exchange, @NotNull String pair, @NotNull String message,
                            @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                            @NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate,
                            @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        return new TrendAlert(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        BigDecimal priceDelta = toPrice.subtract(fromPrice);
        BigDecimal deltaSeconds = secondsBetween(fromDate, toDate);
        for (Candlestick candlestick : candlesticks) {
            if (isListenableCandleStick(candlestick) && isNewerCandleStick(candlestick, previousCandlestick)) {
                BigDecimal startPrice = trendPriceAt(candlestick.openTime(), fromPrice, fromDate, priceDelta, deltaSeconds);
                BigDecimal endPrice = trendPriceAt(candlestick.closeTime(), fromPrice, fromDate, priceDelta, deltaSeconds);
                boolean uptrend = startPrice.compareTo(endPrice) <= 0;
                if (priceOnTrend(candlestick, startPrice, endPrice, MARGIN_DISABLED, uptrend) ||
                        priceCrossedTrend(candlestick, startPrice, endPrice, filterListenableCandleStick(previousCandlestick), uptrend)) {
                    return new MatchingAlert(this, MATCHED, candlestick);
                } else if (priceOnTrend(candlestick, startPrice, endPrice, margin, uptrend)) {
                    return new MatchingAlert(this, MARGIN, candlestick);
                }
                previousCandlestick = candlestick;
            }
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    private static boolean priceOnTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal startPrice, @NotNull BigDecimal endPrice, @NotNull BigDecimal margin, boolean uptrend) {
        return RangeAlert.priceInRange(candlestick, uptrend ? startPrice : endPrice, uptrend ? endPrice : startPrice, margin);
    }

    private static boolean priceCrossedTrend(@NotNull Candlestick candlestick, @NotNull BigDecimal startPrice, @NotNull BigDecimal endPrice, @Nullable Candlestick previousCandlestick, boolean uptrend) {
        return RangeAlert.priceCrossedRange(candlestick, uptrend ? startPrice : endPrice, uptrend ? endPrice : startPrice, previousCandlestick);
    }

    @NotNull
    public static BigDecimal trendPriceAt(@NotNull ZonedDateTime dateTime, @NotNull Alert alert) {
        return trendPriceAt(dateTime, alert.fromPrice, alert.fromDate, alert.toPrice.subtract(alert.fromPrice), secondsBetween(alert.fromDate, alert.toDate));
    }

    private static BigDecimal trendPriceAt(@NotNull ZonedDateTime dateTime, @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate, @NotNull BigDecimal priceDelta, @NotNull BigDecimal deltaSeconds) {
        return fromPrice.add(priceDelta(dateTime, fromDate, priceDelta, deltaSeconds)).max(BigDecimal.ZERO);
    }

    private static BigDecimal priceDelta(@NotNull ZonedDateTime dateTime, @NotNull ZonedDateTime fromDate, @NotNull BigDecimal priceDelta, @NotNull BigDecimal deltaSeconds) {
        BigDecimal delta = priceDelta.multiply(secondsBetween(fromDate, dateTime));
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
        embed.addField("from price", fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("from date", formatDiscord(fromDate) + '\n' + formatDiscordRelative(fromDate), true);
        embed.addField("created", formatDiscordRelative(creationDate), true);
        embed.addField("to price", toPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("to date", formatDiscord(toDate) + '\n' + formatDiscordRelative(toDate), true);
        embed.addField("current trend price", formatPrice(trendPriceAt(now, this), getTicker2()), true);
        return embed;
    }
}
