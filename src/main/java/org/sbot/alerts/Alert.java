package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.MatchingAlert.MatchingStatus;
import org.sbot.chart.Candlestick;
import org.sbot.chart.Ticker;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.chart.Ticker.getSymbol;
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

    public static final BigDecimal MARGIN_DISABLED = BigDecimal.ZERO;
    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_SNOOZE_HOURS = 8;
    public static final long PRIVATE_ALERT = 0L;
    public static final long NULL_ALERT_ID = 0L;

    public static final BigDecimal ONE_HOUR_SECONDS = new BigDecimal(Duration.ofHours(1L).toSeconds());


    public final long id;

    public final Type type;

//    @ColumnName("user_id")
    public final long userId;
//    @ColumnName("server_id")
    public final long serverId; // = PRIVATE_ALERT for private channel

    public final String exchange;
    public final String pair;
    public final String message;

    public final BigDecimal fromPrice;
    public final BigDecimal toPrice;
    public final ZonedDateTime fromDate;
    public final ZonedDateTime toDate;

    @Nullable // updated by dao when saving updates
//    @ColumnName("last_trigger")
    public final ZonedDateTime lastTrigger;

    public final BigDecimal margin;
    public final short repeat;
    public final short snooze;


    protected Alert(long id, @NotNull Type type, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                    @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice, @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                    @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        this.id = id;
        this.type = requireNonNull(type, "missing Alert type");
        this.userId = userId;
        this.serverId = serverId;
        this.exchange = requireSupportedExchange(exchange.toLowerCase()).intern();
        this.pair = requirePairFormat(pair.toUpperCase()).intern();
        this.message = requireAlertMessageMaxLength(message);
        this.fromPrice = fromPrice;
        this.toPrice = toPrice;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.lastTrigger = null != lastTrigger ? requireInPast(lastTrigger) : null;
        this.margin = requirePositive(margin);
        this.repeat = requirePositiveShort(repeat);
        this.snooze = requirePositiveShort(snooze);
    }

    public static boolean isPrivate(long serverId) {
        return PRIVATE_ALERT == serverId;
    }

    public static boolean hasRepeat(long repeat) {
        return repeat > 0;
    }

    public static boolean hasMargin(@NotNull BigDecimal margin) {
        return MARGIN_DISABLED.compareTo(margin) < 0;
    }

    public final boolean isSnoozeOver(long epochSeconds) {
        return null == lastTrigger || (lastTrigger.toEpochSecond() + (ONE_HOUR_SECONDS.longValue() * snooze)) <= epochSeconds;
    }


    public final long getId() {
        return id;
    }

    public final long getUserId() {
        return userId;
    }

    @NotNull
    public final String getExchange() {
        return exchange;
    }

    @NotNull
    public final String getPair() {
        return pair;
    }

    @NotNull
    public final String getTicker2() {
        return pair.substring(pair.indexOf('/') + 1);
    }

    @NotNull
    public final String getMessage() {
        return message;
    }

    @NotNull
    protected abstract Alert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message,
                               BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate,
                               ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short snooze);

    @NotNull
    public final Alert withId(@NotNull Supplier<Long> idGenerator) {
        if(NULL_ALERT_ID != this.id) {
            throw new IllegalStateException("Can't update the id of an already stored alert");
        }
        return build(idGenerator.get(), userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withServerId(long serverId) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withFromPrice(@NotNull BigDecimal fromPrice) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withToPrice(@NotNull BigDecimal toPrice) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withFromDate(@Nullable ZonedDateTime fromDate) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withToDate(@Nullable ZonedDateTime toDate) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withMessage(@NotNull String message) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withMargin(@NotNull BigDecimal margin) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withLastTriggerRepeatSnooze(@Nullable ZonedDateTime lastTrigger, short repeat, short snooze) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withLastTriggerMarginRepeat(@Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        return build(id, userId, serverId, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public abstract MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick);

    protected static boolean isNewerCandleStick(@NotNull Candlestick candlestick, @Nullable Candlestick previousCandlestick) {
        return null == previousCandlestick ||
                candlestick.closeTime().isAfter(previousCandlestick.closeTime());
    }

    @NotNull
    public final String onRaiseMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
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
        return header + " on " + exchange + ' ' + pair +
                (matchingStatus.notMatching() ? "" : (matchingStatus.isMargin() ? " reached **margin** threshold. Set a new one using :\n\n" +
                        SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "*margin " + id + " 'amount in " + getSymbol(getTicker2()) + "'*" :
                        " was **tested !**") +  "\n\n:rocket: Check out the price !!") +
                (matchingStatus.notMatching() && !hasRepeat(repeat) ? "\n\n**DISABLED**\n" : "");
    }

    @NotNull
    protected final String footer(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        int nextRepeat = matchingStatus.notMatching() ? repeat : Math.max(0, repeat - 1);
        return "\n* margin / repeat / delay :\t" +
                (matchingStatus.notMatching() && hasMargin(margin) ? margin.toPlainString() + ' ' + getSymbol(getTicker2()) : "disabled") +
                " / " + (!hasRepeat(nextRepeat) ? "disabled" : nextRepeat) +
                " / " + snooze + (snooze > 1 ? " hours" : " hour") +
                (matchingStatus.notMatching() ? Optional.ofNullable(lastTrigger).map(Dates::formatUTC).map("\n\nLast time raised : "::concat).orElse("") :
                        Optional.ofNullable(previousCandlestick).map(Candlestick::close)
                                .map(price -> Ticker.formatPrice(price, getTicker2()))
                                .map(price -> "\n\nLast close : " + price + " at " + formatUTC(previousCandlestick.closeTime()))
                                .orElse(""));
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
