package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;

import static java.util.Objects.requireNonNull;

public class MarketDataService {

    private static final Logger LOGGER = LogManager.getLogger(MarketDataService.class);

    private final LastCandlesticksDao lastCandlesticksDao;

    public MarketDataService(LastCandlesticksDao lastCandlesticksDao) {
        this.lastCandlesticksDao = requireNonNull(lastCandlesticksDao);
    }

    @Nullable
    public Candlestick getAndUpdateLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        requireNonNull(pair);
        requireNonNull(candlestick);
        return lastCandlesticksDao.transactional(() -> {
            Candlestick lastCandlestick = lastCandlesticksDao.getLastCandlestick(pair).orElse(null);
            boolean isNew = null != lastCandlestick && lastCandlestick.closeTime().isBefore(candlestick.closeTime());
            if(null == lastCandlestick || isNew) {
                lastCandlesticksDao.setLastCandlestick(pair, candlestick);
            } else {
                LOGGER.warn("Unexpected outdated new candlestick received, pair {}, last candlestick : {}, outdated new candlestick : {}", pair, lastCandlestick, candlestick);
            }
            return null;
        });
    }
}
