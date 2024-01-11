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

    public static final String REMAINDER_EXCHANGE = "@r";

    public RemainderAlert(long userId, long serverId,
                          @NotNull String ticker1, @NotNull String ticker2,
                          @NotNull String message, @NotNull ZonedDateTime fromDate) {
        this(0, userId, serverId, ticker1, ticker2, message, fromDate, null, MARGIN_DISABLED, (short) 1);
    }

    public RemainderAlert(long id, long userId, long serverId,
                          @NotNull String ticker1, @NotNull String ticker2, @NotNull String message,
                          @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime lastTrigger,
                          @NotNull BigDecimal margin, short repeat) {
        super(id, remainder, userId, serverId, REMAINDER_EXCHANGE, ticker1, ticker2, message, null, null, fromDate, null, lastTrigger, margin, repeat, DEFAULT_REPEAT_DELAY_HOURS);
        requireNonNull(fromDate, "missing RemainderAlert fromDate");
    }

    @NotNull
    @Override
    public Alert withId(@NotNull Supplier<Long> idGenerator) {
        if(0 != this.id) {
            throw new IllegalArgumentException("Can't update the id of an already stored alert");
        }
        return new RemainderAlert(idGenerator.get(), userId, serverId, ticker1, ticker2, message, fromDate, lastTrigger, margin, repeat);
    }

    @NotNull
    @Override
    public Alert withMessage(@NotNull String message) {
        return new RemainderAlert(id, userId, serverId, ticker1, ticker2, message, fromDate, lastTrigger, margin, repeat);
    }

    @NotNull
    @Override
    public Alert withMargin(@NotNull BigDecimal margin) {
        throw new UnsupportedOperationException("You can't set the margin of a remainder alert");
    }

    @NotNull
    @Override
    public Alert withRepeat(short repeat) {
        throw new UnsupportedOperationException("You can't set the repeat of a remainder alert");
    }

    @NotNull
    @Override
    public Alert withRepeatDelay(short delay) {
        throw new UnsupportedOperationException("You can't set the repeatDelay of a remainder alert");
    }

    @NotNull
    @Override
    public Alert withLastTriggerMarginRepeat(@NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        throw new UnsupportedOperationException("You can't update a remainder alert");
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
        return (matchingStatus.notMatching() ? type.titleName + " set by <@" + userId + "> on " + getSlashPair() :
                "<@" + userId + ">\n\n**" + message + "**") +
                "\n\n* id :\t" + id +
                "\n* date :\t" + formatUTC(fromDate);
    }
}
