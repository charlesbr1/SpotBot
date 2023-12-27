package org.sbot.exchanges;

import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;

import java.util.stream.Stream;

public interface Exchange {

    Stream<Candlestick> getCandlesticks(String pair, TimeFrame timeFrame, long limit);
}
