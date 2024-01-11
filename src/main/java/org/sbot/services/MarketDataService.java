package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class MarketDataService {

    private static final Logger LOGGER = LogManager.getLogger(MarketDataService.class);

    private final LastCandlesticksDao lastCandlesticksDao;

    public MarketDataService(LastCandlesticksDao lastCandlesticksDao) {
        this.lastCandlesticksDao = requireNonNull(lastCandlesticksDao);
    }

    public Optional<ZonedDateTime> getLastCandlestickCloseTime(@NotNull String pair) {
        return lastCandlesticksDao.transactional(() -> lastCandlesticksDao.getLastCandlestickCloseTime(pair));
    }

    public Optional<Candlestick> getLastCandlestick(@NotNull String pair) {
        var lastCandlestick = lastCandlesticksDao.transactional(() -> lastCandlesticksDao.getLastCandlestick(pair));
        lastCandlestick.ifPresentOrElse(candlestick -> LOGGER.debug("Found a last price for pair [{}] : {}", pair, lastCandlestick),
                () -> LOGGER.debug("No last price found for pair [{}]", pair));
        return lastCandlestick;
    }

    public void updateLastCandlestick(@NotNull String pair, @Nullable Candlestick lastCandlestick, @NotNull Candlestick newCandlestick) {
        requireNonNull(pair);
        requireNonNull(newCandlestick);
        lastCandlesticksDao.transactional(() -> {
            if(null == lastCandlestick) {
                lastCandlesticksDao.setLastCandlestick(pair, newCandlestick);
            } else if(lastCandlestick.closeTime().isBefore(newCandlestick.closeTime())) {
                lastCandlesticksDao.updateLastCandlestick(pair, newCandlestick);
            } else {
                LOGGER.warn("Unexpected outdated new candlestick received, pair {}, last candlestick : {}, outdated new candlestick : {}", pair, lastCandlestick, newCandlestick);
            }
        });
    }
}
