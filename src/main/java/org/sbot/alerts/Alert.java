package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public abstract class Alert {

    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_REPEAT_DELAY_HOURS = 8;
    public static final short DEFAULT_THRESHOLD = 0;
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
    protected final short threshold;
    protected Candlestick lastCandlestick; // TODO move elsewhere


    protected Alert(long id, long userId, long serverId, @NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2, @NotNull String message, short repeat, short repeatDelay, short threshold) {
        this.id = id;
        this.userId = userId;
        this.serverId = serverId;
        this.exchange = exchange.toLowerCase().intern();
        this.ticker1 = ticker1.toUpperCase().intern();
        this.ticker2 = ticker2.toUpperCase().intern();
        this.message = requireNonNull(message);
        this.repeat = repeat;
        this.repeatDelay = repeatDelay;
        this.threshold = threshold;
    }

    public long getServerId() {
        return serverId;
    }

    public String getExchange() {
        return exchange;
    }

    public final String getPair() {
        return ticker1 + ticker2;
    }

    public final String getSlashPair() {
        return ticker1 + '/' + ticker2;
    }

    public final boolean isPrivate() {
        return PRIVATE_ALERT == serverId;
    }

    public abstract Alert withRepeat(short repeat);

    public abstract Alert withRepeatDelay(short delay);

    public abstract Alert withThreshold(short threshold);

    // SIDE EFFECT, this updates field lastCandlestick TODO doc
    public abstract boolean match(@NotNull Candlestick candlestick);

    @NotNull
    public abstract String notification();

    protected final boolean isNewerCandleStick(@NotNull Candlestick candlestick) {
        return null == lastCandlestick ||
                candlestick.openTime().isAfter(lastCandlestick.openTime()) ||
                candlestick.closeTime().isBefore(lastCandlestick.closeTime());
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
