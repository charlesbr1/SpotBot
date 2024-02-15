package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public interface LastCandlesticksDao {


    @NotNull
    Map<String, Set<String>> getPairsByExchanges();

    Optional<ZonedDateTime> getLastCandlestickCloseTime(@NotNull String exchange, @NotNull String pair);

    Optional<Candlestick> getLastCandlestick(@NotNull String exchange, @NotNull String pair);

    void setLastCandlestick(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick);

    void updateLastCandlestick(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick);

    void lastCandlestickBatchDeletes(@NotNull Consumer<BatchEntry> deleter);
}
