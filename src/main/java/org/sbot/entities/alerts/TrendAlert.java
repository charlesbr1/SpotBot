package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static org.sbot.entities.alerts.RangeAlert.priceCrossedRange;
import static org.sbot.entities.alerts.RangeAlert.priceInRange;
import static org.sbot.utils.Tickers.formatPrice;
import static org.sbot.utils.Tickers.getSymbol;
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
        checkArguments(fromDate, toDate);
    }

    public TrendAlert(@NotNull Map<FieldParser, Object> fields) {
        super(fields);
        checkArguments(fromDate, toDate);
    }

    private static void checkArguments(@NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime toDate) {
        if(fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from_date is after to_date");
        } else if(fromDate.isEqual(toDate)) {
            throw new IllegalArgumentException("from_date and to_date can not be the same");
        }
    }

    @Override
    @NotNull
    protected TrendAlert build(long id, long userId, long serverId,
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
                // precision is limited to the candlestick timeframe
                var lowPrice = trendPriceAt(candlestick.openTime(), fromPrice, fromDate, priceDelta, deltaSeconds);
                var swapPrice = trendPriceAt(candlestick.closeTime(), fromPrice, fromDate, priceDelta, deltaSeconds);
                var highPrice = lowPrice.compareTo(swapPrice) > 0 ? lowPrice : swapPrice;
                lowPrice = highPrice.equals(lowPrice) ? swapPrice : lowPrice;
                if (priceInRange(candlestick, lowPrice, highPrice, MARGIN_DISABLED) ||
                        priceCrossedRange(candlestick, lowPrice, highPrice, filterListenableCandleStick(previousCandlestick))) {
                    return new MatchingAlert(this, MATCHED, candlestick);
                } else if (priceInRange(candlestick, lowPrice, highPrice, margin)) {
                    return new MatchingAlert(this, MARGIN, candlestick);
                }
                previousCandlestick = candlestick;
            }
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    @NotNull
    public static BigDecimal trendPriceAt(@NotNull ZonedDateTime dateTime, @NotNull Alert alert) {
        return trendPriceAt(dateTime, alert.fromPrice, alert.fromDate,
                alert.toPrice.subtract(alert.fromPrice),
                secondsBetween(alert.fromDate, alert.toDate));
    }

    static BigDecimal trendPriceAt(@NotNull ZonedDateTime dateTime, @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate, @NotNull BigDecimal priceDelta, @NotNull BigDecimal deltaSeconds) {
        return fromPrice.add(priceDelta(dateTime, fromDate, priceDelta, deltaSeconds)).max(BigDecimal.ZERO);
    }

    static BigDecimal priceDelta(@NotNull ZonedDateTime dateTime, @NotNull ZonedDateTime fromDate, @NotNull BigDecimal priceDelta, @NotNull BigDecimal deltaSeconds) {
        BigDecimal delta = priceDelta.multiply(secondsBetween(fromDate, dateTime));
        return deltaSeconds.equals(BigDecimal.ZERO) ? delta : delta.divide(deltaSeconds, 16, HALF_UP);
    }

    @NotNull
    static BigDecimal secondsBetween(@NotNull ZonedDateTime fromDate, @NotNull ZonedDateTime date) {
        long durationSec = Duration.between(fromDate, date).toSeconds();
        return durationSec != 0 ? new BigDecimal(durationSec) : BigDecimal.ZERO;
    }

    @Override
    @NotNull
    public EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable DatedPrice previousClose, @NotNull ZonedDateTime now) {
        requireNonNull(now);
        String description = header(matchingStatus, previousClose, now) +
                "\n\n* id :\t" + id +
                footer(matchingStatus);
        return new EmbedBuilder().setDescription(description)
                .addField("from price", fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true)
                .addField("from date", formatDiscord(fromDate) + '\n' + formatDiscordRelative(fromDate), true)
                .addField("created", formatDiscordRelative(creationDate), true)
                .addField("to price", toPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true)
                .addField("to date", formatDiscord(toDate) + '\n' + formatDiscordRelative(toDate), true)
                .addField("current trend price", formatPrice(trendPriceAt(now, this), getTicker2()), true);
    }
}
