package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.sbot.SpotBot.ALERTS_CHECK_PERIOD_MIN;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.MATCHED;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.utils.Dates.formatUTC;

public final class RemainderAlert extends Alert {

    public static final String REMAINDER_VIRTUAL_EXCHANGE = "@r";
    static final short REMAINDER_DEFAULT_REPEAT = 1;

    public RemainderAlert(long id, long userId, long serverId,
                          @NotNull String pair, @NotNull String message,
                          @NotNull ZonedDateTime fromDate) {
        super(id, remainder, userId, serverId, REMAINDER_VIRTUAL_EXCHANGE, pair, message, null, null,
                requireNonNull(fromDate, "missing RemainderAlert fromDate"),
                null, null, MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Override
    @NotNull
    public RemainderAlert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                                BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate,
                                ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short snooze) {
        if(!REMAINDER_VIRTUAL_EXCHANGE.equals(exchange) || MARGIN_DISABLED.compareTo(margin) != 0 ||
                null != toDate|| null != fromPrice|| null != toPrice || null != lastTrigger || REMAINDER_DEFAULT_REPEAT != repeat || DEFAULT_SNOOZE_HOURS != snooze) {
            throw new IllegalArgumentException("Can't update such value in a Remainder Alert");
        }
        return new RemainderAlert(id, userId, serverId, pair, message, fromDate);
    }

    @Override
    @NotNull
    public MatchingAlert match(@NotNull List<Candlestick> ignored, @Nullable Candlestick unused) {
        return match(Instant.now().atZone(ZoneOffset.UTC));
    }

    MatchingAlert match(@NotNull ZonedDateTime now) {
        if(fromDate.isBefore(now.plusMinutes((ALERTS_CHECK_PERIOD_MIN / 2) + 1L))) { // alert accuracy is +- ALERTS_CHECK_FREQUENCY_MIN / 2
            return new MatchingAlert(this, MATCHED, null);
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    @Override
    @NotNull
    protected String asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return (matchingStatus.notMatching() ? type.titleName + " set by <@" + userId + "> on " + pair :
                "<@" + userId + ">\n\n**" + message + "**") +
                "\n\n* id :\t" + id +
                "\n* date :\t" + formatUTC(fromDate) +
                (matchingStatus.notMatching() ? "\n* message :\t" + message : "");
    }
}
