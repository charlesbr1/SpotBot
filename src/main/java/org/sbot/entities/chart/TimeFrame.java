package org.sbot.entities.chart;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public enum TimeFrame {
    ONE_SECOND("1s"),
    ONE_MINUTE("1m"),
    THREE_MINUTES("3m"),
    FIVE_MINUTES("5m"),
    FIFTEEN_MINUTES("15m"),
    HALF_HOURLY("30m"),
    HOURLY("1h"),
    TWO_HOURLY("2h"),
    FOUR_HOURLY("4h"),
    SIX_HOURLY("6h"),
    EIGHT_HOURLY("8h"),
    TWELVE_HOURLY("12h"),
    DAILY("1d"),
    THREE_DAILY("3d"),
    WEEKLY("1w"),
    MONTHLY("1M");

    public final String symbol;

    TimeFrame(@NotNull String symbol) {
        this.symbol = requireNonNull(symbol);
    }
}
