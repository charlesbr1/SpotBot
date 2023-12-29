package org.sbot.chart;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

public record Candlestick(@NotNull ZonedDateTime openTime, @NotNull ZonedDateTime closeTime,
                          @NotNull BigDecimal open, @NotNull BigDecimal close,
                          @NotNull BigDecimal high, @NotNull BigDecimal low) {

    public Candlestick {
        requireNonNull(openTime);
        requireNonNull(closeTime);
        requireNonNull(open);
        requireNonNull(close);
        requireNonNull(high);
        requireNonNull(low);
    }
}
