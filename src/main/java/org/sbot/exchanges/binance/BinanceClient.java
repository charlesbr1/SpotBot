package org.sbot.exchanges.binance;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.chart.Candlestick;
import org.sbot.exchanges.Exchange;
import org.sbot.chart.TimeFrame;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.sbot.exchanges.binance.BinanceMapper.map;

public class BinanceClient implements Exchange {

    private static final Logger LOGGER = LogManager.getLogger(BinanceClient.class);

    private final BinanceApiRestClient binanceApiClient;

    public BinanceClient(String apiKey, String apiSecret) {
        LOGGER.info("Loading binance connection...");
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(apiKey, apiSecret);
        binanceApiClient = factory.newRestClient();
        LOGGER.info("Binance connection loaded");
    }

    @Override
    public Stream<Candlestick> getCandlesticks(String pair, TimeFrame timeFrame, long limit) {
        //TODO use limit on api call, check open/close time order of responses
        LOGGER.debug("Requesting binance candlestick for pair {} and time frame {} and limit {}...", pair, timeFrame, limit);
        var candlesticks = binanceApiClient.getCandlestickBars(pair.replace("/", ""), map(timeFrame));
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Received binance candlestick for pair " + pair + " and time frame " + timeFrame + " and limit " + limit + ":\n" +
                    candlesticks.stream().map(com.binance.api.client.domain.market.Candlestick::toString).collect(Collectors.joining("\n")));
        }
        return candlesticks.stream().limit(limit).map(BinanceMapper::map);
    }
}
