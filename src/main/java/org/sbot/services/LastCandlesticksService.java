package org.sbot.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.context.TransactionalContext;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.ArgumentValidator.requirePairFormat;
import static org.sbot.utils.ArgumentValidator.requireSupportedExchange;

public final class LastCandlesticksService {

    private static final Logger LOGGER = LogManager.getLogger(LastCandlesticksService.class);

    private final TransactionalContext context;

    public LastCandlesticksService(@NotNull TransactionalContext context) {
        this.context = requireNonNull(context);
    }

    public Optional<Candlestick> getLastCandlestick(@NotNull String exchange, @NotNull String pair) {
        var lastCandlestick = context.lastCandlesticksDao().getLastCandlestick(exchange, pair);
        lastCandlestick.ifPresentOrElse(candlestick -> LOGGER.debug("Found a last price for pair [{}] : {}", pair, lastCandlestick),
                () -> LOGGER.debug("No last price found for pair [{}]", pair));
        return lastCandlestick;
    }

    public void updateLastCandlestick(@NotNull String exchange, @NotNull String pair, @Nullable Candlestick lastCandlestick, @NotNull Candlestick newCandlestick) {
        requireNonNull(exchange);
        requireNonNull(pair);
        if(null == lastCandlestick) {
            context.lastCandlesticksDao().setLastCandlestick(exchange, pair, requireNonNull(newCandlestick));
        } else if(lastCandlestick.closeTime().isBefore(newCandlestick.closeTime())) {
            context.lastCandlesticksDao().updateLastCandlestick(exchange, pair, newCandlestick);
        } else {
            LOGGER.warn("Unexpected outdated new candlestick received, pair {}, last candlestick : {}, outdated new candlestick : {}", pair, lastCandlestick, newCandlestick);
        }
    }

    @FunctionalInterface
    public interface DeleteLastCandlestickBatchEntry {
        void batch(@NotNull String exchange, @NotNull String pair);
    }

    void delete(@NotNull Consumer<DeleteLastCandlestickBatchEntry> deleter) {
        context.lastCandlesticksDao().delete(batchEntry -> deleter.accept((exchange, pair) ->
                batchEntry.batch(Map.of("exchange", requireSupportedExchange(exchange), "pair", requirePairFormat(pair)))));
    }
}
