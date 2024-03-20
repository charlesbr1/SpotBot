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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.Tickers.getSymbol;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.Dates.formatDiscord;
import static org.sbot.utils.Dates.formatDiscordRelative;

public final class RangeAlert extends Alert {

    public RangeAlert(long id, long userId, long serverId,
                      @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                      @NotNull String exchange, @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        super(id, Type.range, userId, serverId, creationDate, listeningDate, exchange, pair, message, requireNonNull(fromPrice), requireNonNull(toPrice),
                fromDate, toDate, lastTrigger, margin, repeat, snooze);
        checkArguments(fromPrice, toPrice, fromDate, toDate);
    }

    public RangeAlert(@NotNull Map<FieldParser, Object> fields) {
        super(fields);
        checkArguments(fromPrice, toPrice, fromDate, toDate);
    }

    private static void checkArguments(@NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate) {
        if(fromPrice.compareTo(toPrice) > 0) {
            throw new IllegalArgumentException("from_price is higher than to_price");
        }
        if(null != fromDate && null != toDate) {
            if(fromDate.isAfter(toDate)) {
                throw new IllegalArgumentException("from_date is after to_date");
            } else if(fromDate.isEqual(toDate)) {
                throw new IllegalArgumentException("from_date and to_date can not be the same");
            }
        }
    }

    @Override
    @NotNull
    protected RangeAlert build(long id, long userId, long serverId,
                            @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                            @NotNull String exchange, @NotNull String pair, @NotNull String message,
                            @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                            @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                            @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        return new RangeAlert(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        for (Candlestick candlestick : candlesticks) {
            if (isListenableCandleStick(candlestick) && isNewerCandleStick(candlestick, previousCandlestick)) {
                if (datesInLimits(candlestick, fromDate, toDate)) {
                    if (priceInRange(candlestick, fromPrice, toPrice, MARGIN_DISABLED) ||
                            priceCrossedRange(candlestick, fromPrice, toPrice, filterListenableCandleStick(previousCandlestick))) {
                        return new MatchingAlert(this, MATCHED, candlestick);
                    } else if (priceInRange(candlestick, fromPrice, toPrice, margin)) {
                        return new MatchingAlert(this, MARGIN, candlestick);
                    }
                }
                previousCandlestick = candlestick;
            }
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    static boolean datesInLimits(@NotNull Candlestick candlestick, @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate) {
        return (null == fromDate || !fromDate.isAfter(candlestick.closeTime())) &&
                (null == toDate || toDate.isAfter(candlestick.closeTime()));
    }

    static boolean priceInRange(@NotNull Candlestick candlestick, @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull BigDecimal margin) {
        return candlestick.low().compareTo(high.add(margin)) <= 0 &&
                candlestick.high().compareTo(low.subtract(margin)) >= 0;
    }

    static boolean priceCrossedRange(@NotNull Candlestick candlestick, @NotNull BigDecimal low, @NotNull BigDecimal high, @Nullable Candlestick previousCandlestick) {
        return null != previousCandlestick &&
                (previousCandlestick.low().compareTo(high) < 0 || candlestick.low().compareTo(high) <= 0) &&
                (previousCandlestick.high().compareTo(low) > 0 || candlestick.high().compareTo(low) >= 0);
    }

    @Override
    @NotNull
     public EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable DatedPrice previousClose, @NotNull ZonedDateTime now) {
        String description = header(matchingStatus, previousClose, now) +
                "\n\n* id :\t" + id +
                footer(matchingStatus);
        var embed = new EmbedBuilder().setDescription(description);
        embed.addField("low", fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("high", toPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("created", formatDiscordRelative(creationDate), true);
        if(null != fromDate || null != toDate) {
            if(null == fromDate) {
                embed.addField("to date", formatDiscord(toDate) + '\n' + formatDiscordRelative(toDate), false);
            } else if(null == toDate) {
                embed.addField("from date", formatDiscord(fromDate) + '\n' + formatDiscordRelative(fromDate), false);
            } else {
                embed.addField("from date", formatDiscord(fromDate) + '\n' + formatDiscordRelative(fromDate), true);
                embed.addField("to date", formatDiscord(toDate) + '\n' + formatDiscordRelative(toDate), true);
                embed.addBlankField(true);
            }
        }
        return embed;
    }
}
