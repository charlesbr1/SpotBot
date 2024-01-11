package org.sbot.exchanges;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;

import java.util.List;
import java.util.stream.Stream;

import static org.sbot.exchanges.Exchanges.VIRTUAL_EXCHANGES;

public interface Exchange {

    @NotNull
    String name();

    @NotNull
    List<Candlestick> getCandlesticks(@NotNull String pair, @NotNull TimeFrame timeFrame, long limit);

    default boolean isVirtual() {
        return VIRTUAL_EXCHANGES.contains(name());
    }
}
