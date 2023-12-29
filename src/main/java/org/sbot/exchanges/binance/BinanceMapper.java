package org.sbot.exchanges.binance;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.TimeFrame;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

public interface BinanceMapper {

    static org.sbot.chart.Candlestick map(@NotNull Candlestick candlestick) {
        return new org.sbot.chart.Candlestick(
                Instant.ofEpochMilli(candlestick.getOpenTime()).atZone(ZoneOffset.UTC),
                Instant.ofEpochMilli(candlestick.getCloseTime()).atZone(ZoneOffset.UTC),
                new BigDecimal(candlestick.getOpen()),
                new BigDecimal(candlestick.getClose()),
                new BigDecimal(candlestick.getHigh()),
                new BigDecimal(candlestick.getLow()));
    }

    static CandlestickInterval map(@NotNull TimeFrame timeFrame) {
        return switch (timeFrame) {
            case ONE_MINUTE -> CandlestickInterval.ONE_MINUTE;
            case THREE_MINUTES -> CandlestickInterval.THREE_MINUTES;
            case FIVE_MINUTES -> CandlestickInterval.FIVE_MINUTES;
            case FIFTEEN_MINUTES -> CandlestickInterval.FIFTEEN_MINUTES;
            case HALF_HOURLY -> CandlestickInterval.HALF_HOURLY;
            case HOURLY -> CandlestickInterval.HOURLY;
            case TWO_HOURLY -> CandlestickInterval.TWO_HOURLY;
            case FOUR_HOURLY -> CandlestickInterval.FOUR_HORLY;
            case SIX_HOURLY -> CandlestickInterval.SIX_HOURLY;
            case EIGHT_HOURLY -> CandlestickInterval.EIGHT_HOURLY;
            case TWELVE_HOURLY -> CandlestickInterval.TWELVE_HOURLY;
            case DAILY -> CandlestickInterval.DAILY;
            case THREE_DAILY -> CandlestickInterval.THREE_DAILY;
            case WEEKLY -> CandlestickInterval.WEEKLY;
            case MONTHLY -> CandlestickInterval.MONTHLY;
        };
    }
}
