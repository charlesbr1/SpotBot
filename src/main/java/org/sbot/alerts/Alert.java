package org.sbot.alerts;

import org.sbot.chart.Candlestick;
import org.sbot.storage.IdGenerator;

import java.util.Objects;

public abstract class Alert {

    public final long id = IdGenerator.newId();

    public final String exchange;
    public final String ticker1;
    public final String ticker2;
    public final String message;
    public final String owner;

    protected Candlestick lastCandlestick;

    protected Alert(String exchange, String ticker1, String ticker2, String message, String owner) {
        this.exchange = exchange.toLowerCase().intern();
        this.ticker1 = ticker1.toUpperCase().intern();
        this.ticker2 = ticker2.toUpperCase().intern();
        this.message = message;
        this.owner = owner;
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
    public abstract boolean match(Candlestick candlestick);

    public abstract String notification();

    protected final boolean isNewerCandleStick(Candlestick candlestick) {
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
