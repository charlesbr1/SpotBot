package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public abstract class Alert {

    public final long id = IdGenerator.newId();

    public final String exchange;
    public final String ticker1;
    public final String ticker2;
    public final String message;
    public final String owner;

    protected Candlestick lastCandlestick;

    protected Alert(@NotNull String exchange, @NotNull String ticker1, @NotNull String ticker2, @NotNull String message, @NotNull String owner) {
        this.exchange = exchange.toLowerCase().intern();
        this.ticker1 = ticker1.toUpperCase().intern();
        this.ticker2 = ticker2.toUpperCase().intern();
        this.message = requireNonNull(message);
        this.owner = requireNonNull(owner);
    }

    public String getOwner() {
        return owner;
    }

    public String getExchange() {
        return exchange;
    }

    public final String getPair() {
        return ticker1 + ticker2;
    }

    public final String getReadablePair() {
        return ticker1 + '/' + ticker2;
    }

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
