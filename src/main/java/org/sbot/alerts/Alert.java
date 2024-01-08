package org.sbot.alerts;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
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

import static org.sbot.alerts.MatchingAlert.MatchingStatus.NO_TRIGGER;
import static org.sbot.chart.Symbol.getSymbol;
import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public abstract class Alert {

    @JdbiConstructor
    public static Alert of(@ColumnName("user_id") long userId, @ColumnName("server_id") long serverId,
                           @ColumnName("exchange") @NotNull String exchange,
                           @ColumnName("ticker1") @NotNull String ticker1, @ColumnName("ticker1") @NotNull String ticker2,
                           @ColumnName("message") @NotNull String message, @ColumnName("last_trigger") @Nullable ZonedDateTime lastTrigger,
                           @ColumnName("margin") @NotNull BigDecimal margin,
                           @ColumnName("repeat") short repeat, @ColumnName("repeatDelay") short repeatDelay) {
        throw new UnsupportedOperationException("TODO ooooooooooooo");
    }
    public static final int ALERT_MESSAGE_ARG_MAX_LENGTH = 210;
    public static final int ALERT_MIN_TICKER_LENGTH = 3;
    public static final int ALERT_MAX_TICKER_LENGTH = 5;

    public static final BigDecimal MARGIN_DISABLED = BigDecimal.ZERO;
    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_REPEAT_DELAY_HOURS = 8;
    public static final long PRIVATE_ALERT = 0;

    public final long id;

    public final long userId;
    public final long serverId; // = PRIVATE_ALERT for private channel

    public final String exchange;
    public final String ticker1;
    public final String ticker2;
    public final String message;

    @Nullable // updated in match(..) function
    public final ZonedDateTime lastTrigger;

    public final BigDecimal margin;
    public final short repeat;
    public final short repeatDelay;


    protected Alert(long id, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2, @NotNull String message, @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short repeatDelay) {
        this.id = id;
        this.userId = userId;
        this.serverId = serverId;
        this.exchange = exchange.toLowerCase().intern();
        this.ticker1 = requireTickerLength(ticker1).toUpperCase().intern();
        this.ticker2 = requireTickerLength(ticker2).toUpperCase().intern();
        this.message = requireAlertMessageLength(message);
        this.lastTrigger = null != lastTrigger ? requireInPast(lastTrigger) : null;
        this.margin = requirePositive(margin).stripTrailingZeros();
        this.repeat = requirePositiveShort(repeat);
        this.repeatDelay = requirePositiveShort(repeatDelay);
    }

    public static boolean isPrivate(long serverId) {
        return PRIVATE_ALERT == serverId;
    }

    public static boolean isDisabled(long repeat) {
        return 0 >= repeat;
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

    public String getExchange() {
        return exchange;
    }

    public final String getSlashPair() {
        return ticker1 + '/' + ticker2;
    }


    @NotNull
    public abstract String name();

    @NotNull
    public abstract Alert withId(long id);
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
        return asMessage(NO_TRIGGER, null);
    }

    @NotNull
    protected abstract String asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick);

    @NotNull
    protected final String header(@NotNull MatchingStatus matchingStatus) {
        String header = matchingStatus.noTrigger() ? name() + " Alert set by <@" + userId + '>' :
                "<@" + userId + ">\nYour " + name().toLowerCase() + " set";
        return header + " on " + exchange + ' ' + getSlashPair() +
                (matchingStatus.noTrigger() ? "" : (matchingStatus.isMargin() ? " reached **margin** threshold. Set a new one using :\n\n" +
                        SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "*!margin " + id + " 'amount in " + getSymbol(ticker2) + "'*" :
                        " was **tested !**") +  "\n\n:rocket: Check out the price !!") +
                (matchingStatus.noTrigger() && isDisabled() ? "\n\n**DISABLED**\n" : "");
    }

    @NotNull
    protected final String footer(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick) {
        return "\n* margin / repeat / delay :\t" +
                (hasMargin(margin) ? margin.toPlainString() + ' ' + getSymbol(ticker2) : "disabled") + " / " + (isDisabled() ? "disabled" : repeat) + " / " + repeatDelay +
                Optional.ofNullable(previousCandlestick).map(Candlestick::close)
                        .map(BigDecimal::stripTrailingZeros)
                        .map(BigDecimal::toPlainString)
                        .map(price -> "\n\nLast close : " + price + ' ' + getSymbol(ticker2) +
                                " at " + formatUTC(previousCandlestick.closeTime()))
                        .orElse("") +
                (matchingStatus.noTrigger() ? Optional.ofNullable(lastTrigger).map(Dates::formatUTC).map("\n\nLast time triggered : "::concat) : "");
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
