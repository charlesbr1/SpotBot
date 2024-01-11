package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.sbot.utils.ArgumentValidator.requirePairFormat;

public final class LastCandlesticksMemory implements LastCandlesticksDao {

    private static final Logger LOGGER = LogManager.getLogger(LastCandlesticksMemory.class);

    private static final Map<String, Candlestick> lastCandlesticks = new ConcurrentHashMap<>();

    {
        LOGGER.debug("Loading memory storage for last_candlesticks");
    }

    @Override
    public Optional<ZonedDateTime> getLastCandlestickCloseTime(@NotNull String pair) {
        LOGGER.debug("getLastCandlestickCloseTime {}", pair);
        return Optional.ofNullable(lastCandlesticks.get(pair)).map(Candlestick::closeTime);
    }

    @Override
    public Optional<Candlestick> getLastCandlestick(@NotNull String pair) {
        LOGGER.debug("getLastCandlestick {}", pair);
        return Optional.ofNullable(lastCandlesticks.get(pair));
    }

    @Override
    public void setLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("setLastCandlestick {} {}", pair, candlestick);
        lastCandlesticks.put(requirePairFormat(pair), candlestick);
    }

    @Override
    public void updateLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("updateLastCandlestick {} {}", pair, candlestick);
        lastCandlesticks.put(requirePairFormat(pair), candlestick);
    }
}
