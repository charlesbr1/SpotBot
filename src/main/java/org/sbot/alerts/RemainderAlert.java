package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.MATCHED;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.utils.Dates.formatUTC;

public class RemainderAlert extends Alert {

    public static final String REMAINDER_VIRTUAL_EXCHANGE = "@r";

    public RemainderAlert(long userId, long serverId,
                          @NotNull String pair,
                          @NotNull String message, @NotNull ZonedDateTime fromDate) {
        this(0, userId, serverId, pair, message, fromDate, null, MARGIN_DISABLED, (short) 1);
    }

    public RemainderAlert(long id, long userId, long serverId,
                          @NotNull String pair, @NotNull String message,
                          @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime lastTrigger,
                          @NotNull BigDecimal margin, short repeat) {
        super(id, remainder, userId, serverId, REMAINDER_VIRTUAL_EXCHANGE, pair, message, null, null, fromDate, null, lastTrigger, margin, repeat, DEFAULT_REPEAT_DELAY_HOURS);
        requireNonNull(fromDate, "missing RemainderAlert fromDate");
    }

    @Override
    public RemainderAlert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                                BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate,
                                ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short repeatDelay) {
        if(MARGIN_DISABLED.compareTo(margin) != 0 || !REMAINDER_VIRTUAL_EXCHANGE.equals(exchange) ||
                null != toDate|| null != fromPrice|| null != toPrice || 1 != repeat || DEFAULT_REPEAT_DELAY_HOURS != repeatDelay) {
            throw new IllegalArgumentException("Can't update such value in a Remainder Alert");
        }
        return new RemainderAlert(id, userId, serverId, pair, message, fromDate, lastTrigger, MARGIN_DISABLED, (short) 1);
    }

    @NotNull
    @Override
    public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
        if(fromDate.isBefore(now.plusMinutes(60)) || fromDate.isAfter(now.minusMinutes(60))) {
            return new MatchingAlert(this, MATCHED, null);
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    @NotNull
    @Override
    protected String asMessage(@NotNull MatchingAlert.MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return (matchingStatus.notMatching() ? type.titleName + " set by <@" + userId + "> on " + pair :
                "<@" + userId + ">\n\n**" + message + "**") +
                "\n\n* id :\t" + id +
                "\n* date :\t" + formatUTC(fromDate);
    }
}
