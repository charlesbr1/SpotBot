package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.BatchEntry;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.services.dao.memory.LastCandlesticksMemory.LastCandlestickId.id;
import static org.sbot.utils.ArgumentValidator.requirePairFormat;
import static org.sbot.utils.ArgumentValidator.requireSupportedExchange;

public final class LastCandlesticksMemory implements LastCandlesticksDao {

    private static final Logger LOGGER = LogManager.getLogger(LastCandlesticksMemory.class);

    record LastCandlestickId(@NotNull String exchange, @NotNull String pair) {
        static LastCandlestickId id(@NotNull String exchange, @NotNull String pair) {
            return new LastCandlestickId(requireSupportedExchange(exchange), requirePairFormat(pair));
        }
    }

    private final Map<LastCandlestickId, Candlestick> lastCandlesticks = new ConcurrentHashMap<>();

    public LastCandlesticksMemory() {
        LOGGER.debug("Loading memory storage for last_candlesticks");
    }

    @Override
    @NotNull
    public Map<String, Set<String>> getPairsByExchanges() {
        LOGGER.debug("getPairsByExchanges");
        return lastCandlesticks.keySet().stream().collect(groupingBy(LastCandlestickId::exchange, mapping(LastCandlestickId::pair, toSet())));
    }

    @Override
    public Optional<ZonedDateTime> getLastCandlestickCloseTime(@NotNull String exchange, @NotNull String pair) {
        LOGGER.debug("getLastCandlestickCloseTime {} {}", exchange, pair);
        return Optional.ofNullable(lastCandlesticks.get(id(exchange, pair))).map(Candlestick::closeTime);
    }

    @Override
    public Optional<Candlestick> getLastCandlestick(@NotNull String exchange, @NotNull String pair) {
        LOGGER.debug("getLastCandlestick {} {}", exchange, pair);
        return Optional.ofNullable(lastCandlesticks.get(id(exchange, pair)));
    }

    @Override
    public void setLastCandlestick(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("setLastCandlestick {} {} {}", exchange, pair, candlestick);
        lastCandlesticks.put(id(exchange, pair), candlestick);
    }

    @Override
    public void updateLastCandlestick(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("updateLastCandlestick {} {} {}", exchange, pair, candlestick);
        requireNonNull(candlestick);
        lastCandlesticks.computeIfPresent(id(exchange, pair), (id, c) -> candlestick);
    }

    @Override
    public void lastCandlestickBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("lastCandlestickBatchDeletes");
        deleter.accept(ids -> lastCandlesticks.remove(id((String) ids.get("exchange"), (String) ids.get("pair"))));
    }
}
