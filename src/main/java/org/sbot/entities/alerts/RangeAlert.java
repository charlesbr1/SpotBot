package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.sbot.entities.chart.Ticker.getSymbol;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatDiscord;
import static org.sbot.utils.Dates.formatDiscordRelative;

public final class RangeAlert extends Alert {

    public RangeAlert(long id, long userId, long serverId,
                      @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                      @NotNull String exchange, @NotNull String pair, @NotNull String message,
                      @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                      @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                      @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        super(id, Type.range, userId, serverId, creationDate, listeningDate, exchange, pair, message, requirePositive(fromPrice), requirePositive(toPrice),
                fromDate, toDate, lastTrigger, margin, repeat, snooze);
        if(fromPrice.compareTo(toPrice) > 0) {
            throw new IllegalArgumentException("from_price is higher than to_price");
        }
        if(null != fromDate && null != toDate) {
            if(fromDate.isAfter(toDate)) {
                throw new IllegalArgumentException("from_date is after to_date");
            } else if(fromDate.compareTo(toDate) == 0) {
                throw new IllegalArgumentException("from_date and to_date can not be the same");
            }
        }
    }

    @Override
    @NotNull
    public RangeAlert build(long id, long userId, long serverId,
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
        return (null == fromDate || fromDate.compareTo(candlestick.closeTime()) <= 0) &&
                (null == toDate || toDate.compareTo(candlestick.closeTime()) > 0);
    }

    static boolean priceInRange(@NotNull Candlestick candlestick, @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @NotNull BigDecimal margin) {
        return candlestick.low().compareTo(toPrice.add(margin)) <= 0 &&
                candlestick.high().compareTo(fromPrice.subtract(margin)) >= 0;
    }

    static boolean priceCrossedRange(@NotNull Candlestick candlestick, @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice, @Nullable Candlestick previousCandlestick) {
        return null != previousCandlestick &&
                (previousCandlestick.low().compareTo(toPrice) < 0 || candlestick.low().compareTo(toPrice) <= 0) &&
                (previousCandlestick.high().compareTo(fromPrice) > 0 || candlestick.high().compareTo(fromPrice) >= 0);
    }

    @Override
    @NotNull
     protected EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick, @NotNull ZonedDateTime now) {
        String description = header(matchingStatus, previousCandlestick, now) +
                "\n\n* id :\t" + id +
                footer(matchingStatus);
        var embed = new EmbedBuilder().setDescription(description);
        embed.addField("low", fromPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("high", toPrice.toPlainString() + ' ' + getSymbol(getTicker2()), true);
        embed.addField("created", formatDiscordRelative(creationDate), true);
        if(null != fromDate || null != toDate) {
            Optional.ofNullable(fromDate).ifPresentOrElse(date -> embed.addField("from date", formatDiscord(date) + '\n' + formatDiscordRelative(date), true),
                    () -> embed.addBlankField(true));
            Optional.ofNullable(toDate).ifPresentOrElse(date -> embed.addField("to date", formatDiscord(date) + '\n' + formatDiscordRelative(date), true),
                    () -> embed.addBlankField(true));
            embed.addBlankField(true);
        }
        return embed;
    }
}
