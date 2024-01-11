package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class MarketDataService {

    private static final Logger LOGGER = LogManager.getLogger(MarketDataService.class);

    private final LastCandlesticksDao lastCandlesticksDao;

    public MarketDataService(LastCandlesticksDao lastCandlesticksDao) {
        this.lastCandlesticksDao = requireNonNull(lastCandlesticksDao);
    }

    public Optional<Candlestick> getLastCandlestick(@NotNull String pair) {
        var lastCandlestick = lastCandlesticksDao.transactional(() -> lastCandlesticksDao.getLastCandlestick(pair));
        lastCandlestick.ifPresentOrElse(candlestick -> LOGGER.debug("Found a last price for pair [{}] : {}", pair, lastCandlestick),
                () -> LOGGER.debug("No last price found for pair [{}]", pair));
        return lastCandlestick;
    }

    public void updateLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        requireNonNull(pair);
        requireNonNull(candlestick);
        lastCandlesticksDao.transactional(() -> {
            Candlestick lastCandlestick = lastCandlesticksDao.getLastCandlestick(pair).orElse(null);
            boolean isNew = null != lastCandlestick && lastCandlestick.closeTime().isBefore(candlestick.closeTime());
            if(null == lastCandlestick || isNew) {
                lastCandlesticksDao.setLastCandlestick(pair, candlestick);
            } else {
                LOGGER.warn("Unexpected outdated new candlestick received, pair {}, last candlestick : {}, outdated new candlestick : {}", pair, lastCandlestick, candlestick);
            }
        });
    }
}
