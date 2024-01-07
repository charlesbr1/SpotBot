package org.sbot.exchanges;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;

import java.util.stream.Stream;

public interface Exchange {

    @NotNull
    String name();

    @NotNull
    Stream<Candlestick> getCandlesticks(@NotNull String pair, @NotNull TimeFrame timeFrame, long limit);
}
