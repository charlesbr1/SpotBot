package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;

public abstract class Alert {

    public static final int ALERT_MESSAGE_ARG_MAX_LENGTH = 210;
    public static final int ALERT_MIN_TICKER_LENGTH = 3;
    public static final int ALERT_MAX_TICKER_LENGTH = 5;

    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_REPEAT_DELAY_HOURS = 8;
    public static final short DEFAULT_MARGIN = 0;
    public static final long PRIVATE_ALERT = 0;

    public final long id;

    public final long userId;
    public final long serverId; // = PRIVATE_ALERT for private channel

    public final String exchange;
    public final String ticker1;
    public final String ticker2;
    public final String message;

    protected final short repeat;
    protected final short repeatDelay;
    protected final short margin;
    protected Candlestick lastCandlestick; // TODO move elsewhere


    protected Alert(long id, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2, @NotNull String message, short repeat, short repeatDelay, short margin) {
        this.id = id;
        this.userId = userId;
        this.serverId = serverId;
        this.exchange = exchange.toLowerCase().intern();
        this.ticker1 = requireTickerLength(ticker1).toUpperCase().intern();
        this.ticker2 = requireTickerLength(ticker2).toUpperCase().intern();
        this.message = requireMaxMessageArgLength(message);
        this.repeat = requirePositiveShort(repeat);
        this.repeatDelay = requirePositiveShort(repeatDelay);
        this.margin = requirePositiveShort(margin);
    }

    public final boolean isPrivate() {
        return PRIVATE_ALERT == serverId;
    }

    public final boolean isOver() {
        return 0 == repeat;
    }

    public final boolean hasMargin() {
        return 0 != margin;
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
    public abstract Alert withRepeat(short repeat);

    @NotNull
    public abstract Alert withRepeatDelay(short delay);

    @NotNull
    public abstract Alert withMargin(short margin);

    // SIDE EFFECT, this updates field lastCandlestick TODO doc
    public abstract boolean match(@NotNull Candlestick candlestick);
    public abstract boolean inMargin(@NotNull Candlestick candlestick);

    protected final boolean isNewerCandleStick(@NotNull Candlestick candlestick) {
        return null == lastCandlestick ||
                candlestick.openTime().isAfter(lastCandlestick.openTime()) ||
                candlestick.closeTime().isBefore(lastCandlestick.closeTime());
    }

    @NotNull
    public final String asTriggered() {
        return asMessage(true);
    }

    @NotNull
    public final String asDescription() {
        return asMessage(false);
    }

    @NotNull
    protected abstract String asMessage(boolean triggered);

    @NotNull
    protected final String header(boolean triggered) {
        String header = triggered ? "<@" + userId + ">\nYour " + name().toLowerCase() + " set" :
                name() + " Alert set by <@" + userId + '>';
        return header + " on " + exchange + " [" + getSlashPair() + ']' +
                (triggered ? " was **tested**,\ncheck out the price !!" : "") +
                (!triggered && isOver() ? "\n\n**DISABLED**\n" : "");
    }

    @NotNull
    protected final String footer() {
        return "\n* margin / repeat / delay :\t" +
                (hasMargin() ? margin : "disabled") + " / " + (isOver() ? "disabled" : repeat) + " / " + repeatDelay +
                Optional.ofNullable(lastCandlestick).map(Candlestick::close)
                        .map(BigDecimal::toPlainString)
                        .map(price -> "\n\nLast close : " + price + ' ' + getSymbol(ticker2) +
                                " at " + lastCandlestick.closeTime().format(DATE_TIME_FORMATTER))
                        .orElse("");
    }

    @NotNull
    protected static String getSymbol(@NotNull String ticker) {
        return ticker.contains("USD") ? "$" :
                (ticker.contains("EUR") ? "€" : ticker);
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
}
