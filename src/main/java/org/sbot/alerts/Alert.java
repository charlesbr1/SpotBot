package org.sbot.alerts;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.alerts.RemainderAlert.REMAINDER_EXCHANGE;
import static org.sbot.chart.Symbol.getSymbol;
import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public abstract class Alert {

    public enum Type {
        range("Range"),
        trend("Trend"),
        remainder("Remainder");

        public final String titleName;

        Type(String titleName) {
            this.titleName = requireNonNull(titleName);
        }
    }

    public static final int ALERT_MESSAGE_ARG_MAX_LENGTH = 210;
    public static final int ALERT_MIN_TICKER_LENGTH = 3;
    public static final int ALERT_MAX_TICKER_LENGTH = 5;

    public static final BigDecimal MARGIN_DISABLED = BigDecimal.ZERO;
    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_REPEAT_DELAY_HOURS = 8;
    public static final long PRIVATE_ALERT = 0;

    public final long id;

    public final Type type;

    @ColumnName("user_id")
    public final long userId;
    @ColumnName("server_id")
    public final long serverId; // = PRIVATE_ALERT for private channel

    public final String exchange;
    public final String ticker1;
    public final String ticker2;
    public final String message;

    public final BigDecimal fromPrice;
    public final BigDecimal toPrice;
    public final ZonedDateTime fromDate;
    public final ZonedDateTime toDate;

    @Nullable // updated by dao when saving updates
    @ColumnName("last_trigger")
    public final ZonedDateTime lastTrigger;

    public final BigDecimal margin;
    public final short repeat;
    @ColumnName("repeat_delay")
    public final short repeatDelay;


    protected Alert(long id, @NotNull Type type, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2, @NotNull String message,
                    @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice, @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                    @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short repeatDelay) {
        this.id = id;
        this.type = requireNonNull(type, "missing Alert type");
        this.userId = userId;
        this.serverId = serverId;
        this.exchange = requireSupportedExchange(exchange.toLowerCase()).intern();
        this.ticker1 = requireTickerLength(ticker1).toUpperCase().intern();
        this.ticker2 = requireTickerLength(ticker2).toUpperCase().intern();
        this.message = requireAlertMessageLength(message);
        this.fromPrice = Optional.ofNullable(fromPrice).map(BigDecimal::stripTrailingZeros).orElse(null);
        this.toPrice = Optional.ofNullable(toPrice).map(BigDecimal::stripTrailingZeros).orElse(null);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.lastTrigger = null != lastTrigger ? requireInPast(lastTrigger) : null;
        this.margin = requirePositive(margin).stripTrailingZeros();
        this.repeat = requirePositiveShort(repeat);
        this.repeatDelay = requirePositiveShort(repeatDelay);
    }

    public static boolean isPrivate(long serverId) {
        return PRIVATE_ALERT == serverId;
    }

    public static boolean hasRepeat(long repeat) {
        return repeat > 0;
    }

    public boolean isRepeatDelayOver(long epochSeconds) {
        return null == lastTrigger || (lastTrigger.toEpochSecond() + (3600L * repeatDelay)) <= epochSeconds;
    }

    public static boolean hasMargin(@NotNull BigDecimal margin) {
        return MARGIN_DISABLED.compareTo(margin) < 0;
    }


    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    @NotNull
    public String getExchange() {
        return exchange;
    }

    @NotNull
    public final String getSlashPair() {
        return ticker1 + '/' + ticker2;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public abstract Alert withId(@NotNull Supplier<Long> idGenerator);
    @NotNull
    public abstract Alert withMessage(@NotNull String message);

    @NotNull
    public abstract Alert withMargin(@NotNull BigDecimal margin);

    @NotNull
    public abstract Alert withRepeat(short repeat);

    @NotNull
    public abstract Alert withRepeatDelay(short delay);

    @NotNull
    public abstract Alert withLastTriggerMarginRepeat(@NotNull ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat);

    @NotNull
    public abstract MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick);

    protected static boolean isNewerCandleStick(@NotNull Candlestick candlestick, @Nullable Candlestick previousCandlestick) {
        return null == previousCandlestick ||
                candlestick.closeTime().isBefore(previousCandlestick.closeTime());
    }

    @NotNull
    public final String triggeredMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return asMessage(matchingStatus, previousCandlestick);
    }

    @NotNull
    public final String descriptionMessage() {
        return asMessage(NOT_MATCHING, null);
    }

    @NotNull
    protected abstract String asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick);

    @NotNull
    protected final String header(@NotNull MatchingStatus matchingStatus) {
        String header = matchingStatus.notMatching() ? type.titleName + " Alert set by <@" + userId + '>' :
                "<@" + userId + ">\nYour " + type.name() + " set";
        return header + " on " + exchange + ' ' + getSlashPair() +
                (matchingStatus.notMatching() ? "" : (matchingStatus.isMargin() ? " reached **margin** threshold. Set a new one using :\n\n" +
                        SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "*!margin " + id + " 'amount in " + getSymbol(ticker2) + "'*" :
                        " was **tested !**") +  "\n\n:rocket: Check out the price !!") +
                (matchingStatus.notMatching() && !hasRepeat(repeat) ? "\n\n**DISABLED**\n" : "");
    }

    @NotNull
    protected final String footer(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        int nextRepeat = matchingStatus.notMatching() ? repeat : Math.max(0, repeat - 1);
        return "\n* margin / repeat / delay :\t" +
                (matchingStatus.notMatching() && hasMargin(margin) ? margin.toPlainString() + ' ' + getSymbol(ticker2) : "disabled") +
                " / " + (!hasRepeat(nextRepeat) ? "disabled" : nextRepeat) +
                " / " + repeatDelay + (repeatDelay > 1 ? " hours" : " hour") +
                Optional.ofNullable(previousCandlestick).map(Candlestick::close)
                        .map(BigDecimal::stripTrailingZeros)
                        .map(BigDecimal::toPlainString)
                        .map(price -> "\n\nLast close : " + price + ' ' + getSymbol(ticker2) +
                                " at " + formatUTC(previousCandlestick.closeTime()))
                        .orElse("") +
                (matchingStatus.notMatching() ? Optional.ofNullable(lastTrigger).map(Dates::formatUTC).map("\n\nLast time triggered : "::concat).orElse("") : "");
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alert alert)) return false;
        return id == alert.id;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    @NotNull
    public String toString() {
        return descriptionMessage();
    }
}
