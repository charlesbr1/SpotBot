package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.sbot.utils.ArgumentValidator.requirePairFormat;

public class LastCandlesticksMemory implements LastCandlesticksDao {

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
    public <T> T transactional(@NotNull Supplier<T> callback, @NotNull TransactionIsolationLevel isolationLevel) {
        return callback.get(); // no transaction support in memory
    }
}
