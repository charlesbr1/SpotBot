package org.sbot.chart;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

public record Candlestick(@NotNull ZonedDateTime openTime, @NotNull ZonedDateTime closeTime,
                          @NotNull BigDecimal open, @NotNull BigDecimal close,
                          @NotNull BigDecimal high, @NotNull BigDecimal low) {

    public Candlestick {
        requireNonNull(openTime, "missing Candlestick openTime");
        requireNonNull(closeTime, "missing Candlestick closeTime");
        if(openTime.compareTo(closeTime) > 0) {
            throw new IllegalArgumentException("openTime is after closeTime");
        }
        requireNonNull(open, "missing Candlestick open");
        requireNonNull(close, "missing Candlestick close");
        requireNonNull(high, "missing Candlestick high");
        requireNonNull(low, "missing Candlestick low");
        if(low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low is higher than high");
        }
    }
}
